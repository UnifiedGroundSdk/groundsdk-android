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

import android.util.SparseArray;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.device.peripheral.CopterMotors;
import com.parrot.drone.groundsdk.device.peripheral.motor.MotorError;
import com.parrot.drone.groundsdk.internal.device.peripheral.CopterMotorsCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;
import com.parrot.drone.sdkcore.ulog.ULog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.EnumSet;
import java.util.Objects;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.parrot.drone.groundsdk.arsdkengine.Logging.TAG;

/** CopterMotors peripheral controller for Anafi family drones. */
public final class AnafiMotors extends DronePeripheralController {

    /** CopterMotors peripheral for which this object is the backend. */
    @NonNull
    private final CopterMotorsCore mCopterMotors;
    
    /** Dictionary containing device specific values for this component. */
    @NonNull
    private final PersistentStore.Dictionary mDeviceDict;
    
    /** Key used to access device specific dictionary for this component's settings. */
    private static final String SETTINGS_KEY = "motors";

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public AnafiMotors(@NonNull DroneController droneController) {
        super(droneController);
        mDeviceDict = mDeviceController.getDeviceDict().getDictionary(SETTINGS_KEY);
        mCopterMotors = new CopterMotorsCore(mComponentStore, mBackend);

        if (!mDeviceDict.isNew()) {
            loadLastValues();
            mCopterMotors.publish();
        }
    }

    @Override
    protected void onConnected() {
        mCopterMotors.publish();
    }

    @Override
    protected void onDisconnecting() {
        super.onDisconnecting();

        for (CopterMotors.Motor motor : CopterMotors.Motor.values()) {
            StorageEntry.ofString("motor_" + motor.ordinal() + "_version").save(mDeviceDict, Objects.requireNonNull(mCopterMotors.motors().get(motor)).getHardwareVersion());
            StorageEntry.ofString("motor_" + motor.ordinal() + "_type").save(mDeviceDict, Objects.requireNonNull(mCopterMotors.motors().get(motor)).getType());
            StorageEntry.ofString("motor_" + motor.ordinal() + "_error").save(mDeviceDict, Objects.requireNonNull(mCopterMotors.motors().get(motor)).getError().name());
        }
    }

    @Override
    protected void onDisconnected() {
        if (mDeviceDict.isNew()) {
            mCopterMotors.unpublish();
        }
    }
    
    @Override
    protected void onForgetting() {
        mDeviceDict.clear();
        mCopterMotors.unpublish();
    }
    
    private void loadLastValues() {
        for (CopterMotors.Motor motor : CopterMotors.Motor.values()) {
            final String version = StorageEntry.ofString("motor_" + motor.ordinal() + "_version").load(mDeviceDict);
            final String type = StorageEntry.ofString("motor_" + motor.ordinal() + "_type").load(mDeviceDict);
            final String error = StorageEntry.ofString("motor_" + motor.ordinal() + "_error").load(mDeviceDict);

            mCopterMotors.updateCurrentError(motor, error == null ? MotorError.NONE : MotorError.valueOf(error));
            mCopterMotors.updateMotorDetail(motor, type == null ? "Not Available" : type, version == null ? "Not Available" : version, version == null ? "Not Available" : version);
        }
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureArdrone3.SettingsState.UID) {
            ArsdkFeatureArdrone3.SettingsState.decode(command, mArdrone3SettingsStateCallbacks);
        }
    }

    /**
     * Callbacks called when a command of the feature ArsdkFeatureArdrone3.SettingsState is decoded.
     */
    private final ArsdkFeatureArdrone3.SettingsState.Callback mArdrone3SettingsStateCallbacks = new ArsdkFeatureArdrone3.SettingsState.Callback() {
        @Override
        public void onProductMotorVersionListChanged(int motorNumber, String type, String software, String hardware) {
            mCopterMotors.updateMotorDetail(CopterMotors.Motor.values()[motorNumber], type, software, hardware);
            mCopterMotors.notifyUpdated();
        }

        @Override
        public void onMotorSoftwareVersionChanged(String info) {
            if (info != null) {
                final String[] data = info.split("\\.");
                final String version = data[0] + "." + data[1];

                if (data.length > 2) {
                    final String type = data[2];
                    final int motors = Integer.parseInt(data[3]);

                    for (int x = 0; x < motors; x++) {
                        mCopterMotors.updateMotorDetail(CopterMotors.Motor.values()[x], type, version, version);
                    }
                } else {
                    for (int x = 0; x < 4; x++) {
                        mCopterMotors.updateMotorDetail(CopterMotors.Motor.values()[x], "Release", version, version);
                    }

                }

                mCopterMotors.notifyUpdated();
            }
        }

        @Override
        public void onMotorErrorStateChanged(
                @MotorAdapter.ArsdkMask int motorIds,
                @Nullable ArsdkFeatureArdrone3.SettingsstateMotorerrorstatechangedMotorerror motorError) {
            MotorError error = ErrorAdapter.from(motorError);
            for (CopterMotors.Motor motor : MotorAdapter.from(motorIds)) {
                mCopterMotors.updateCurrentError(motor, error);
            }

            mCopterMotors.notifyUpdated();
        }

        @Override
        public void onMotorErrorLastErrorChanged(
                @Nullable ArsdkFeatureArdrone3.SettingsstateMotorerrorlasterrorchangedMotorerror motorError) {
            MotorError error = ErrorAdapter.from(motorError);
            for (CopterMotors.Motor motor : CopterMotors.Motor.values()) {
                mCopterMotors.updateLatestError(motor, error);
            }
            mCopterMotors.notifyUpdated();
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final CopterMotorsCore.Backend mBackend = enable -> false;

    /**
     * Utility class to adapt arsdk motor ids to {@link CopterMotors.Motor groundsdk motors}.
     */
    private static final class MotorAdapter {

        /**
         * Int definition for all motor mask values.
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(flag = true, value = {MOTOR_0, MOTOR_1, MOTOR_2, MOTOR_3})
        @interface ArsdkMask {}

        /** Motor 0. */
        @SuppressWarnings("PointlessBitwiseExpression")
        private static final int MOTOR_0 = 1 << 0;

        /** Motor 1. */
        private static final int MOTOR_1 = 1 << 1;

        /** Motor 2. */
        private static final int MOTOR_2 = 1 << 2;

        /** Motor 3. */
        private static final int MOTOR_3 = 1 << 3;

        /** GSDK CopterMotors.Motor, by ARSDK motor mask. */
        private static final SparseArray<CopterMotors.Motor> GSDK_COPTER_MOTORS = new SparseArray<>();

        static {
            GSDK_COPTER_MOTORS.put(MOTOR_0, CopterMotors.Motor.FRONT_LEFT);
            GSDK_COPTER_MOTORS.put(MOTOR_1, CopterMotors.Motor.FRONT_RIGHT);
            GSDK_COPTER_MOTORS.put(MOTOR_2, CopterMotors.Motor.REAR_RIGHT);
            GSDK_COPTER_MOTORS.put(MOTOR_3, CopterMotors.Motor.REAR_LEFT);
        }

        /**
         * Translates an arsdk motor bitfield into its groundsdk CopterMotors.Motor set equivalent.
         *
         * @param motorBitfield bitfield of arsdk motor ids to convert
         *
         * @return the corresponding CopterMotors.Motor set
         */
        @NonNull
        static EnumSet<CopterMotors.Motor> from(@ArsdkMask int motorBitfield) {
            EnumSet<CopterMotors.Motor> motors = EnumSet.noneOf(CopterMotors.Motor.class);
            while (motorBitfield != 0) {
                @ArsdkMask int motorMask = Integer.lowestOneBit(motorBitfield);
                CopterMotors.Motor motor = GSDK_COPTER_MOTORS.get(motorMask);
                if (motor == null) {
                    ULog.w(TAG, "Unsupported motor: " + Integer.toBinaryString(motorMask));
                } else {
                    motors.add(motor);
                }
                motorBitfield ^= motorMask;
            }
            return motors;
        }
    }

    /**
     * Utility class to adapt arsdk motor errors to {@link MotorError groundsdk motor errors}.
     */
    private static final class ErrorAdapter {

        /**
         * Converts an arsdk {@link ArsdkFeatureArdrone3.SettingsstateMotorerrorstatechangedMotorerror motor error} into
         * its groundsdk {@link MotorError representation}.
         *
         * @param error arsdk motor error to convert
         *
         * @return groundsdk representation of the specified error
         */
        @NonNull
        static MotorError from(@Nullable ArsdkFeatureArdrone3.SettingsstateMotorerrorstatechangedMotorerror error) {
            if (error == null) {
                return MotorError.OTHER;
            } else switch (error) {
                case NOERROR:
                    return MotorError.NONE;
                case ERRORMOTORSTALLED:
                    return MotorError.STALLED;
                case ERRORPROPELLERSECURITY:
                    return MotorError.SECURITY_MODE;
                case ERRORRCEMERGENCYSTOP:
                    return MotorError.EMERGENCY_STOP;
                case ERRORBATTERYVOLTAGE:
                    return MotorError.BATTERY_VOLTAGE;
                case ERRORLIPOCELLS:
                    return MotorError.LIPO_CELLS;
                case ERRORTEMPERATURE:
                    return MotorError.TEMPERATURE;
                case ERRORMOSFET:
                    return MotorError.MOSFET;
                case ERROREEPROM:
                case ERRORCOMMLOST:
                case ERRORREALTIME:
                case ERRORMOTORSETTING:
                case ERRORBOOTLOADER:
                case ERRORASSERT:
                default:
                    return MotorError.OTHER;
            }
        }

        /**
         * Converts an arsdk {@link ArsdkFeatureArdrone3.SettingsstateMotorerrorlasterrorchangedMotorerror motor error}
         * into its groundsdk {@link MotorError representation}.
         *
         * @param error arsdk motor error to convert
         *
         * @return groundsdk representation of the specified error
         */
        @NonNull
        static MotorError from(@Nullable ArsdkFeatureArdrone3.SettingsstateMotorerrorlasterrorchangedMotorerror error) {
            if (error == null) {
                return MotorError.OTHER;
            } else switch (error) {
                case NOERROR:
                    return MotorError.NONE;
                case ERRORMOTORSTALLED:
                    return MotorError.STALLED;
                case ERRORPROPELLERSECURITY:
                    return MotorError.SECURITY_MODE;
                case ERRORRCEMERGENCYSTOP:
                    return MotorError.EMERGENCY_STOP;
                case ERRORBATTERYVOLTAGE:
                    return MotorError.BATTERY_VOLTAGE;
                case ERRORLIPOCELLS:
                    return MotorError.LIPO_CELLS;
                case ERRORTEMPERATURE:
                    return MotorError.TEMPERATURE;
                case ERRORMOSFET:
                    return MotorError.MOSFET;
                case ERROREEPROM:
                case ERRORCOMMLOST:
                case ERRORREALTIME:
                case ERRORMOTORSETTING:
                case ERRORBOOTLOADER:
                case ERRORASSERT:
                default:
                    return MotorError.OTHER;
            }
        }
    }
}
