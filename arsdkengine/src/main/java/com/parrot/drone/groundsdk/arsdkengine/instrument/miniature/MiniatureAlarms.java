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
import com.parrot.drone.groundsdk.device.instrument.Alarms;
import com.parrot.drone.groundsdk.internal.device.instrument.AlarmsCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Alarms instrument controller for the Mambo  drone. */
public class MiniatureAlarms extends DroneInstrumentController {

    /** The alarms from which this object is the backend. */
    @NonNull
    private final AlarmsCore mAlarms;

    /** {@code true} if the drone is flying, otherwise {@code false}. */
    private boolean mIsFlying;

    /** Latest {@link Alarms.Alarm.Kind#HOVERING_DIFFICULTIES_NO_GPS_TOO_DARK} alarm level received from the drone. */
    @NonNull
    private Alarms.Alarm.Level mDroneHoveringTooDarkAlarmLevel;

    /** Latest {@link Alarms.Alarm.Kind#HOVERING_DIFFICULTIES_NO_GPS_TOO_HIGH} alarm level received from the drone. */
    @NonNull
    private Alarms.Alarm.Level mDroneHoveringTooHighAlarmLevel;

    /**
     * Constructor.
     *
     * @param droneController The drone controller that owns this component controller.
     */
    public MiniatureAlarms(@NonNull DroneController droneController) {
        super(droneController);
        mAlarms = new AlarmsCore(mComponentStore)
                .updateAlarmsLevel(Alarms.Alarm.Level.OFF, Alarms.Alarm.Kind.values());
        mDroneHoveringTooDarkAlarmLevel = Alarms.Alarm.Level.OFF;
        mDroneHoveringTooHighAlarmLevel = Alarms.Alarm.Level.OFF;
    }

    @Override
    public void onConnected() {
        mAlarms.publish();
    }

    @Override
    public void onDisconnected() {
        mAlarms.unpublish();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureMinidrone.PilotingState.UID) {
            ArsdkFeatureMinidrone.PilotingState.decode(command, mPilotingStateCallback);
        }  else if (featureId == ArsdkFeatureCommon.CommonState.UID) {
            ArsdkFeatureCommon.CommonState.decode(command, mCommonStateCallback);
        }
    }

    /** Callbacks called when a command of the feature ArsdkFeatureMinidrone.PilotingState is decoded. */
    private final ArsdkFeatureMinidrone.PilotingState.Callback mPilotingStateCallback =
            new ArsdkFeatureMinidrone.PilotingState.Callback() {

                @Override
                public void onFlyingStateChanged(
                        @Nullable ArsdkFeatureMinidrone.PilotingstateFlyingstatechangedState state) {
                }

                @Override
                public void onAlertStateChanged(
                        @Nullable ArsdkFeatureMinidrone.PilotingstateAlertstatechangedState state) {
                    if (state != null) {
                        switch (state) {
                            case NONE:
                                mAlarms.updateAlarmLevel(Alarms.Alarm.Kind.POWER, Alarms.Alarm.Level.OFF);
                                mAlarms.updateAlarmLevel(Alarms.Alarm.Kind.MOTOR_CUT_OUT, Alarms.Alarm.Level.OFF)
                                        .updateAlarmLevel(Alarms.Alarm.Kind.USER_EMERGENCY, Alarms.Alarm.Level.OFF)
                                        .notifyUpdated();
                                break;
                            case CRITICAL_BATTERY:
                                mAlarms.updateAlarmLevel(Alarms.Alarm.Kind.MOTOR_CUT_OUT, Alarms.Alarm.Level.OFF)
                                        .updateAlarmLevel(Alarms.Alarm.Kind.USER_EMERGENCY, Alarms.Alarm.Level.OFF)
                                        .updateAlarmLevel(Alarms.Alarm.Kind.POWER, Alarms.Alarm.Level.CRITICAL)
                                        .notifyUpdated();
                                break;
                            case LOW_BATTERY:
                                mAlarms.updateAlarmLevel(Alarms.Alarm.Kind.MOTOR_CUT_OUT, Alarms.Alarm.Level.OFF)
                                        .updateAlarmLevel(Alarms.Alarm.Kind.USER_EMERGENCY, Alarms.Alarm.Level.OFF)
                                        .updateAlarmLevel(Alarms.Alarm.Kind.POWER, Alarms.Alarm.Level.WARNING)
                                        .notifyUpdated();
                                break;
                            case CUT_OUT:
                                // remove only non-persistent alarms
                                mAlarms.updateAlarmLevel(Alarms.Alarm.Kind.USER_EMERGENCY, Alarms.Alarm.Level.OFF)
                                        .updateAlarmLevel(Alarms.Alarm.Kind.MOTOR_CUT_OUT, Alarms.Alarm.Level.CRITICAL)
                                        .notifyUpdated();
                                break;
                            case USER:
                                // remove only non-persistent alarms
                                mAlarms.updateAlarmLevel(Alarms.Alarm.Kind.MOTOR_CUT_OUT, Alarms.Alarm.Level.OFF)
                                        .updateAlarmLevel(Alarms.Alarm.Kind.USER_EMERGENCY, Alarms.Alarm.Level.CRITICAL)
                                        .notifyUpdated();
                                break;
                        }
                    }
                }
            };

    /** Callbacks called when a command of the feature ArsdkFeatureCommon.CommonState is decoded. */
    private final ArsdkFeatureCommon.CommonState.Callback mCommonStateCallback
            = new ArsdkFeatureCommon.CommonState.Callback() {

        @Override
        public void onSensorsStatesListChanged(
                @Nullable ArsdkFeatureCommon.CommonstateSensorsstateslistchangedSensorname sensorName,
                int sensorState) {
            if (sensorName == ArsdkFeatureCommon.CommonstateSensorsstateslistchangedSensorname.VERTICAL_CAMERA) {
                Alarms.Alarm.Level alarmLevel = sensorState == 1 ?
                        Alarms.Alarm.Level.OFF : Alarms.Alarm.Level.CRITICAL;
                mAlarms.updateAlarmLevel(Alarms.Alarm.Kind.VERTICAL_CAMERA, alarmLevel).notifyUpdated();
            }
        }
    };
}
