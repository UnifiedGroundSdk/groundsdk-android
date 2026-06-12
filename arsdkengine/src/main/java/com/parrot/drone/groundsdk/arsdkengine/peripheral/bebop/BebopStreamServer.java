/*
 *     Copyright (C) 2019 Parrot Drones SAS
 *
 *     Redistribution and use in source and binary forms, with or without
 *     modification, are permitted provided that the following conditions
 *     are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of the Parrot Company nor the names
 *       of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written
 *       permission.
 *
 *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *     "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *     LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *     FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *     PARROT COMPANY BE LIABLE FOR ANY DIRECT, INDIRECT,
 *     INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *     BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 *     OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 *     AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *     OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *     OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *     SUCH DAMAGE.
 *
 */

package com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop;

import android.util.Log;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.device.DeviceConnector;
import com.parrot.drone.groundsdk.device.RemoteControl;
import com.parrot.drone.groundsdk.internal.device.DeviceConnectorCore;
import com.parrot.drone.groundsdk.internal.device.RemoteControlCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.stream.StreamServerCore;
import com.parrot.drone.groundsdk.internal.utility.RemoteControlStore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;
import com.parrot.drone.sdkcore.stream.SdkCoreStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.parrot.drone.sdkcore.arsdk.device.ArsdkDevice.LIVE_URL;

/**
 * StreamServer peripheral controller for legacy Bebop/Disco drones.
 *
 * <p>Transport selection protocol:
 * <ul>
 *   <li>MUX transport ("mux/arstream2"): used only when the drone is connected through a
 *       SkyController 2 or SkyController 2+ over USB. The SC2 family tunnels arstream2 over
 *       the MUX protocol on the USB link.</li>
 *   <li>NET transport ("net/arstream2"): used for all direct WiFi connections, including
 *       connections through a SkyController 1 over WiFi. SC1 is a pure WiFi relay — it does
 *       not provide a MUX link, so the drone is always reachable over UDP.</li>
 * </ul>
 * The transport is selected from {@code useMux}, which is derived from the actual remote
 * control model at connection time. The {@code skyController} flag is retained only to
 * trigger the legacy streaming enable command and to configure the local RTSP bridge with
 * the correct drone IP address.
 *
 * <p>Media replay note: Bebop arstream2 is a unidirectional live UDP video push; it has no
 * seek or file-playback capability. A non-live URL passed to {@link #openStream} (e.g. an
 * FTP path from BebopMediaStore) cannot be honoured and is explicitly refused — see the
 * comment in the backend implementation below.
 */
public class BebopStreamServer extends DronePeripheralController {
    private static final String CLASS_NAME = BebopStreamServer.class.getSimpleName();

    /** StreamServer peripheral for which this object is the backend. */
    @NonNull
    private final StreamServerCore mStreamController;
    private final DroneController mDroneController;

    /**
     * True when the active connection goes through any SkyController model (SC1, SC2, SC2+).
     * Used only to:
     * <ul>
     *   <li>send the legacy video-enable command on connect (required by SC1), and</li>
     *   <li>supply the correct drone IP (192.168.43.1) to {@link BebopLocalRtspServer} for
     *       the SDP Content-Base and connection-address fields.</li>
     * </ul>
     * NOT used to choose the transport — see {@code useMux}.
     */
    private boolean skyController = false;

    /**
     * True only when the drone is connected through a SkyController 2 or SkyController 2+.
     * Those controllers tunnel arstream2 over the MUX protocol on the USB link.
     * SC1 and all direct WiFi connections use plain UDP (net transport).
     *
     * <p>Protocol assumption: SC2/SC2P always present on USB with MUX available; SC1 and
     * direct WiFi connections are always plain UDP regardless of RC presence.
     */
    private boolean useMux = false;

    private BebopLocalRtspServer bebopLocalRtspServer = null;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public BebopStreamServer(@NonNull DroneController droneController) {
        super(droneController);

        final StreamServerCore.Backend backend = new StreamServerCore.Backend() {
            @Nullable
            @Override
            public SdkCoreStream openStream(@NonNull String url, @Nullable String track,
                    @NonNull SdkCoreStream.Client client) {
                // Protocol contract: arstream2 is a live unidirectional UDP video push.
                // It carries no seek/file-playback mechanism; the firmware does not accept
                // a media URL over this path.
                //
                // Only LIVE_URL ("live") is supported here.  Any other url value comes from
                // a MediaReplay source (an FTP file path such as
                // "/internal_000/Bebop_Drone/media/DVD_0001.MP4") and cannot be served
                // through the arstream2 bridge.  Return null so the StreamCore layer
                // transitions the stream to FAILED rather than silently opening the live
                // feed under a replay handle.
                if (!LIVE_URL.equals(url)) {
                    Log.e(CLASS_NAME, "openStream: media replay is not supported on Bebop "
                            + "(arstream2 live-only); url=" + url + " refused");
                    return null;
                }

                // Transport selection is keyed off useMux, not skyController.
                // useMux is true only for SC2/SC2P (USB+MUX link).
                // SC1 over WiFi: skyController=true, useMux=false → net transport.
                return mDeviceController.openVideoStream(
                        useMux ? "mux/arstream2" : "net/arstream2", track, client);
            }

            @Override
            public void enableLegacyVideoStreaming(final boolean enable) {
                sendCommand(ArsdkFeatureArdrone3.MediaStreaming.encodeVideoEnable(enable ? 1 : 0));
            }

            @Override
            public void setLegacyVideoStreamingMode(ArsdkFeatureArdrone3.MediastreamingVideostreammodeMode mode) {
                sendCommand(ArsdkFeatureArdrone3.MediaStreaming.encodeVideoStreamMode(mode));
            }
        };

        mStreamController = new StreamServerCore(mComponentStore, backend);
        mDroneController = droneController;
    }

    @Override
    protected void onConnected() {
        if (mDroneController.getActiveProvider() != null) {

            final DeviceConnectorCore connector = mDroneController.getActiveProvider().getConnector();
            skyController = connector.getType() == DeviceConnector.Type.REMOTE_CONTROL;

            if (skyController && connector.getUid() != null) {
                final RemoteControlCore rcc = mDroneController.getEngine()
                        .getUtilityOrThrow(RemoteControlStore.class).get(connector.getUid());
                if (rcc != null) {
                    // MUX is available only on SC2 and SC2+ (USB tunnelled link).
                    // SC1 connects over plain WiFi; useMux stays false.
                    useMux = RemoteControl.Model.SKY_CONTROLLER_2.equals(rcc.getModel())
                            || RemoteControl.Model.SKY_CONTROLLER_2P.equals(rcc.getModel());
                }
            }
        }

        if (skyController) {
            // SC1 requires an explicit video-enable command; SC2/SC2P enable it automatically
            // but sending it is harmless.
            sendCommand(ArsdkFeatureArdrone3.MediaStreaming.encodeVideoEnable(1));
        }

        if (!useMux) {
            // The local RTSP bridge is only needed for net (UDP) paths.
            // Pass skyController so the bridge emits the correct drone IP in the SDP:
            //   SC path  → 192.168.43.1  (Bebop behind SC1 on 192.168.43.x subnet)
            //   WiFi path → 192.168.42.1  (Bebop direct on 192.168.42.x subnet)
            // The bridge always responds with NET transport (RTSP_TRANSPORT_NET) because
            // it is never instantiated for MUX paths — see BebopLocalRtspServer.processSetup.
            bebopLocalRtspServer = new BebopLocalRtspServer(skyController);
            bebopLocalRtspServer.startServer();
        }

        mStreamController.publish();
    }

    @Override
    protected void onDisconnected() {
        mStreamController.unpublish();
        if (bebopLocalRtspServer != null) bebopLocalRtspServer.stopServer();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        super.onCommandReceived(command);
        if (command.getFeatureId() == ArsdkFeatureArdrone3.MediaStreamingState.UID) {
            ArsdkFeatureArdrone3.MediaStreamingState.decode(command, mMediaStreamingStateCallback);
        }
    }

    private final ArsdkFeatureArdrone3.MediaStreamingState.Callback mMediaStreamingStateCallback =
            new ArsdkFeatureArdrone3.MediaStreamingState.Callback() {
                @Override
                public void onVideoEnableChanged(
                        @Nullable ArsdkFeatureArdrone3.MediastreamingstateVideoenablechangedEnabled enabled) {
                    if (enabled != null) {
                        Log.i(CLASS_NAME, "MediastreamingstateVideoenablechangedEnabled=" + enabled.name());
                    }
                }

                @Override
                public void onVideoStreamModeChanged(
                        @Nullable ArsdkFeatureArdrone3.MediastreamingstateVideostreammodechangedMode mode) {
                    if (mode != null) {
                        mStreamController.updateLegacyStreamingMode(
                                ArsdkFeatureArdrone3.MediastreamingVideostreammodeMode.fromValue(mode.value));
                        mStreamController.notifyUpdated();
                    }
                }
            };
}


