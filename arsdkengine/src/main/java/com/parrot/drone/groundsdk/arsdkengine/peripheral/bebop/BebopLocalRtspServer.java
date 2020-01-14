package com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop;

import android.util.Log;

import com.parrot.drone.groundsdk.arsdkengine.BuildConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

public class BebopLocalRtspServer {
    private final RtspListenerThread listenerThread = new RtspListenerThread();
    private Collection<ClientThread> clients = new ArrayList<>();

    private static final String RTSP_OK = "RTSP/1.0 200 OK";
    private static final String RTSP_CSEQ = "CSeq: %d";
    private static final String RTSP_PUBLIC = "Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, SET_PARAMETER";
    private static final String RTSP_TIMESTAMP = "Date:  18 9 2018 17:22:2 GMT";
    private static final String RTSP_SESSION = "Session: oxlmg4ay61xdbywm7p6ws1ko";

    private static final String RTSP_TRANSPORT_MUX = "Transport: RTP/AVP/MUX;unicast;client_port=1-1;server_port=5004-5005";
    private static final String RTSP_TRANSPORT_NET = "Transport: RTP/AVP/UDP;unicast;client_port=55004-55005;server_port=5004-5005";

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

            try {
                listenerThread.server.close();
                listenerThread.join();
            } catch (Exception e) {
                logEvent(Log.WARN, "unable to clean up listener: " + e.getMessage(), e);
            }
        }
    }

    private class RtspListenerThread extends Thread {
        private CleanupThread cleanupThread = new CleanupThread();
        ServerSocket server;

        private RtspListenerThread() { this(5554); }
        private RtspListenerThread(final int port) {
            try {
                server = new ServerSocket(port);
            } catch (IOException e) {
                server = null;
                logEvent(Log.WARN, "unable to open listener: " + e.getMessage(), e);
            }
        }

        @Override
        public void run() {
            if (server == null) {
                logEvent(Log.ERROR, "unable to start socket server");
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
                logEvent(Log.WARN, "unable to open input/output streams: " + e.getMessage(), e);
            }
        }

        @Override
        public void run() {
            if (is == null || os == null) {
                logEvent(Log.ERROR, "unable to start socket client");
                return;
            }

            int seq = 0;

            boolean running = true;

            while (running) {
                try {
                    String inputLine = is.readLine();
                    seq++;

                    if (inputLine.startsWith("OPTIONS")) {
                        while (inputLine.length() > 0) {
                            logEvent(inputLine);
                            inputLine = is.readLine();
                        }
                        processOptions(seq);

                        continue;
                    }

                    if (inputLine.startsWith("DESCRIBE")) {
                        while (inputLine.length() > 0) {
                            logEvent(inputLine);
                            inputLine = is.readLine();
                        }
                        processDescribe(seq);

                        continue;
                    }

                    if (inputLine.startsWith("SETUP")) {
                        while (inputLine.length() > 0) {
                            logEvent(inputLine);
                            inputLine = is.readLine();
                        }
                        processSetup(seq);

                        continue;
                    }

                    if (inputLine.startsWith("PLAY")) {
                        while (inputLine.length() > 0) {
                            logEvent(inputLine);
                            inputLine = is.readLine();
                        }
                        processPlay(seq);

                        continue;
                    }

                    if (inputLine.startsWith("PAUSE")) {
                        while (!inputLine.equals("\r\n")) {
                            logEvent(inputLine);
                            inputLine = is.readLine();
                        }
                        processPause(seq);

                        continue;
                    }

                    if (inputLine.startsWith("TEARDOWN")) {
                        while (inputLine.length() > 0) {
                            logEvent(inputLine);
                            inputLine = is.readLine();
                        }
                        processTeardown(seq);
                        seq = 0;

                        continue;
                    }

                    // should never be here
                    logEvent(Log.WARN,"invalid data received: " + inputLine);

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

        private void processOptions(final int seq) {
            try {
                logEvent("processOptions");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, seq));
                os.println(RTSP_PUBLIC);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process OPTIONS: " + e.getMessage(), e);
            }
        }

        private void processDescribe(final int seq) {
            try {
                logEvent("processDescribe");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, seq));
                os.println(RTSP_TIMESTAMP);
                os.println("Content-Base: rtsp://" + (skyController ? "192.168.43.1" : "192.168.42.1") + "/media/stream2/");
                os.println("Content-Type: application/sdp");
                os.println("Content-Length: " + (skyController ? "289" : "290"));
                os.println();
                os.println("v=0");
                os.println("o=- 0 0 IN IP4 127.0.0.1");
                os.println("s=media/stream2");
                os.println("i=A Seminar on the session description protocol");
                os.println("c=IN IP4 " + (skyController ? "192.168.43.1" : "192.168.42.1"));
                os.println("t=0 0");
                os.println("m=video " + (skyController ? "5004" : "55004") + " RTP/AVP 96");
                os.println("a=rtpmap:96 H264/90000");
                os.println("a=fmtp:96 packetization-mode=1;profile-level-id=000042;sprop-parameter-sets=Z01AKZZUAoAtyA==,aO44gA==");
                os.println("a=control:stream=0");

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process DESCRIBE: " + e.getMessage(), e);
            }
        }

        private void processSetup(final int seq) {
            try {
                logEvent("processSetup");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, seq));
                os.println(RTSP_TIMESTAMP);
                os.println(RTSP_SESSION);

                if (skyController) {
                    os.println(RTSP_TRANSPORT_MUX);
                } else {
                    os.println(RTSP_TRANSPORT_NET);
                }

                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process SETUP: " + e.getMessage(), e);
            }
        }

        private void processPlay(final int seq) {
            try {
                logEvent("processPlay");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, seq));
                os.println(RTSP_TIMESTAMP);
                os.println(RTSP_SESSION);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process PLAY: " + e.getMessage(), e);
            }
        }

        private void processPause(final int seq) {
            try {
                logEvent("processPause");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, seq));
                os.println(RTSP_TIMESTAMP);
                os.println(RTSP_SESSION);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process PAUSE: " + e.getMessage(), e);
            }
        }

        private void processTeardown(final int seq) {
            try {
                logEvent("processTeardown");

                os.println(RTSP_OK);
                os.println(String.format(Locale.US, RTSP_CSEQ, seq));
                os.println(RTSP_TIMESTAMP);
                os.println(RTSP_SESSION);
                os.println();

            } catch (Exception e) {
                logEvent(Log.WARN, "unable to process TEARDOWN: " + e.getMessage(), e);
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

                synchronized(lock) {
                    for (final Iterator<ClientThread> iterator = clients.iterator() ; iterator.hasNext() ;) {
                        final ClientThread client = iterator.next();

                        if (!client.isAlive()) {
                            iterator.remove();
                            logEvent("client removed");
                        }
                    }
                }
            }

            synchronized (lock) {
                for (final Iterator<ClientThread> iterator = clients.iterator() ; iterator.hasNext() ;) {
                    final ClientThread client = iterator.next();

                    if (client.isAlive()) {
                        client.interrupt();

                        try {
                            client.join();
                        } catch (InterruptedException e) {
                            logEvent(Log.WARN, "unable to join client thread: " + e.getMessage(), e);
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
    private static void logEvent(final int severity, final String message, final Throwable exception) {
        if (BuildConfig.DEBUG) {
            final String className = BebopLocalRtspServer.class.getSimpleName();
            
            switch (severity) {
                case Log.VERBOSE:
                    Log.v(className, message, exception);
                    break;
                case Log.DEBUG:
                    Log.d(className, message, exception);
                    break;
                case Log.INFO:
                    Log.i(className, message, exception);
                    break;
                case Log.WARN:
                    Log.w(className, message, exception);
                    break;
                case Log.ERROR:
                    Log.e(className, message, exception);
                    break;
                default:
                    throw new RuntimeException("Invalid log level specified");
            }
        }
    }
}
