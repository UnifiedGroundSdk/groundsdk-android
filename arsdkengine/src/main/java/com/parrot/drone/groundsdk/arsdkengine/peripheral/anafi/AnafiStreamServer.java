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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.device.DeviceConnector;
import com.parrot.drone.groundsdk.device.RemoteControl;
import com.parrot.drone.groundsdk.internal.device.DeviceConnectorCore;
import com.parrot.drone.groundsdk.internal.device.RemoteControlCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.stream.StreamServerCore;
import com.parrot.drone.groundsdk.internal.utility.RemoteControlStore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.stream.SdkCoreStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.parrot.drone.sdkcore.arsdk.device.ArsdkDevice.LIVE_URL;

/** StreamServer peripheral controller for Anafi family drones. */
public class AnafiStreamServer extends DronePeripheralController {

    /** StreamServer peripheral for which this object is the backend. */
    @NonNull
    private final StreamServerCore mStreamController;
    private final DroneController mDroneController;

    private boolean useMux = false;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public AnafiStreamServer(@NonNull final DroneController droneController) {
        super(droneController);

        StreamServerCore.Backend backend = new StreamServerCore.Backend() {
            @Nullable
            @Override
            public SdkCoreStream openStream(@NonNull String url, @Nullable String track, @NonNull SdkCoreStream.Client client) {
                if (!url.equals(LIVE_URL)) {
                    return mDeviceController.openVideoStream(url, track, client);
                } else {
                    return mDeviceController.openVideoStream(useMux ? "mux/rtsp/anafi" : "net/rtsp/anafi", track, client);
                }
            }

            @Override
            public void enableLegacyVideoStreaming(final boolean enable) {
            }

            @Override
            public void setLegacyVideoStreamingMode(ArsdkFeatureArdrone3.MediastreamingVideostreammodeMode mode) {
            }
        };

        mStreamController = new StreamServerCore(mComponentStore, backend);
        mDroneController = droneController;
    }

    @Override
    protected void onConnected() {
        if (mDroneController.getActiveProvider() != null) {

            final DeviceConnectorCore connector = mDroneController.getActiveProvider().getConnector();
            final boolean skyController = connector.getType() == DeviceConnector.Type.REMOTE_CONTROL;

            if (skyController && connector.getUid() != null) {
                final RemoteControlCore rcc = mDroneController.getEngine().getUtilityOrThrow(RemoteControlStore.class).get(connector.getUid());
                if (rcc != null) {
                    useMux = RemoteControl.Model.SKY_CONTROLLER_3.equals(rcc.getModel());
                }
            }
        }

        mStreamController.publish();
    }

    @Override
    protected void onDisconnected() {
        mStreamController.unpublish();
    }
}