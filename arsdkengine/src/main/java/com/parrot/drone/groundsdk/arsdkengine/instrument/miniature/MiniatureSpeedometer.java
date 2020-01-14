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

package com.parrot.drone.groundsdk.arsdkengine.instrument.miniature;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.instrument.DroneInstrumentController;
import com.parrot.drone.groundsdk.internal.device.instrument.SpeedometerCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;

import static com.parrot.drone.groundsdk.arsdkengine.devicecontroller.MiniatureFamilyDroneController.setFromQuaternion2;

/** Speedometer instrument controller for Mambo drones. */
public class MiniatureSpeedometer extends DroneInstrumentController {

    /** The speedometer from which this object is the backend. */
    @NonNull
    private final SpeedometerCore mSpeedometer;

    /**
     * Constructor.
     *
     * @param droneController The drone controller that owns this component controller.
     */
    public MiniatureSpeedometer(@NonNull DroneController droneController) {
        super(droneController);
        mSpeedometer = new SpeedometerCore(mComponentStore);
    }

    @Override
    public void onConnected() {
        mSpeedometer.publish();
    }

    @Override
    public void onDisconnected() {
        mSpeedometer.unpublish();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureMinidrone.NavigationDataState.UID) {
            ArsdkFeatureMinidrone.NavigationDataState.decode(command, mNavigationDataStateCallback);
        }
    }

    private final ArsdkFeatureMinidrone.NavigationDataState.Callback mNavigationDataStateCallback =
            new ArsdkFeatureMinidrone.NavigationDataState.Callback() {

                /** Current drone yaw in radians. */
                private float mYaw;

                @Override
                public void onDroneSpeed(float speedX, float speedY, float speedZ, int ts) {
                    double sin = Math.sin(mYaw);
                    double cos = Math.cos(mYaw);
                    mSpeedometer.updateGroundSpeed(Math.sqrt(Math.pow(speedX, 2) + Math.pow(speedY, 2)))
                                .updateNorthSpeed(speedX)
                                .updateEastSpeed(speedY)
                                .updateDownSpeed(speedZ)
                                .updateForwardSpeed(cos * speedX + sin * speedY)
                                .updateRightSpeed(-sin * speedX + cos * speedY)
                                .notifyUpdated();
                }

                public void onDroneQuaternion(float qW, float qX, float qY, float qZ, int ts) {
                    final double[] results = setFromQuaternion2(qW, qX, qY, qZ);
                    mYaw = (float) results[2];
                }
            };
}
