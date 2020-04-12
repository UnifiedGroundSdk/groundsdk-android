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

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.internal.device.peripheral.PitotCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pitot peripheral controller for Bebop (Disco) family drones. */
public final class DiscoPitot extends DronePeripheralController {

    /** The pitot peripheral from which this object is the backend. */
    @NonNull
    private final PitotCore mPitot;

    /**
     * Constructor.
     *
     * @param droneController The drone controller that owns this component controller.
     */
    public DiscoPitot(@NonNull DroneController droneController) {
        super(droneController);
        mPitot = new PitotCore(mComponentStore, mBackend);
    }

    @Override
    public void onConnected() {
        mPitot.publish();
    }

    @Override
    public void onDisconnected() {
        mPitot.unpublish();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureCommon.CalibrationState.UID) {
            ArsdkFeatureCommon.CalibrationState.decode(command, mCalibrationStateCallback);
        }
    }

    /** Callbacks called when a command of the feature ArsdkFeatureCommon.CalibrationState is decoded. */
    private final ArsdkFeatureCommon.CalibrationState.Callback mCalibrationStateCallback =
            new ArsdkFeatureCommon.CalibrationState.Callback() {

                @Override
                public void onPitotCalibrationStateChanged(@Nullable ArsdkFeatureCommon.CalibrationstatePitotcalibrationstatechangedState state, int lasterror) {
                    if (lasterror != 0) {
                        mPitot.updateIsCalibrated(false).notifyUpdated();
                        return;
                    }

                    if (state != null) {
                        switch (state) {
                            case DONE:
                                mPitot.updateIsCalibrated(true).notifyUpdated();
                                break;
                            case READY:
                            case IN_PROGRESS:
                            case REQUIRED:
                                mPitot.updateIsCalibrated(false).notifyUpdated();
                                break;
                        }
                    }
                }
            };

    /** Backend of MagnetometerCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final PitotCore.Backend mBackend =
            () -> sendCommand(ArsdkFeatureCommon.Calibration.encodePitotCalibration(1));
}
