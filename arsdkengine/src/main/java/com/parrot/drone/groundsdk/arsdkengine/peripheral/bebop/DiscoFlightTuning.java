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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.device.Drone;
import com.parrot.drone.groundsdk.device.peripheral.FixedWingFlightTuning;
import com.parrot.drone.groundsdk.internal.device.peripheral.FixedWingFlightTuningCore;
import com.parrot.drone.groundsdk.value.IntegerRange;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;
import com.parrot.drone.sdkcore.ulog.ULog;

import static com.parrot.drone.groundsdk.arsdkengine.Logging.TAG;

/**
 * FixedWingFlightTuning peripheral controller for Disco fixed-wing drones.
 * <p>
 * Handles {@code ardrone3.PilotingSettings} commands and
 * {@code ardrone3.PilotingSettingsState} events for the circling and
 * autonomous-flight-maxima groups. Published only when connected to a
 * {@link Drone.Model#DISCO} device.
 */
public final class DiscoFlightTuning extends DronePeripheralController {

    /** Key used to access the preset dictionary for this peripheral's settings. */
    private static final String SETTINGS_KEY = "discoFlightTuning";

    // preset store bindings

    /** Circling direction preset entry. */
    private static final StorageEntry<FixedWingFlightTuning.CirclingDirection> CIRCLING_DIRECTION_PRESET =
            StorageEntry.ofEnum("circlingDirection", FixedWingFlightTuning.CirclingDirection.class);

    /** Circling altitude preset entry (metres, integer). */
    private static final StorageEntry<Integer> CIRCLING_ALTITUDE_PRESET =
            StorageEntry.ofInteger("circlingAltitude");

    /** Autonomous-flight maximum horizontal speed preset entry. */
    private static final StorageEntry<Double> AUTO_MAX_H_SPEED_PRESET =
            StorageEntry.ofDouble("autoMaxHSpeed");

    /** Autonomous-flight maximum vertical speed preset entry. */
    private static final StorageEntry<Double> AUTO_MAX_V_SPEED_PRESET =
            StorageEntry.ofDouble("autoMaxVSpeed");

    /** Autonomous-flight maximum horizontal acceleration preset entry. */
    private static final StorageEntry<Double> AUTO_MAX_H_ACCEL_PRESET =
            StorageEntry.ofDouble("autoMaxHAccel");

    /** Autonomous-flight maximum vertical acceleration preset entry. */
    private static final StorageEntry<Double> AUTO_MAX_V_ACCEL_PRESET =
            StorageEntry.ofDouble("autoMaxVAccel");

    /** Autonomous-flight maximum rotation speed preset entry. */
    private static final StorageEntry<Double> AUTO_MAX_ROT_SPEED_PRESET =
            StorageEntry.ofDouble("autoMaxRotSpeed");

    // device-specific store bindings (ranges sent by the drone)

    /** Circling altitude bounds range. */
    private static final StorageEntry<IntegerRange> CIRCLING_ALTITUDE_RANGE_SETTING =
            StorageEntry.ofIntegerRange("circlingAltitudeRange");

    /** The peripheral from which this object is the backend. */
    @NonNull
    private final FixedWingFlightTuningCore mFlightTuning;

    /** Dictionary containing current preset values for this peripheral. */
    @Nullable
    private PersistentStore.Dictionary mPresetDict;

    /** Dictionary containing device-specific values (ranges) for this peripheral. */
    @Nullable
    private final PersistentStore.Dictionary mDeviceDict;

    // Last values received from the drone (null = not yet received)

    @Nullable
    private FixedWingFlightTuning.CirclingDirection mCirclingDirection;

    @Nullable
    private Integer mCirclingAltitude;

    @Nullable
    private Double mAutoMaxHSpeed;

    @Nullable
    private Double mAutoMaxVSpeed;

    @Nullable
    private Double mAutoMaxHAccel;

    @Nullable
    private Double mAutoMaxVAccel;

    @Nullable
    private Double mAutoMaxRotSpeed;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public DiscoFlightTuning(@NonNull DroneController droneController) {
        super(droneController);
        mDeviceDict = offlineSettingsEnabled()
                ? mDeviceController.getDeviceDict().getDictionary(SETTINGS_KEY) : null;
        mPresetDict = offlineSettingsEnabled()
                ? mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY) : null;
        mFlightTuning = new FixedWingFlightTuningCore(mComponentStore, mCirclingBackend, mAutonomousBackend);
        loadPersistedData();
        if (isPersisted()) {
            mFlightTuning.publish();
        }
    }

    @Override
    protected void onConnected() {
        if (mDeviceController.getDevice().getModel() != Drone.Model.DISCO) {
            return;
        }
        applyPresets();
        mFlightTuning.publish();
    }

    @Override
    protected void onDisconnected() {
        mFlightTuning.cancelSettingsRollbacks();
        if (isPersisted()) {
            mFlightTuning.notifyUpdated();
        } else {
            mFlightTuning.unpublish();
        }
    }

    @Override
    protected void onPresetChange() {
        mPresetDict = mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY);
        if (isConnected()) {
            applyPresets();
        }
        mFlightTuning.notifyUpdated();
    }

    @Override
    protected void onForgetting() {
        if (mDeviceDict != null) {
            mDeviceDict.clear().commit();
        }
        mFlightTuning.unpublish();
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureArdrone3.PilotingSettingsState.UID) {
            ArsdkFeatureArdrone3.PilotingSettingsState.decode(command, mPilotingSettingsStateCallback);
        }
    }

    // ---- persistence helpers ----

    /**
     * Tells whether device-specific settings are persisted for this component.
     *
     * @return {@code true} if persisted device settings exist, otherwise {@code false}
     */
    private boolean isPersisted() {
        return mDeviceDict != null && !mDeviceDict.isNew();
    }

    /**
     * Loads persisted device-specific data (ranges) and preset values into the component.
     */
    private void loadPersistedData() {
        IntegerRange altitudeRange = CIRCLING_ALTITUDE_RANGE_SETTING.load(mDeviceDict);
        if (altitudeRange != null) {
            mFlightTuning.circlingAltitude().updateBounds(altitudeRange);
        }
        applyPresets();
    }

    /**
     * Applies all persisted presets by sending any stale values to the drone.
     */
    private void applyPresets() {
        applyCirclingDirection(CIRCLING_DIRECTION_PRESET.load(mPresetDict));
        applyCirclingAltitude(CIRCLING_ALTITUDE_PRESET.load(mPresetDict));
        applyAutoMaxHSpeed(AUTO_MAX_H_SPEED_PRESET.load(mPresetDict));
        applyAutoMaxVSpeed(AUTO_MAX_V_SPEED_PRESET.load(mPresetDict));
        applyAutoMaxHAccel(AUTO_MAX_H_ACCEL_PRESET.load(mPresetDict));
        applyAutoMaxVAccel(AUTO_MAX_V_ACCEL_PRESET.load(mPresetDict));
        applyAutoMaxRotSpeed(AUTO_MAX_ROT_SPEED_PRESET.load(mPresetDict));
    }

    // ---- apply helpers (preset-on-connect + backend-on-user-change) ----

    private boolean applyCirclingDirection(@Nullable FixedWingFlightTuning.CirclingDirection direction) {
        if (direction == null) {
            direction = mCirclingDirection;
        }
        if (direction == null) {
            return false;
        }
        boolean updating = direction != mCirclingDirection
                && sendCommand(ArsdkFeatureArdrone3.PilotingSettings.encodeCirclingDirection(
                        directionToArsdk(direction)));
        mCirclingDirection = direction;
        mFlightTuning.circlingDirection().updateValue(direction);
        return updating;
    }

    private boolean applyCirclingAltitude(@Nullable Integer altitude) {
        if (altitude == null) {
            altitude = mCirclingAltitude;
        }
        if (altitude == null) {
            return false;
        }
        boolean updating = !altitude.equals(mCirclingAltitude)
                && sendCommand(ArsdkFeatureArdrone3.PilotingSettings.encodeCirclingAltitude(altitude));
        mCirclingAltitude = altitude;
        mFlightTuning.circlingAltitude().updateValue(altitude);
        return updating;
    }

    private boolean applyAutoMaxHSpeed(@Nullable Double value) {
        if (value == null) {
            value = mAutoMaxHSpeed;
        }
        if (value == null) {
            return false;
        }
        boolean updating = !value.equals(mAutoMaxHSpeed)
                && sendCommand(ArsdkFeatureArdrone3.PilotingSettings.encodeSetAutonomousFlightMaxHorizontalSpeed(
                        value.floatValue()));
        mAutoMaxHSpeed = value;
        mFlightTuning.autonomousFlightMaxHorizontalSpeed().updateValue(value);
        return updating;
    }

    private boolean applyAutoMaxVSpeed(@Nullable Double value) {
        if (value == null) {
            value = mAutoMaxVSpeed;
        }
        if (value == null) {
            return false;
        }
        boolean updating = !value.equals(mAutoMaxVSpeed)
                && sendCommand(ArsdkFeatureArdrone3.PilotingSettings.encodeSetAutonomousFlightMaxVerticalSpeed(
                        value.floatValue()));
        mAutoMaxVSpeed = value;
        mFlightTuning.autonomousFlightMaxVerticalSpeed().updateValue(value);
        return updating;
    }

    private boolean applyAutoMaxHAccel(@Nullable Double value) {
        if (value == null) {
            value = mAutoMaxHAccel;
        }
        if (value == null) {
            return false;
        }
        boolean updating = !value.equals(mAutoMaxHAccel)
                && sendCommand(ArsdkFeatureArdrone3.PilotingSettings.encodeSetAutonomousFlightMaxHorizontalAcceleration(
                        value.floatValue()));
        mAutoMaxHAccel = value;
        mFlightTuning.autonomousFlightMaxHorizontalAcceleration().updateValue(value);
        return updating;
    }

    private boolean applyAutoMaxVAccel(@Nullable Double value) {
        if (value == null) {
            value = mAutoMaxVAccel;
        }
        if (value == null) {
            return false;
        }
        boolean updating = !value.equals(mAutoMaxVAccel)
                && sendCommand(ArsdkFeatureArdrone3.PilotingSettings.encodeSetAutonomousFlightMaxVerticalAcceleration(
                        value.floatValue()));
        mAutoMaxVAccel = value;
        mFlightTuning.autonomousFlightMaxVerticalAcceleration().updateValue(value);
        return updating;
    }

    private boolean applyAutoMaxRotSpeed(@Nullable Double value) {
        if (value == null) {
            value = mAutoMaxRotSpeed;
        }
        if (value == null) {
            return false;
        }
        boolean updating = !value.equals(mAutoMaxRotSpeed)
                && sendCommand(ArsdkFeatureArdrone3.PilotingSettings.encodeSetAutonomousFlightMaxRotationSpeed(
                        value.floatValue()));
        mAutoMaxRotSpeed = value;
        mFlightTuning.autonomousFlightMaxRotationSpeed().updateValue(value);
        return updating;
    }

    // ---- enum conversion helpers ----

    @NonNull
    private static ArsdkFeatureArdrone3.PilotingsettingsCirclingdirectionValue directionToArsdk(
            @NonNull FixedWingFlightTuning.CirclingDirection direction) {
        switch (direction) {
            case CW:  return ArsdkFeatureArdrone3.PilotingsettingsCirclingdirectionValue.CW;
            case CCW: return ArsdkFeatureArdrone3.PilotingsettingsCirclingdirectionValue.CCW;
            default:  return ArsdkFeatureArdrone3.PilotingsettingsCirclingdirectionValue.CW;
        }
    }

    @Nullable
    private static FixedWingFlightTuning.CirclingDirection directionFromArsdk(
            @Nullable ArsdkFeatureArdrone3.PilotingsettingsstateCirclingdirectionchangedValue arsdkValue) {
        if (arsdkValue == null) {
            return null;
        }
        switch (arsdkValue) {
            case CW:  return FixedWingFlightTuning.CirclingDirection.CW;
            case CCW: return FixedWingFlightTuning.CirclingDirection.CCW;
            default:  return null;
        }
    }

    // ---- ARSDK event callbacks ----

    /** Callbacks called when a command of the feature ArsdkFeatureArdrone3.PilotingSettingsState is decoded. */
    @SuppressWarnings("FieldCanBeLocal")
    private final ArsdkFeatureArdrone3.PilotingSettingsState.Callback mPilotingSettingsStateCallback =
            new ArsdkFeatureArdrone3.PilotingSettingsState.Callback() {

                @Override
                public void onCirclingDirectionChanged(
                        @Nullable ArsdkFeatureArdrone3.PilotingsettingsstateCirclingdirectionchangedValue value) {
                    FixedWingFlightTuning.CirclingDirection dir = directionFromArsdk(value);
                    if (dir == null) {
                        return;
                    }
                    mCirclingDirection = dir;
                    mFlightTuning.circlingDirection().updateValue(dir);
                    mFlightTuning.notifyUpdated();
                }

                @Override
                public void onCirclingAltitudeChanged(int current, int min, int max) {
                    if (min > max) {
                        ULog.w(TAG, "Invalid circling altitude bounds, skip this event");
                        return;
                    }
                    IntegerRange range = IntegerRange.of(min, max);
                    CIRCLING_ALTITUDE_RANGE_SETTING.save(mDeviceDict, range);
                    mFlightTuning.circlingAltitude().updateBounds(range);
                    mCirclingAltitude = current;
                    mFlightTuning.circlingAltitude().updateValue(current);
                    mFlightTuning.notifyUpdated();
                }

                @Override
                public void onAutonomousFlightMaxHorizontalSpeed(float value) {
                    mAutoMaxHSpeed = (double) value;
                    mFlightTuning.autonomousFlightMaxHorizontalSpeed().updateValue(value);
                    mFlightTuning.notifyUpdated();
                }

                @Override
                public void onAutonomousFlightMaxVerticalSpeed(float value) {
                    mAutoMaxVSpeed = (double) value;
                    mFlightTuning.autonomousFlightMaxVerticalSpeed().updateValue(value);
                    mFlightTuning.notifyUpdated();
                }

                @Override
                public void onAutonomousFlightMaxHorizontalAcceleration(float value) {
                    mAutoMaxHAccel = (double) value;
                    mFlightTuning.autonomousFlightMaxHorizontalAcceleration().updateValue(value);
                    mFlightTuning.notifyUpdated();
                }

                @Override
                public void onAutonomousFlightMaxVerticalAcceleration(float value) {
                    mAutoMaxVAccel = (double) value;
                    mFlightTuning.autonomousFlightMaxVerticalAcceleration().updateValue(value);
                    mFlightTuning.notifyUpdated();
                }

                @Override
                public void onAutonomousFlightMaxRotationSpeed(float value) {
                    mAutoMaxRotSpeed = (double) value;
                    mFlightTuning.autonomousFlightMaxRotationSpeed().updateValue(value);
                    mFlightTuning.notifyUpdated();
                }
            };

    // ---- backends ----

    /** Backend of FixedWingFlightTuningCore for circling settings. */
    @SuppressWarnings("FieldCanBeLocal")
    private final FixedWingFlightTuningCore.CirclingBackend mCirclingBackend =
            new FixedWingFlightTuningCore.CirclingBackend() {

                @Override
                public boolean setCirclingDirection(@NonNull FixedWingFlightTuning.CirclingDirection direction) {
                    boolean updating = applyCirclingDirection(direction);
                    CIRCLING_DIRECTION_PRESET.save(mPresetDict, direction);
                    if (!updating) {
                        mFlightTuning.notifyUpdated();
                    }
                    return updating;
                }

                @Override
                public boolean setCirclingAltitude(int altitude) {
                    boolean updating = applyCirclingAltitude(altitude);
                    CIRCLING_ALTITUDE_PRESET.save(mPresetDict, altitude);
                    if (!updating) {
                        mFlightTuning.notifyUpdated();
                    }
                    return updating;
                }
            };

    /** Backend of FixedWingFlightTuningCore for autonomous-flight maxima settings. */
    @SuppressWarnings("FieldCanBeLocal")
    private final FixedWingFlightTuningCore.AutonomousFlightBackend mAutonomousBackend =
            new FixedWingFlightTuningCore.AutonomousFlightBackend() {

                @Override
                public boolean setAutonomousFlightMaxHorizontalSpeed(double speed) {
                    boolean updating = applyAutoMaxHSpeed(speed);
                    AUTO_MAX_H_SPEED_PRESET.save(mPresetDict, speed);
                    if (!updating) {
                        mFlightTuning.notifyUpdated();
                    }
                    return updating;
                }

                @Override
                public boolean setAutonomousFlightMaxVerticalSpeed(double speed) {
                    boolean updating = applyAutoMaxVSpeed(speed);
                    AUTO_MAX_V_SPEED_PRESET.save(mPresetDict, speed);
                    if (!updating) {
                        mFlightTuning.notifyUpdated();
                    }
                    return updating;
                }

                @Override
                public boolean setAutonomousFlightMaxHorizontalAcceleration(double acceleration) {
                    boolean updating = applyAutoMaxHAccel(acceleration);
                    AUTO_MAX_H_ACCEL_PRESET.save(mPresetDict, acceleration);
                    if (!updating) {
                        mFlightTuning.notifyUpdated();
                    }
                    return updating;
                }

                @Override
                public boolean setAutonomousFlightMaxVerticalAcceleration(double acceleration) {
                    boolean updating = applyAutoMaxVAccel(acceleration);
                    AUTO_MAX_V_ACCEL_PRESET.save(mPresetDict, acceleration);
                    if (!updating) {
                        mFlightTuning.notifyUpdated();
                    }
                    return updating;
                }

                @Override
                public boolean setAutonomousFlightMaxRotationSpeed(double speed) {
                    boolean updating = applyAutoMaxRotSpeed(speed);
                    AUTO_MAX_ROT_SPEED_PRESET.save(mPresetDict, speed);
                    if (!updating) {
                        mFlightTuning.notifyUpdated();
                    }
                    return updating;
                }
            };
}
