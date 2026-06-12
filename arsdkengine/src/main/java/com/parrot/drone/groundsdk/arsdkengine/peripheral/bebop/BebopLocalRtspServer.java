package com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop;

import android.util.Log;

import com.parrot.drone.groundsdk.arsdkengine.BuildConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

/**
 * Localhost fake RTSP server bridging legacy Bebop/Mambo arstream2 video into pdraw.
 *
 * <p>pdraw's RTSP client (librtsp/rtsp_client.c) connects to this server on 127.0.0.1:5554 to
 * perform the OPTIONS/DESCRIBE/SETUP/PLAY exchange.  The native arsdk-ng layer injects the real
 * arstream2 RTP ports during connection; pdraw never contacts the drone's RTSP service directly.
 *
 * <h3>Protocol assumptions (verified against librtsp source)</h3>
 * <ul>
 *   <li><b>CSeq echo</b>: rtsp_client.c ~line 842 strictly checks
 *       {@code resp.cseq == req.cseq} and returns {@code -EPROTO} on mismatch, causing the
 *       entire request to be dropped.  The server therefore must echo the CSeq value from each
 *       incoming request header rather than maintain its own counter.  The old counter-based
 *       approach desynced permanently after any TEARDOWN/reconnect because the client resets
 *       its counter to 1 while the server counter carried forward.</li>
 *   <li><b>Content-Length</b>: computed as the exact UTF-8 byte length of the SDP body string
 *       using LF line endings (what {@link PrintWriter#println()} emits on Android).
 *       librtsp's {@code find_double_newline()} accepts both {@code \r\n\r\n} and {@code \n\n},
 *       so LF-only endings are tolerated.  The old hardcoded literals 289/290 were correct only
 *       by coincidence for the specific SDP text at the time they were written.</li>
 *   <li><b>SDP sprop-parameter-sets</b>: pdraw's StreamDemuxer uses SDP-provided SPS/PPS for
 *       early out-of-band pre-configuration of the H.264 codec info
 *       ({@code pdraw_demuxer_stream.cpp setupInputMedia}).  Arstream2 always carries SPS/PPS
 *       in-band in the RTP stream; when the first IDR arrives, {@code codecInfoChangedCb} fires
 *       and calls {@code setupInputMedia} again with the in-band values.  Providing SPS/PPS in
 *       the SDP reduces latency to first decoded frame.
 *       The placeholder values below are Bebop 2 H.264 baseline Level 4.0 defaults.
 *       TODO(bench): capture actual SPS/PPS bytes from a live Bebop 2 / Mambo RTP stream and
 *       verify against these base64 values; update if they differ.</li>
 *   <li><b>Device IPs in SDP</b>: 192.168.42.1 (direct Wi-Fi) and 192.168.43.1 (SkyController
 *       bridge) are fixed by Parrot drone firmware — they are protocol constants, not
 *       runtime-discovered values.  The {@code skyController} boolean is the correct
 *       discriminator.</li>
 *   <li><b>Ports in SDP</b>: 55004 (direct) / 5004 (SkyController) are the fixed arstream2 RTP
 *       data ports that arsdk-ng configures on the device side.</li>
 *   <li><b>Transport header on SETUP</b>: this server is only instantiated for non-MUX (UDP)
 *       paths — see {@link BebopStreamServer#onConnected()}.  For SkyController 2/2+ (MUX)
 *       the bridge is skipped entirely, so RTSP_TRANSPORT_NET is always correct here even when
 *       {@code skyController} is true (SC1 over Wi-Fi reaches this path).</li>
 * </ul>
 */
public class BebopLocalRtspServer {
    private final RtspListenerThread listenerThread = new RtspListenerThread();
    private Collection<ClientThread> clients = new ArrayList<>();

    private static final String TAG = BebopLocalRtspServer.class.getSimpleName();

    /* Fixed RTSP response status lines */
    private static final String RTSP_OK = "RTSP/1.0 200 OK";
    private static final String RTSP_NOT_IMPLEMENTED = "RTSP/1.0 501 Not Implemented";

    /**
     * CSeq response format.  Value must echo the CSeq from the incoming request.
     * See class-level javadoc and rtsp_client.c for the mismatch-drop contract.
     */
    private static final String RTSP_CSEQ = "CSeq: %d";

    private static final String RTSP_PUBLIC =
            "Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, SET_PARAMETER";
    private static final String RTSP_TIMESTAMP = "Date:  18 9 2018 17:22:2 GMT";
    private static final String RTSP_SESSION = "Session: oxlmg4ay61xdbywm7p6ws1ko";

    /**
     * SETUP transport for the direct UDP path (non-MUX).
     * client_port=55004-55005 matches the arstream2 RTP/RTCP ports arsdk-ng opens.
     * server_port=5004-5005 are the Bebop's fixed RTP/RTCP listening ports.
     */
    private static final String RTSP_TRANSPORT_NET =
            "Transport: RTP/AVP/UDP;unicast;client_port=55004-55005;server_port=5004-5005";

    /**
     * {@code skyController} selects the correct device IP for the SDP body.
     * It does NOT affect transport selection — this server is only created for non-MUX paths.
     */
    private final boolean skyController;
    private final Object lock = new Object();

    BebopLocalRtspServer(final boolean skyController) {
        this.skyController = skyController;
    }

    void startServer() {
        listenerThread.start();
    }

    void stopServer() {
        if (listenerThread.isAlive()) {
            listenerThread.interrupt();

            // Close client sockets before joining so readLine() unblocks in each ClientThread.
            synchronized (lock) {
                for (final ClientThread client : clients) {
                    try {
                        client.socket.close();
                    } catch (Exception e) {
                        logEvent(Log.WARN,
                                "unable to close client socket on stop: " + e.getMessage(), e);
                    }
                }
            }

            try {
                listenerThread.server.close();
            } catch (Exception e) {
                logEvent(Log.WARN, "unable to close server socket: " + e.getMessage(), e);
            }

            try {
                listenerThread.join(5000);
            } catch (Exception e) {
                logEvent(Log.WARN, "unable to clean up listener: " + e.getMessage(), e);
            }
        }
    }

    private class RtspListenerThread extends Thread {
        private final CleanupThread cleanupThread = new CleanupThread();
        ServerSocket server;

        private RtspListenerThread() {
            this(5554);
        }

        private RtspListenerThread(final int port) {
            try {
                /*
                 * Bind to loopback only — this fake server must not be reachable from the
                 * network.  InetAddress.getLoopbackAddress() returns 127.0.0.1 (available since
                 * Java 7 / Android API 1 via InetAddress.getByName("localhost")).
                 * The three-argument ServerSocket constructor also sets SO_REUSEADDR implicitly.
                 * Backlog of 5 is sufficient for the single pdraw client that uses this server.
                 */
                server = new ServerSocket(port, 5, InetAddress.getLoopbackAddress());
            } catch (IOException e) {
                server = null;
                /* ERROR is always emitted (not debug-gated): a bind failure means no video. */
                logEvent(Log.ERROR,
                        "unable to open listener on port " + port + ": " + e.getMessage(), e);
            }
        }

        @Override
        public void run() {
            if (server == null) {
                logEvent(Log.ERROR, "unable to start socket server: no ServerSocket");
                return;
            }

            cleanupThread.start();

            boolean running = true;

            while (running) {
                try {
                    final Socket socket = server.accept();
                    final ClientThread client = new ClientThread(socket);

                    synchronized (lock) {
                        clients.add(client);
                    }

                    logEvent("client added");

                    client.start();
                } catch (Exception ex) {
                    running = false;
                }
            }

            cleanupThread.interrupt();
            try {
                cleanupThread.join();
            } catch (InterruptedException e) {
                logEvent(Log.WARN, "unable to join cleanup thread: " + e.getMessage(), e);
            }
        }
    }

    private class ClientThread extends Thread {
        private final Socket socket;

        private BufferedReader is = null;
        private PrintWriter os = null;

        private ClientThread(final Socket socket) {
            this.socket = socket;

            try {
                is = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                os = new PrintWriter(socket.getOutputStream(), true);
            } catch (Exception e) {
                logEvent(Log.WARN,
                        "unable to open input/output streams: " + e.getMessage(), e);
            }
        }

        /**
         * Drain RTSP request headers until the blank line that terminates them, logging each
         * header line and extracting the CSeq value.
         *
         * <p>RFC 2326 §6: the request-line has already been consumed by the caller; this method
         * starts from the first header line.  librtsp sends {@code CSeq: <n>} as the second
         * header on every method; parsing it here ensures the response echoes the request's
         * sequence number regardless of reconnects or TEARDOWN resets.
         *
         * <p>Case-insensitive match per RFC 2326 §4.2 (header field names are case-insensitive).
         *
         * @param firstHeaderLine the line immediately after the request-line (may be null if
         *                        the connection was closed)
         * @return the CSeq integer from the request headers, or {@code -1} if not found or
         *         malformed
         */
        private int drainHeaders(final String firstHeaderLine) throws IOException {
            int cseq = -1;
            String line = firstHeaderLine;
            while (line != null && line.length() > 0) {
                logEvent(line);
                if (line.regionMatches(true, 0, "CSeq:", 0, 5)) {
                    try {
                        cseq = Integer.parseInt(line.substring(5).trim());
                    } catch (NumberFormatException ignored) {
                        logEvent(Log.WARN, "malformed CSeq header: " + line);
                    }
                }
                line = is.readLine();
            }
            return cseq;
        }

        @Override
        public void run() {
            if (is == null || os == null) {
                logEvent(Log.ERROR, "unable to start socket client: streams not initialized");
                return;
            }

            boolean running = true;

            while (running) {
                try {
                    final String inputLine = is.readLine();

                    if (inputLine == null) {
                        // Connection closed by peer.
                        running = false;
                        continue;
                    }

                    if (inputLine.startsWith("OPTIONS")) {
                        final int cseq = drainHeaders(is.readLine());
                        processOptions(cseq);
                        continue;
                    }

                    if (inputLine.startsWith("DESCRIBE")) {
                        final int cseq = drainHeaders(is.readLine());
                        processDescribe(cseq);
                        continue;
                    }

                    if (inputLine.startsWith("SETUP")) {
                        final int cseq = drainHeaders(is.readLine());
                        processSetup(cseq);
                        continue;
                    }

                    if (inputLine.startsWith("PLAY")) {
                        final int cseq = drainHeaders(is.readLine());
                        processPlay(cseq);
                        continue;
                    }

                    if (inputLine.startsWith("PAUSE")) {
                        final int cseq = drainHeaders(is.readLine());
                        processPause(cseq);
                        continue;
                    }

                    if (inputLine.startsWith("TEARDOWN")) {
                        final int cseq = drainHeaders(is.readLine());
                        processTeardown(cseq);
                        continue;
                    }

                    /*
                     * Unknown method: drain remaining headers and reply 501 Not Implemented
                     * (RFC 2326 §7.1).  Silence would leave the client waiting indefinitely.
                     */
                    logEvent(Log.WARN, "unknown RTSP method: " + inputLine);
                    final int cseq = drainHeaders(is.readLine());
                    processNotImplemented(cseq);

                } catch (Exception e) {
                    running = false;
                }
            }

            try {
                is.close();
            } catch (Exception e) {
                logEvent(Log.WARN, "unable to close input stream: " + e.getMessage(), e);
            }
            try {
                os.close();
            } catch (Exception e) {
                logEvent(Log.WARN, "unable to close output stream: " + e.getMessage(), e);
            }
            try {
                socket.close();
            } catch (Exception e) {
                logEvent(Log.WARN, "unable to close client socket: " + e.getMessage(), e);
            }
        }

        private void processOptions(final int cseq) {
            try {
                logEvent("processOptions");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, cseq));
                os.println(RTSP_PUBLIC);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process OPTIONS: " + e.getMessage(), e);
            }
        }

        /**
         * Build and send the DESCRIBE response with an SDP session description.
         *
         * <p><b>Content-Length</b>: measured as the exact UTF-8 byte length of the SDP body
         * string (LF line endings as produced by {@link PrintWriter#println()}) rather than the
         * old hardcoded literals 289/290, which were fragile and only happened to be correct for
         * a specific version of the SDP text.
         *
         * <p>See class-level javadoc for the protocol assumptions behind device IP selection,
         * port values, and sprop-parameter-sets.
         */
        private void processDescribe(final int cseq) {
            try {
                logEvent("processDescribe");

                /*
                 * Device IP — fixed by Parrot firmware network configuration:
                 *   192.168.42.1  Bebop/Mambo direct Wi-Fi (access-point mode)
                 *   192.168.43.1  Bebop behind SkyController bridge subnet
                 */
                final String deviceIp = skyController ? "192.168.43.1" : "192.168.42.1";

                /*
                 * arstream2 RTP data port on the device side, injected by arsdk-ng:
                 *   5004   SkyController path
                 *   55004  direct Wi-Fi path
                 */
                final int videoPort = skyController ? 5004 : 55004;

                /*
                 * Build the SDP body as a single string.  Each println() appends '\n' on
                 * Android, so Content-Length is the UTF-8 byte count of this string.
                 * librtsp's find_double_newline() accepts \n\n as the header terminator.
                 */
                final String sdpBody =
                        "v=0\n"
                        + "o=- 0 0 IN IP4 127.0.0.1\n"
                        + "s=media/stream2\n"
                        + "i=A Seminar on the session description protocol\n"
                        + "c=IN IP4 " + deviceIp + "\n"
                        + "t=0 0\n"
                        + "m=video " + videoPort + " RTP/AVP 96\n"
                        + "a=rtpmap:96 H264/90000\n"
                        /*
                         * sprop-parameter-sets: Bebop 2 H.264 baseline Level 4.0 defaults.
                         * pdraw uses these for early out-of-band codec pre-configuration;
                         * arstream2 always also carries SPS/PPS in-band so these are not
                         * strictly required, but providing them reduces first-frame latency.
                         * TODO(bench): verify these match actual firmware SPS/PPS — see class
                         * javadoc.
                         */
                        + "a=fmtp:96 packetization-mode=1;profile-level-id=000042;"
                        + "sprop-parameter-sets=Z01AKZZUAoAtyA==,aO44gA==\n"
                        + "a=control:stream=0\n";

                final int contentLength =
                        sdpBody.getBytes(StandardCharsets.UTF_8).length;

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, cseq));
                os.println(RTSP_TIMESTAMP);
                os.println("Content-Base: rtsp://" + deviceIp + "/media/stream2/");
                os.println("Content-Type: application/sdp");
                os.println("Content-Length: " + contentLength);
                os.println();
                os.print(sdpBody);
                os.flush();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process DESCRIBE: " + e.getMessage(), e);
            }
        }

        private void processSetup(final int cseq) {
            try {
                logEvent("processSetup");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, cseq));
                os.println(RTSP_TIMESTAMP);
                os.println(RTSP_SESSION);
                /*
                 * Always advertise UDP/NET transport.  This server is only instantiated for
                 * non-MUX paths — BebopStreamServer.onConnected() skips bridge creation when
                 * useMux=true (SC2/SC2+).  A Bebop behind SC1 has skyController=true but
                 * useMux=false, so it reaches this code and still requires NET transport.
                 */
                os.println(RTSP_TRANSPORT_NET);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process SETUP: " + e.getMessage(), e);
            }
        }

        private void processPlay(final int cseq) {
            try {
                logEvent("processPlay");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, cseq));
                os.println(RTSP_TIMESTAMP);
                os.println(RTSP_SESSION);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process PLAY: " + e.getMessage(), e);
            }
        }

        private void processPause(final int cseq) {
            try {
                logEvent("processPause");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, cseq));
                os.println(RTSP_TIMESTAMP);
                os.println(RTSP_SESSION);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process PAUSE: " + e.getMessage(), e);
            }
        }

        private void processTeardown(final int cseq) {
            try {
                logEvent("processTeardown");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, cseq));
                os.println(RTSP_TIMESTAMP);
                os.println(RTSP_SESSION);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process TEARDOWN: " + e.getMessage(), e);
            }
        }

        /**
         * Reply 501 Not Implemented for methods this server does not support (RFC 2326 §7.1).
         * Silence would leave librtsp's client loop waiting indefinitely for a response.
         */
        private void processNotImplemented(final int cseq) {
            try {
                os.println(RTSP_NOT_IMPLEMENTED);
                os.println(String.format(Locale.US, RTSP_CSEQ, cseq));
                os.println();
            } catch (Exception e) {
                logEvent(Log.WARN, "unable to send 501 response: " + e.getMessage(), e);
            }
        }
    }

    private class CleanupThread extends Thread {

        @Override
        public void run() {

            boolean running = true;

            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    running = false;
                    continue;
                }

                synchronized (lock) {
                    for (final Iterator<ClientThread> iterator = clients.iterator();
                            iterator.hasNext(); ) {
                        final ClientThread client = iterator.next();

                        if (!client.isAlive()) {
                            iterator.remove();
                            logEvent("client removed");
                        }
                    }
                }
            }

            synchronized (lock) {
                for (final Iterator<ClientThread> iterator = clients.iterator();
                        iterator.hasNext(); ) {
                    final ClientThread client = iterator.next();

                    if (client.isAlive()) {
                        client.interrupt();

                        // Close the socket before joining so readLine() unblocks immediately.
                        try {
                            client.socket.close();
                        } catch (Exception e) {
                            logEvent(Log.WARN,
                                    "unable to close client socket on cleanup: "
                                            + e.getMessage(), e);
                        }

                        try {
                            client.join(5000);
                        } catch (InterruptedException e) {
                            logEvent(Log.WARN,
                                    "unable to join client thread: " + e.getMessage(), e);
                        } finally {
                            iterator.remove();
                        }
                    } else {
                        iterator.remove();
                    }
                }
            }
        }
    }

    private static void logEvent(final String message) {
        logEvent(Log.VERBOSE, message);
    }

    private static void logEvent(final int severity, final String message) {
        logEvent(severity, message, null);
    }

    private static void logEvent(final int severity, final String message,
            final Throwable exception) {
        /*
         * ERROR messages are always emitted regardless of build variant: a server-bind failure
         * means no legacy video stream, which is a visible operational failure that must appear
         * in production logs.  All other severities are debug-only.
         */
        if (severity == Log.ERROR || BuildConfig.DEBUG) {
            switch (severity) {
                case Log.VERBOSE:
                    Log.v(TAG, message, exception);
                    break;
                case Log.DEBUG:
                    Log.d(TAG, message, exception);
                    break;
                case Log.INFO:
                    Log.i(TAG, message, exception);
                    break;
                case Log.WARN:
                    Log.w(TAG, message, exception);
                    break;
                case Log.ERROR:
                    Log.e(TAG, message, exception);
                    break;
                default:
                    throw new RuntimeException("Invalid log level specified");
            }
        }
    }
}
