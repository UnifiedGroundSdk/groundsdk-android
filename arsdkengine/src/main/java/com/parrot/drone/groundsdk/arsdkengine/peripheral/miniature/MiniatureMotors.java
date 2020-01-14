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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.device.peripheral.CopterMotors;
import com.parrot.drone.groundsdk.internal.device.peripheral.CopterMotorsCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;

/** CopterMotors peripheral controller for Anafi family drones. */
public final class MiniatureMotors extends DronePeripheralController {

    /** CopterMotors peripheral for which this object is the backend. */
    @NonNull
    private final CopterMotorsCore mCopterMotors;

    private Integer mCutOffMode;
    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public MiniatureMotors(@NonNull DroneController droneController) {
        super(droneController);
        mCopterMotors = new CopterMotorsCore(mComponentStore, mBackend);
    }

    @Override
    protected void onConnected() {
        mCopterMotors.publish();
    }

    @Override
    protected void onDisconnected() {
        mCopterMotors.cancelSettingsRollbacks();
        mCopterMotors.unpublish();
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureMinidrone.SettingsState.UID) {
            ArsdkFeatureMinidrone.SettingsState.decode(command, mSettingsStateCallback);
        }
    }

    /**
     * Callbacks called when a command of the feature ArsdkFeatureArdrone3.SettingsState is decoded.
     */
    private final ArsdkFeatureMinidrone.SettingsState.Callback mSettingsStateCallback = new ArsdkFeatureMinidrone.SettingsState.Callback() {
        @Override
        public void onProductMotorsVersionChanged(int motor, String type, String software, String hardware) {
            mCopterMotors.updateMotorDetail(CopterMotors.Motor.values()[motor], type, software, hardware);
            mCopterMotors.notifyUpdated();
        }

        @Override
        public void onCutOutModeChanged(int enable) {
            mCopterMotors.updateCutOutMode(enable == 1);
            mCopterMotors.notifyUpdated();
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final CopterMotorsCore.Backend mBackend = enable -> sendCommand(ArsdkFeatureMinidrone.Settings.encodeCutOutMode(enable ? 1 : 0));
}
