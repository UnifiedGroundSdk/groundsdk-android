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

/** StreamServer peripheral controller for Anafi family drones. */
public class BebopStreamServer extends DronePeripheralController {
    private static final String CLASS_NAME = BebopStreamServer.class.getSimpleName();

    /** StreamServer peripheral for which this object is the backend. */
    @NonNull
    private final StreamServerCore mStreamController;
    private final DroneController mDroneController;

    private boolean skyController = false;
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
            public SdkCoreStream openStream(@NonNull String url, @Nullable String track, @NonNull SdkCoreStream.Client client) {
                return mDeviceController.openVideoStream(useMux  ? "mux/arstream2" : "net/arstream2", track, client);
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
                final RemoteControlCore rcc = mDroneController.getEngine().getUtilityOrThrow(RemoteControlStore.class).get(connector.getUid());
                if (rcc != null) {
                    useMux = RemoteControl.Model.SKY_CONTROLLER_2.equals(rcc.getModel()) || RemoteControl.Model.SKY_CONTROLLER_2P.equals(rcc.getModel());
                }
            }
        }

        if (skyController) {
            sendCommand(ArsdkFeatureArdrone3.MediaStreaming.encodeVideoEnable(1));
        }

        if (!useMux) {
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
                public void onVideoEnableChanged(@Nullable ArsdkFeatureArdrone3.MediastreamingstateVideoenablechangedEnabled enabled) {
                    if (enabled != null) {
                        Log.i(CLASS_NAME, "MediastreamingstateVideoenablechangedEnabled=" + enabled.name());
                    }
                }

                @Override
                public void onVideoStreamModeChanged(@Nullable ArsdkFeatureArdrone3.MediastreamingstateVideostreammodechangedMode mode) {
                    if (mode != null) {
                        mStreamController.legacyStreamingMode = ArsdkFeatureArdrone3.MediastreamingVideostreammodeMode.fromValue(mode.value);
                        mStreamController.notifyUpdated();
                    }
                }
    };
}


