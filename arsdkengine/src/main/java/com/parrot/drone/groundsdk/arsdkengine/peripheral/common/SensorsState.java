/*
 *     Copyright (C) 2019 Parrot Drones SAS / 2020 SMS
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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.common;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.device.peripheral.sensor.Sensor;
import com.parrot.drone.groundsdk.device.peripheral.sensor.SensorState;
import com.parrot.drone.groundsdk.internal.device.peripheral.SensorsStateCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** SensorsState peripheral controller for all drones. */
public final class SensorsState extends DronePeripheralController {

    /** SensorsState peripheral for which this object is the backend. */
    @NonNull
    private final SensorsStateCore mSensorsState;

    /** Dictionary containing device specific values for this component. */
    @NonNull
    private final PersistentStore.Dictionary mDeviceDict;

    /** Key used to access device specific dictionary for this component's settings. */
    private static final String SETTINGS_KEY = "sensors";

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public SensorsState(@NonNull DroneController droneController) {
        super(droneController);
        mDeviceDict = mDeviceController.getDeviceDict().getDictionary(SETTINGS_KEY);
        mSensorsState = new SensorsStateCore(mComponentStore);

        if (!mDeviceDict.isNew()) {
            loadLastValues();
            mSensorsState.publish();
        }
    }

    @Override
    protected void onConnected() {
        mSensorsState.publish();
    }

    @Override
    protected void onDisconnecting() {
        super.onDisconnecting();

        for (Sensor sensor : Sensor.values()) {
            StorageEntry.ofString(sensor.name()).save(mDeviceDict, Objects.requireNonNull(mSensorsState.sensors().get(sensor)).name());
        }
    }

    @Override
    protected void onDisconnected() {
        if (mDeviceDict.isNew()) {
            mSensorsState.unpublish();
        }
    }

    @Override
    protected void onForgetting() {
        mDeviceDict.clear();
        mSensorsState.unpublish();
    }

    private void loadLastValues() {
        for (Sensor sensor : Sensor.values()) {
            mSensorsState.updateSensor(sensor, SensorState.valueOf(StorageEntry.ofString(sensor.name()).load(mDeviceDict)));
        }
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureCommon.CommonState.UID) {
            ArsdkFeatureCommon.CommonState.decode(command, mCommonStateCallbacks);
        }
    }

    /**
     * Callbacks called when a command of the feature ArsdkFeatureArdrone3.SettingsState is decoded.
     */
    private final ArsdkFeatureCommon.CommonState.Callback mCommonStateCallbacks = new ArsdkFeatureCommon.CommonState.Callback() {

        @Override
        public void onSensorsStatesListChanged(@Nullable ArsdkFeatureCommon.CommonstateSensorsstateslistchangedSensorname sensorname, int sensorstate) {
            if (sensorname != null) {
                mSensorsState.updateSensor(Sensor.valueOf(sensorname.name()), SensorState.values()[sensorstate]);
                mSensorsState.notifyUpdated();
            }
        }
    };
}
