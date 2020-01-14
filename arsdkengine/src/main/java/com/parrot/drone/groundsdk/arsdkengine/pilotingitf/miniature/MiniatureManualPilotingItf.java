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

package com.parrot.drone.groundsdk.arsdkengine.pilotingitf.miniature;

import com.parrot.drone.groundsdk.arsdkengine.blackbox.BlackBoxDroneSession;
import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.PilotingItfActivationController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.ActivablePilotingItfController;
import com.parrot.drone.groundsdk.internal.device.pilotingitf.ManualFlightPilotingItfCore;
import com.parrot.drone.groundsdk.value.DoubleRange;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** ManualCopter piloting interface controller for Anafi family drones. */
public class MiniatureManualPilotingItf extends ActivablePilotingItfController {

    /** Key used to access preset and range dictionaries for this piloting interface settings. */
    private static final String SETTINGS_KEY = "manualCopter";

    // preset store bindings

    /** Max pitch/roll preset entry. */
    private static final StorageEntry<Double> MAX_PITCH_ROLL_PRESET = StorageEntry.ofDouble("maxPitchRoll");

    /** Max pitch/roll velocity preset entry. */
    private static final StorageEntry<Double> MAX_PITCH_ROLL_VELOCITY_PRESET =
            StorageEntry.ofDouble("maxPitchRollVelocity");

    /** Max vertical speed preset entry. */
    private static final StorageEntry<Double> MAX_VERTICAL_SPEED_PRESET = StorageEntry.ofDouble("maxVerticalSpeed");

    /** Max yaw rotation speed preset entry. */
    private static final StorageEntry<Double> MAX_YAW_ROTATION_SPEED_PRESET =
            StorageEntry.ofDouble("maxYawRotationSpeed");

    /** Banked turn mode preset entry. */
    private static final StorageEntry<Boolean> BANKED_TURN_MODE_PRESET = StorageEntry.ofBoolean("bankedTurnMode");

    /** Thrown take-off mode preset entry. */
    private static final StorageEntry<Boolean> THROWN_TAKEOFF_MODE_PRESET = StorageEntry.ofBoolean("thrownTakeOffMode");

    // device specific store bindings

    /** Max pitch/roll range device setting. */
    private static final StorageEntry<DoubleRange> MAX_PITCH_ROLL_RANGE_SETTING =
            StorageEntry.ofDoubleRange("maxPitchRollRange");

    /** Max pitch/roll velocity range device setting. */
    private static final StorageEntry<DoubleRange> MAX_PITCH_ROLL_VELOCITY_RANGE_SETTING =
            StorageEntry.ofDoubleRange("maxPitchRollVelocityRange");

    /** Max vertical speed range device setting. */
    private static final StorageEntry<DoubleRange> MAX_VERTICAL_SPEED_RANGE_SETTING =
            StorageEntry.ofDoubleRange("maxVerticalSpeedRange");

    /** Max yaw rotation speed range device setting. */
    private static final StorageEntry<DoubleRange> MAX_YAW_ROTATION_SPEED_RANGE_SETTING =
            StorageEntry.ofDoubleRange("maxYawRotationSpeedRange");

    /** Banked turn mode support device setting. */
    private static final StorageEntry<Boolean> BANKED_TURN_MODE_SUPPORT_SETTING =
            StorageEntry.ofBoolean("bankedTurnModeSupport");

    /** Thrown take off mode support device setting. */
    private static final StorageEntry<Boolean> THROWN_TAKE_OFF_MODE_SUPPORT_SETTING =
            StorageEntry.ofBoolean("thrownTakeOffModeSupport");


    /** Piloting interface for which this object is the backend. */
    @NonNull
    private final ManualFlightPilotingItfCore mPilotingItf;

    /** Persists device specific values for this piloting interface, such as settings ranges, supported status. */
    @Nullable
    private final PersistentStore.Dictionary mDeviceDict;

    /** Persists current preset values for this piloting interface. */
    @Nullable
    private PersistentStore.Dictionary mPresetDict;

    /** Max pitch/roll. */
    @Nullable
    private Double mMaxPitchRoll;

    /** Max pitch/roll velocity. */
    @Nullable
    private Double mMaxPitchRollVelocity;

    /** Max vertical speed. */
    @Nullable
    private Double mMaxVerticalSpeed;

    /** Max yaw rotation speed. */
    @Nullable
    private Double mMaxYawRotationSpeed;

    /** Banked turn mode. */
    @Nullable
    private Boolean mBankedTurnMode;

    /** Thrown take-off mode. */
    @Nullable
    private Boolean mThrownTakeOffMode = false;

    private int autoTakeOffMode;

    /**
     * Constructor.
     *
     * @param activationController activation controller that owns this piloting interface controller
     */
    public MiniatureManualPilotingItf(@NonNull PilotingItfActivationController activationController) {
        this(activationController, false);
    }
    public MiniatureManualPilotingItf(@NonNull PilotingItfActivationController activationController, @NonNull boolean withHoverLock) {
        super(activationController, true);
        mPresetDict = offlineSettingsEnabled() ? mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY) : null;
        mDeviceDict = offlineSettingsEnabled() ? mDeviceController.getDeviceDict().getDictionary(SETTINGS_KEY) : null;
        mPilotingItf = new ManualFlightPilotingItfCore(mComponentStore, new Backend());

        loadPersistedData();
        if (isPersisted()) {
            mPilotingItf.publish();
        }

        MiniatureManualPilotingItf.this.setWithHoverLock(withHoverLock);
    }



    @Override
    public void requestActivation() {
        super.requestActivation();
        notifyActive();
    }

    @Override
    public void requestDeactivation() {
        super.requestDeactivation();
        notifyIdle();
    }

    @Override
    @NonNull
    public final ManualFlightPilotingItfCore getPilotingItf() {
        return mPilotingItf;
    }

    @Override
    protected final void onConnecting() {
        super.onConnecting();
        notifyIdle();
    }

    @Override
    protected final void onConnected() {
        super.onConnected();
        applyPresets();

        mPilotingItf.publish();
    }

    @Override
    protected final void onDisconnected() {
        // clear all non saved settings
        mPilotingItf.cancelSettingsRollbacks()
                    .updateCanLand(false)
                    .updateCanTakeOff(false)
                    .updateSmartWillDoThrownTakeOff(false);
        if (!isPersisted()) {
            mPilotingItf.unpublish();
        }
        super.onDisconnected();
    }

    @Override
    protected final void onPresetChange() {
        mPresetDict = mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY);
        if (isConnected()) {
            applyPresets();
        }
        mPilotingItf.notifyUpdated();
    }

    @Override
    protected final void onForgetting() {
        if (mDeviceDict != null) {
            mDeviceDict.clear().commit();
        }
        mPilotingItf.unpublish();
    }

    @Override
    public final void onCommandReceived(@NonNull ArsdkCommand command) {
        int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureMinidrone.PilotingSettingsState.UID) {
            ArsdkFeatureMinidrone.PilotingSettingsState.decode(command, mPilotingSettingsStateCallback);
        } else if (featureId == ArsdkFeatureMinidrone.PilotingState.UID) {
            ArsdkFeatureMinidrone.PilotingState.decode(command, mPilotingStateCallback);
        } else if (featureId == ArsdkFeatureMinidrone.SpeedSettingsState.UID) {
            ArsdkFeatureMinidrone.SpeedSettingsState.decode(command, mSpeedSettingsStateCallback);
        }
    }

    /**
     * Tells whether device specific settings are persisted for this component.
     *
     * @return {@code true} if the component has persisted device settings, otherwise {@code false}
     */
    private boolean isPersisted() {
        return mDeviceDict != null && !mDeviceDict.isNew();
    }

    /**
     * Loads presets and settings from persistent storage and updates the component accordingly.
     */
    private void loadPersistedData() {
        DoubleRange maxPitchRollRange = MAX_PITCH_ROLL_RANGE_SETTING.load(mDeviceDict);
        if (maxPitchRollRange != null) {
            mPilotingItf.getMaxPitchRoll().updateBounds(maxPitchRollRange);
        }

        DoubleRange maxPitchRollVelocityRange = MAX_PITCH_ROLL_VELOCITY_RANGE_SETTING.load(mDeviceDict);
        if (maxPitchRollVelocityRange != null) {
            mPilotingItf.getMaxPitchRollVelocity().updateBounds(maxPitchRollVelocityRange);
        }

        DoubleRange maxVerticalSpeedRange = MAX_VERTICAL_SPEED_RANGE_SETTING.load(mDeviceDict);
        if (maxVerticalSpeedRange != null) {
            mPilotingItf.getMaxVerticalSpeed().updateBounds(maxVerticalSpeedRange);
        }

        DoubleRange maxYawRotationSpeedRange = MAX_YAW_ROTATION_SPEED_RANGE_SETTING.load(mDeviceDict);
        if (maxYawRotationSpeedRange != null) {
            mPilotingItf.getMaxYawRotationSpeed().updateBounds(maxYawRotationSpeedRange);
        }

        mPilotingItf.getBankedTurnMode().updateSupportedFlag(
                Boolean.TRUE.equals(BANKED_TURN_MODE_SUPPORT_SETTING.load(mDeviceDict)));

        mPilotingItf.getThrownTakeOffMode().updateSupportedFlag(
                Boolean.TRUE.equals(THROWN_TAKE_OFF_MODE_SUPPORT_SETTING.load(mDeviceDict)));

        applyPresets();
    }

    /**
     * Applies component's persisted presets.
     */
    private void applyPresets() {
        applyMaxPitchRoll(MAX_PITCH_ROLL_PRESET.load(mPresetDict));
        applyMaxPitchRollVelocity(MAX_PITCH_ROLL_VELOCITY_PRESET.load(mPresetDict));
        applyMaxVerticalSpeed(MAX_VERTICAL_SPEED_PRESET.load(mPresetDict));
        applyMaxYawRotationSpeed(MAX_YAW_ROTATION_SPEED_PRESET.load(mPresetDict));
        applyBankedTurnMode(BANKED_TURN_MODE_PRESET.load(mPresetDict));

        // thrown takeoff mode is always available
        THROWN_TAKE_OFF_MODE_SUPPORT_SETTING.save(mDeviceDict, true);
        mPilotingItf.getThrownTakeOffMode().updateSupportedFlag(true);

        applyThrownTakeOffMode(THROWN_TAKEOFF_MODE_PRESET.load(mPresetDict));
    }

    /**
     * Applies max pitch roll.
     * <ul>
     * <li>Finds an appropriate fallback value if the given value is null, or unsupported;</li>
     * <li>Sends the computed value to the drone in case it differs from the last received value;</li>
     * <li>Updates the component's setting accordingly.</li>
     * </ul>
     *
     * @param maxPitchRoll value to apply
     *
     * @return {@code true} if a command was sent to the device and the component's setting should arm its updating
     *         flag
     */
    private boolean applyMaxPitchRoll(@Nullable Double maxPitchRoll) {
        if (maxPitchRoll == null) {
            if (mMaxPitchRoll == null) {
                return false;
            }
            maxPitchRoll = mMaxPitchRoll;
        }

        boolean updating = !maxPitchRoll.equals(mMaxPitchRoll) && sendCommand(
                ArsdkFeatureMinidrone.PilotingSettings.encodeMaxTilt(maxPitchRoll.floatValue()));

        mMaxPitchRoll = maxPitchRoll;
        mPilotingItf.getMaxPitchRoll()
                    .updateValue(maxPitchRoll);

        return updating;
    }

    /**
     * Applies max pitch roll velocity.
     * <ul>
     * <li>Finds an appropriate fallback value if the given value is null, or unsupported;</li>
     * <li>Sends the computed value to the drone in case it differs from the last received value;</li>
     * <li>Updates the component's setting accordingly.</li>
     * </ul>
     *
     * @param maxPitchRollVelocity value to apply
     *
     * @return {@code true} if a command was sent to the device and the component's setting should arm its updating
     *         flag
     */
    private boolean applyMaxPitchRollVelocity(@Nullable Double maxPitchRollVelocity) {
        if (maxPitchRollVelocity == null) {
            if (mMaxPitchRollVelocity == null) {
                return false;
            }
            maxPitchRollVelocity = mMaxPitchRollVelocity;
        }

//        boolean updating = !maxPitchRollVelocity.equals(mMaxPitchRollVelocity) && sendCommand(
//                ArsdkFeatureMinidrone.SpeedSettings.encodeMaxPitchRollRotationSpeed(maxPitchRollVelocity.floatValue()));
//
//        mMaxPitchRollVelocity = maxPitchRollVelocity;
//        mPilotingItf.getMaxPitchRollVelocity()
//                    .updateValue(maxPitchRollVelocity);

        boolean updating = true;
        return updating;
    }

    /**
     * Applies max vertical speed.
     * <ul>
     * <li>Finds an appropriate fallback value if the given value is null, or unsupported;</li>
     * <li>Sends the computed value to the drone in case it differs from the last received value;</li>
     * <li>Updates the component's setting accordingly.</li>
     * </ul>
     *
     * @param maxVerticalSpeed value to apply
     *
     * @return {@code true} if a command was sent to the device and the component's setting should arm its updating
     *         flag
     */
    private boolean applyMaxVerticalSpeed(@Nullable Double maxVerticalSpeed) {
        if (maxVerticalSpeed == null) {
            if (mMaxVerticalSpeed == null) {
                return false;
            }
            maxVerticalSpeed = mMaxVerticalSpeed;
        }

        boolean updating = !maxVerticalSpeed.equals(mMaxVerticalSpeed) && sendCommand(
                ArsdkFeatureMinidrone.SpeedSettings.encodeMaxVerticalSpeed(maxVerticalSpeed.floatValue()));

        mMaxVerticalSpeed = maxVerticalSpeed;
        mPilotingItf.getMaxVerticalSpeed()
                    .updateValue(maxVerticalSpeed);

        return updating;
    }

    /**
     * Applies max yaw rotation speed.
     * <ul>
     * <li>Finds an appropriate fallback value if the given value is null, or unsupported;</li>
     * <li>Sends the computed value to the drone in case it differs from the last received value;</li>
     * <li>Updates the component's setting accordingly.</li>
     * </ul>
     *
     * @param maxYawRotationSpeed value to apply
     *
     * @return {@code true} if a command was sent to the device and the component's setting should arm its updating
     *         flag
     */
    private boolean applyMaxYawRotationSpeed(@Nullable Double maxYawRotationSpeed) {
        if (maxYawRotationSpeed == null) {
            if (mMaxYawRotationSpeed == null) {
                return false;
            }
            maxYawRotationSpeed = mMaxYawRotationSpeed;
        }

        boolean updating = !maxYawRotationSpeed.equals(mMaxYawRotationSpeed) && sendCommand(
                ArsdkFeatureMinidrone.SpeedSettings.encodeMaxRotationSpeed(maxYawRotationSpeed.floatValue()));

        mMaxYawRotationSpeed = maxYawRotationSpeed;
        mPilotingItf.getMaxYawRotationSpeed()
                    .updateValue(maxYawRotationSpeed);

        return updating;
    }

    /**
     * Applies banked turn mode.
     * <ul>
     * <li>Finds an appropriate fallback value if the given value is null, or unsupported;</li>
     * <li>Sends the computed value to the drone in case it differs from the last received value;</li>
     * <li>Updates the component's setting accordingly.</li>
     * </ul>
     *
     * @param bankedTurnMode value to apply
     *
     * @return {@code true} if a command was sent to the device and the component's setting should arm its updating
     *         flag
     */
    private boolean applyBankedTurnMode(@Nullable Boolean bankedTurnMode) {
        if (bankedTurnMode == null) {
            if (mBankedTurnMode == null) {
                return false;
            }
            bankedTurnMode = mBankedTurnMode;
        }

        boolean updating = !bankedTurnMode.equals(mBankedTurnMode) && sendCommand(
                ArsdkFeatureMinidrone.PilotingSettings.encodeBankedTurn(bankedTurnMode ? 1 : 0));

        mBankedTurnMode = bankedTurnMode;
        mPilotingItf.getBankedTurnMode()
                    .updateValue(bankedTurnMode);

        return updating;
    }

    /**
     * Applies thrown take-off mode.
     * <ul>
     * <li>Finds an appropriate fallback value if the given value is null, or unsupported;</li>
     * <li>Sends the computed value to the drone in case it differs from the last received value;</li>
     * <li>Updates the component's setting accordingly.</li>
     * </ul>
     *
     * @param thrownTakeOffMode value to apply
     *
     * @return {@code true} if a command was sent to the device and the component's setting should arm its updating
     *         flag
     */
    private boolean applyThrownTakeOffMode(@Nullable Boolean thrownTakeOffMode) {
        if (thrownTakeOffMode == null) {
            if (mThrownTakeOffMode == null) {
                return false;
            }
            thrownTakeOffMode = mThrownTakeOffMode;
        }

        mThrownTakeOffMode = thrownTakeOffMode;
        mPilotingItf.getThrownTakeOffMode()
                .updateValue(thrownTakeOffMode);

        mPilotingItf.updateSmartWillDoThrownTakeOff(thrownTakeOffMode)
                .notifyUpdated();

        return false;
    }

    /** Callbacks called when a command of the feature ArsdkFeatureMinidrone.PilotingSettingsState is decoded. */
    private final ArsdkFeatureMinidrone.PilotingSettingsState.Callback mPilotingSettingsStateCallback =
            new ArsdkFeatureMinidrone.PilotingSettingsState.Callback() {

                @Override
                public void onMaxTiltChanged(float current, float min, float max) {
                    if (min > max) {
                        throw new ArsdkCommand.RejectedEventException(
                                "Invalid max pitch/roll bounds [min: " + min + ", max: " + max + "]");
                    }

                    DoubleRange bounds = DoubleRange.of(min, max);
                    MAX_PITCH_ROLL_RANGE_SETTING.save(mDeviceDict, bounds);
                    mPilotingItf.getMaxPitchRoll().updateBounds(bounds);

                    mMaxPitchRoll = (double) current;
                    if (isConnected()) {
                        mPilotingItf.getMaxPitchRoll().updateValue(current);
                    }

                    mPilotingItf.notifyUpdated();
                }

                @Override
                public void onBankedTurnChanged(int state) {
                    BANKED_TURN_MODE_SUPPORT_SETTING.save(mDeviceDict, true);
                    mPilotingItf.getBankedTurnMode().updateSupportedFlag(true);

                    mBankedTurnMode = state == 1;
                    if (isConnected()) {
                        mPilotingItf.getBankedTurnMode().updateValue(mBankedTurnMode);
                    }

                    mPilotingItf.notifyUpdated();
                }

            };

    /** Callbacks called when a command of the feature ArsdkFeatureMinidrone.PilotingState is decoded. */
    private final ArsdkFeatureMinidrone.PilotingState.Callback mPilotingStateCallback =
            new ArsdkFeatureMinidrone.PilotingState.Callback() {

                @Override
                public void onFlyingStateChanged(@Nullable ArsdkFeatureMinidrone.PilotingstateFlyingstatechangedState state) {
                    if (state != null) {
                        switch (state) {
                            case LANDED:
                            case LANDING:
                                mPilotingItf.updateCanLand(false).updateCanTakeOff(true)
                                        .updateSmartWillDoThrownTakeOff(mThrownTakeOffMode).notifyUpdated();
                                break;
                            case FLYING:
                            case HOVERING:
                            case TAKINGOFF:
                                mPilotingItf.updateCanLand(true).updateCanTakeOff(false)
                                        .updateSmartWillDoThrownTakeOff(false).notifyUpdated();
                                break;
                            case EMERGENCY:
                                mPilotingItf.updateCanLand(false).updateCanTakeOff(false).notifyUpdated();
                                break;
                            case ROLLING:
                                break;
                            case INIT:
                                break;
                        }
                    }
                }

                @Override
                public void onAutoTakeOffModeChanged(int state) {
                    autoTakeOffMode = state;
                    if (state == 1) {
                        mPilotingItf.updateCanLand(true).updateCanTakeOff(false)
                                .updateSmartWillDoThrownTakeOff(false).notifyUpdated();
                    } else {
                        mPilotingItf.updateCanLand(false).updateCanTakeOff(true)
                                .updateSmartWillDoThrownTakeOff(mThrownTakeOffMode).notifyUpdated();

                    }
                }
            };

    /** Callbacks called when a command of the feature ArsdkFeatureMinidrone.SpeedSettingsState is decoded. */
    private final ArsdkFeatureMinidrone.SpeedSettingsState.Callback mSpeedSettingsStateCallback =
            new ArsdkFeatureMinidrone.SpeedSettingsState.Callback() {

                @Override
                public void onMaxVerticalSpeedChanged(float current, float min, float max) {
                    if (min > max) {
                        throw new ArsdkCommand.RejectedEventException(
                                "Invalid max vertical speed bounds [min: " + min + ", max: " + max + "]");
                    }

                    DoubleRange bounds = DoubleRange.of(min, max);
                    MAX_VERTICAL_SPEED_RANGE_SETTING.save(mDeviceDict, bounds);
                    mPilotingItf.getMaxVerticalSpeed().updateBounds(bounds);

                    mMaxVerticalSpeed = (double) current;
                    if (isConnected()) {
                        mPilotingItf.getMaxVerticalSpeed().updateValue(mMaxVerticalSpeed);
                    }

                    mPilotingItf.notifyUpdated();
                }

                @Override
                public void onMaxRotationSpeedChanged(float current, float min, float max) {
                    if (min > max) {
                        throw new ArsdkCommand.RejectedEventException(
                                "Invalid max yaw rotation speed bounds [min: " + min + ", max: " + max + "]");
                    }

                    DoubleRange bounds = DoubleRange.of(min, max);
                    MAX_YAW_ROTATION_SPEED_RANGE_SETTING.save(mDeviceDict, bounds);

                    mPilotingItf.getMaxYawRotationSpeed().updateBounds(bounds);

                    mMaxYawRotationSpeed = (double) current;
                    if (isConnected()) {
                        mPilotingItf.getMaxYawRotationSpeed().updateValue(mMaxYawRotationSpeed);
                    }

                    mPilotingItf.notifyUpdated();
                }
            };

    /** Backend of ManualCopterPilotingItfCore implementation. */
    private final class Backend extends ActivablePilotingItfController.Backend
            implements ManualFlightPilotingItfCore.Backend {

        @Override
        public void takeOff() {
            sendCommand(ArsdkFeatureMinidrone.Piloting.encodeTakeOff());
        }

        @Override
        public void thrownTakeOff() {
            sendCommand(ArsdkFeatureMinidrone.Piloting.encodeAutoTakeOffMode(1));
        }

        @Override
        public void land() {
            if (autoTakeOffMode == 1) {
                sendCommand(ArsdkFeatureMinidrone.Piloting.encodeAutoTakeOffMode(0));
            } else {
                sendCommand(ArsdkFeatureMinidrone.Piloting.encodeLanding());
                BlackBoxDroneSession blackBoxSession = mDeviceController.getBlackBoxSession();
                if (blackBoxSession != null) {
                    blackBoxSession.onLandCommandSent();
                }
            }
        }

        @Override
        public void emergencyCutOut() {
            sendCommand(ArsdkFeatureMinidrone.Piloting.encodeEmergency());
        }

        @Override
        public boolean setMaxPitchRoll(double maxPitchRoll) {
            boolean updating = applyMaxPitchRoll(maxPitchRoll);
            MAX_PITCH_ROLL_PRESET.save(mPresetDict, maxPitchRoll);
            if (!updating) {
                mPilotingItf.notifyUpdated();
            }
            return updating;
        }

        @Override
        public boolean setMaxPitchRollVelocity(double maxPitchRollVelocity) {
            boolean updating = applyMaxPitchRollVelocity(maxPitchRollVelocity);
            MAX_PITCH_ROLL_VELOCITY_PRESET.save(mPresetDict, maxPitchRollVelocity);
            if (!updating) {
                mPilotingItf.notifyUpdated();
            }
            return updating;
        }

        @Override
        public boolean setMaxVerticalSpeed(double maxVerticalSpeed) {
            boolean updating = applyMaxVerticalSpeed(maxVerticalSpeed);
            MAX_VERTICAL_SPEED_PRESET.save(mPresetDict, maxVerticalSpeed);
            if (!updating) {
                mPilotingItf.notifyUpdated();
            }
            return updating;
        }

        @Override
        public boolean setMaxYawRotationSpeed(double maxYawRotationSpeed) {
            boolean updating = applyMaxYawRotationSpeed(maxYawRotationSpeed);
            MAX_YAW_ROTATION_SPEED_PRESET.save(mPresetDict, maxYawRotationSpeed);
            if (!updating) {
                mPilotingItf.notifyUpdated();
            }
            return updating;
        }

        @Override
        public boolean setBankedTurnMode(boolean enable) {
            boolean updating = applyBankedTurnMode(enable);
            BANKED_TURN_MODE_PRESET.save(mPresetDict, enable);
            if (!updating) {
                mPilotingItf.notifyUpdated();
            }
            return updating;
        }

        @Override
        public boolean useThrownTakeOffForSmartTakeOff(boolean enable) {
            applyThrownTakeOffMode(enable);
            THROWN_TAKEOFF_MODE_PRESET.save(mPresetDict, enable);

            mPilotingItf.notifyUpdated();

            return false;
        }

        @Override
        public void setPitch(int pitch) {
            MiniatureManualPilotingItf.this.setPitch(pitch);
        }

        @Override
        public void setRoll(int roll) {
            MiniatureManualPilotingItf.this.setRoll(roll);
        }

        @Override
        public void setFlag(boolean flag) {
            MiniatureManualPilotingItf.this.setFlag(flag);
        }

        @Override
        public void setWithHoverLock(boolean withHoverLock) {
            MiniatureManualPilotingItf.this.setWithHoverLock(withHoverLock);
        }

        @Override
        public void setYawRotationSpeed(int yawRotationSpeed) {
            setYaw(yawRotationSpeed);
        }

        @Override
        public void setVerticalSpeed(int verticalSpeed) {
            setGaz(verticalSpeed);
        }

        @Override
        public void hover() {
            MiniatureManualPilotingItf.this.setPitch(0);
            MiniatureManualPilotingItf.this.setRoll(0);
        }
    }
}
