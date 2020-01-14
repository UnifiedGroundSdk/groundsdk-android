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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.gimbal;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.device.peripheral.Gimbal;
import com.parrot.drone.groundsdk.device.peripheral.Gimbal.Axis;
import com.parrot.drone.groundsdk.internal.device.peripheral.GimbalCore;
import com.parrot.drone.groundsdk.value.DoubleRange;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureGimbal;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.EnumMap;
import java.util.EnumSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Gimbal peripheral controller for Anafi family drones. */
public final class BebopGimbal extends DronePeripheralController {

    /** Key used to access preset and range dictionaries for this peripheral settings. */
    private static final String SETTINGS_KEY = "gimbal";

    // preset store bindings

    /** Stabilized axes preset entry. */
    private static final StorageEntry<EnumSet<Axis>> STABILIZED_AXES_PRESET =
            StorageEntry.ofEnumSet("stabilizedAxes", Axis.class);

    /** Maximum speed preset entry. */
    private static final StorageEntry<EnumMap<Axis, Double>> MAX_SPEEDS_PRESET =
            StorageEntry.ofEnumToDoubleMap("maxSpeeds", Axis.class);

    // device specific store bindings

    /** Supported axes device setting. */
    private static final StorageEntry<EnumSet<Axis>> SUPPORTED_AXES_SETTING =
            StorageEntry.ofEnumSet("supportedAxes", Axis.class);

    /** Maximum speed setting range. */
    private static final StorageEntry<EnumMap<Axis, DoubleRange>> MAX_SPEEDS_RANGE_SETTING =
            StorageEntry.ofEnumToDoubleRangeMap("maxSpeedsRange", Axis.class);


    /** Gimbal peripheral for which this object is the backend. */
    @NonNull
    private final GimbalCore mGimbal;

    /** Dictionary containing device specific values for this peripheral, such as settings ranges, supported status. */
    @Nullable
    private final PersistentStore.Dictionary mDeviceDict;

    /** Dictionary containing current preset values for this peripheral. */
    @Nullable
    private PersistentStore.Dictionary mPresetDict;

    /** Maximum speed setting value by axis. */
    @NonNull
    private EnumMap<Axis, Double> mMaxSpeeds;

    /**
     * Stabilization state by axis.
     * <p>
     * This field is {@code null} until stabilization is loaded or received from drone. We need to know whether it has
     * been initialized because stabilization command is non-acknowledged.
     */
    @NonNull
    private final EnumSet<Axis> mStabilizedAxes;

    /** Absolute attitude by axis. */
    @NonNull
    private final EnumMap<Axis, Double> mAbsoluteAttitude;

    /** Relative attitude by axis. */
    @NonNull
    private final EnumMap<Axis, Double> mRelativeAttitude;

    /** Absolute attitude bounds by axis. */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @NonNull
    private final EnumMap<Axis, DoubleRange> mAbsoluteAttitudeBounds;

    /** Relative attitude bounds by axis. */
    @NonNull
    private final EnumMap<Axis, DoubleRange> mRelativeAttitudeBounds;

    @NonNull
    private final EnumMap<Axis, DoubleRange> mMaxSpeedRanges;

    private final DoubleRange velocityRange = DoubleRange.of(-1, 1);

    private DoubleRange pitchRange;
    private DoubleRange yawRange;

    private float centerTilt;
    private float centerPan;

    /** Whether attitude has been received from drone at least once. */
    private boolean mAttitudeReceived;
    private boolean mStabilizeReceived;
    private boolean mBoundsReceived;
    private boolean mMaxSpeedReceived;
    private boolean mAllReceived;

    private boolean mCenterReceived;
    private boolean mCenterSent;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public BebopGimbal(@NonNull DroneController droneController) {
        super(droneController);
        mGimbal = new GimbalCore(mComponentStore, mBackend);

        mDeviceDict = offlineSettingsEnabled() ? mDeviceController.getDeviceDict().getDictionary(SETTINGS_KEY) : null;
        mPresetDict = offlineSettingsEnabled() ? mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY) : null;

        mMaxSpeeds = new EnumMap<>(Axis.class);
        mAbsoluteAttitude = new EnumMap<>(Axis.class);
        mRelativeAttitude = new EnumMap<>(Axis.class);
        mAbsoluteAttitudeBounds = new EnumMap<>(Axis.class);
        mRelativeAttitudeBounds = new EnumMap<>(Axis.class);
        mMaxSpeedRanges = new EnumMap<>(Axis.class);

        mStabilizedAxes = EnumSet.noneOf(Axis.class);

        loadPersistedData();
        if (isPersisted()) {
            mGimbal.publish();
        }
    }

    @Override
    protected void onConnected() {
        applyPresets();

//        DeviceController.Backend backend = mDeviceController.getProtocolBackend();
//        if (backend != null) {
//            backend.registerNoAckCommandEncoders(mGimbalControlEncoder);
//        }

        mGimbal.publish();
    }

    @Override
    protected void onDisconnected() {
        // reset online-only settings
        mGimbal.cancelSettingsRollbacks()
               .updateLockedAxes(EnumSet.allOf(Axis.class))
               .updateCorrectableAxes(EnumSet.noneOf(Axis.class))
               .updateErrors(EnumSet.noneOf(Gimbal.Error.class));

        for (Axis axis : EnumSet.allOf(Axis.class)) {
            mGimbal.updateAttitudeBounds(axis, null)
                   .updateAbsoluteAttitude(axis, 0)
                   .updateRelativeAttitude(axis, 0);
        }

        mAbsoluteAttitude.clear();
        mRelativeAttitude.clear();
        mRelativeAttitudeBounds.clear();
        mMaxSpeedRanges.clear();

        mAttitudeReceived = false;
        mStabilizeReceived = false;
        mMaxSpeedReceived = false;
        mBoundsReceived = false;
        mAllReceived = false;
        mCenterReceived = false;

//        DeviceController.Backend backend = mDeviceController.getProtocolBackend();
//        if (backend != null) {
//            backend.unregisterNoAckCommandEncoders(mGimbalControlEncoder);
//        }
//        mGimbalControlEncoder.reset();

        mGimbal.updateOffsetCorrectionProcessState(false);

        if (isPersisted()) {
            mGimbal.notifyUpdated();
        } else {
            mGimbal.unpublish();
        }
    }

    @Override
    protected void onPresetChange() {
        // reload preset store
        mPresetDict = mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY);
        if (isConnected()) {
            applyPresets();
        }
        mGimbal.notifyUpdated();
    }

    @Override
    protected void onForgetting() {
        if (mDeviceDict != null) {
            mDeviceDict.clear().commit();
        }
        mGimbal.unpublish();
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
        EnumSet<Axis> supportedAxes = SUPPORTED_AXES_SETTING.load(mDeviceDict);
        if (supportedAxes != null) {
            mGimbal.updateSupportedAxes(supportedAxes);
        }

        EnumMap<Axis, DoubleRange> maxSpeedRanges = MAX_SPEEDS_RANGE_SETTING.load(mDeviceDict);
        if (maxSpeedRanges != null) {
            mGimbal.updateMaxSpeedRanges(maxSpeedRanges);
        }

        EnumMap<Axis, Double> maxSpeeds = MAX_SPEEDS_PRESET.load(mPresetDict);
        if (maxSpeeds != null) {
            mMaxSpeeds = maxSpeeds;
            mGimbal.updateMaxSpeeds(maxSpeeds);
        }

        EnumSet<Axis> stabilizedAxes = STABILIZED_AXES_PRESET.load(mPresetDict);
        if (stabilizedAxes != null && supportedAxes != null) {
            mStabilizedAxes.addAll(stabilizedAxes);
            mGimbal.updateStabilization(stabilizedAxes);
        }
    }

    /**
     * Applies component's persisted presets.
     */
    private void applyPresets() {
        applyMaxSpeeds(MAX_SPEEDS_PRESET.load(mPresetDict), false);
        if (mAttitudeReceived) {
            applyStabilizationPreset();
        }
    }

    /**
     * Applies persisted gimbal presets.
     * <p>
     * This happens when both drone is connected and attitude has been received.
     */
    private void applyStabilizationPreset() {
//        EnumSet<Axis> stabilizedAxes = STABILIZED_AXES_PRESET.load(mPresetDict);
//        for (Axis axis : mGimbal.getSupportedAxes()) {
//            applyStabilization(axis, stabilizedAxes != null ? stabilizedAxes.contains(axis) : null);
//        }
    }

    /**
     * Applies maximum speed to all axes.
     * <ul>
     * <li>Gets the last received values if the given map is null;</li>
     * <li>Sends the obtained values to the drone in case they differ from the last received values or sending is
     * forced;</li>
     * <li>Saves the obtained values to the gimbal presets and updates the peripheral's settings accordingly.</li>
     * </ul>
     *
     * @param maxSpeeds           maximum speeds to apply
     * @param forceSendingCommand {@code true} to force sending values to the drone
     *
     * @return {@code true} if a command was sent to the device and the peripheral's setting should arm its updating
     *         flag
     */
    private boolean applyMaxSpeeds(@Nullable EnumMap<Axis, Double> maxSpeeds, boolean forceSendingCommand) {
        // Validating given value
        if (maxSpeeds == null) {
            maxSpeeds = mMaxSpeeds;
        }

        boolean updating = false;
        if (forceSendingCommand || !maxSpeeds.equals(mMaxSpeeds)) {
            Double yaw = maxSpeeds.get(Axis.YAW);
            Double pitch = maxSpeeds.get(Axis.PITCH);
            Double roll = maxSpeeds.get(Axis.ROLL);
            updating = sendCommand(ArsdkFeatureGimbal.encodeSetMaxSpeed(0,
                    yaw != null ? (float) yaw.doubleValue() : 0f,
                    pitch != null ? (float) pitch.doubleValue() : 0f,
                    roll != null ? (float) roll.doubleValue() : 0f));
        }

        mMaxSpeeds = maxSpeeds;

        mGimbal.updateMaxSpeeds(maxSpeeds);
        return updating;
    }

    /**
     * Applies maximum speed to the given axis.
     * <ul>
     * <li>Sends the given value to the drone in case it differs from the last received value;</li>
     * <li>Saves it to the gimbal presets and updates the peripheral's setting accordingly.</li>
     * </ul>
     *
     * @param axis     the axis to which the new max speed will apply
     * @param maxSpeed maximum speed to apply
     *
     * @return {@code true} if a command was sent to the device and the peripheral's setting should arm its updating
     *         flag
     */
    private boolean applyMaxSpeed(@NonNull Axis axis, @NonNull Double maxSpeed) {
        if (!maxSpeed.equals(mMaxSpeeds.get(axis))) {
            mMaxSpeeds.put(axis, maxSpeed);
            return applyMaxSpeeds(null, true);
        }
        return false;
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        final int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureArdrone3.CameraState.UID) {
            ArsdkFeatureArdrone3.CameraState.decode(command, mCameraStateCallbacks);
        } else if (featureId == ArsdkFeatureCommon.CameraSettingsState.UID) {
            ArsdkFeatureCommon.CameraSettingsState.decode(command, mCameraSettingsCallback);
        } else if (featureId == ArsdkFeatureArdrone3.PictureSettingsState.UID) {
            ArsdkFeatureArdrone3.PictureSettingsState.decode(command, mPictureSettingsCallback);
        }
    }

    @NonNull
    private final ArsdkFeatureArdrone3.CameraState.Callback mCameraStateCallbacks = new ArsdkFeatureArdrone3.CameraState.Callback() {

        float lastPitch = Integer.MIN_VALUE;
        float lastYaw = Integer.MIN_VALUE;

        @Override
        public void onOrientation(int tilt, int pan) {
            onOrientationV2(tilt, pan);
        }

        @Override
        public void onOrientationV2(float tilt, float pan) {
            if (!mAttitudeReceived) {
                mRelativeAttitude.put(Axis.YAW, (double) roundToSecondDecimal(pan));
                mRelativeAttitude.put(Axis.PITCH, (double) roundToSecondDecimal(tilt));

                mAbsoluteAttitude.put(Axis.YAW, (double) roundToSecondDecimal(pan));
                mAbsoluteAttitude.put(Axis.PITCH, (double) roundToSecondDecimal(tilt));

                mAttitudeReceived = true;
                notifyIfAllReceived();
            }

            if (mAllReceived) {
                // Store internally the current attitude on each frame of reference
                mRelativeAttitude.put(Axis.YAW, (double) roundToSecondDecimal(pan));
                mRelativeAttitude.put(Axis.PITCH, (double) roundToSecondDecimal(tilt));

                mAbsoluteAttitude.put(Axis.YAW, (double) roundToSecondDecimal(pan));
                mAbsoluteAttitude.put(Axis.PITCH, (double) roundToSecondDecimal(tilt));

                mGimbal.updateRelativeAttitude(Axis.YAW, roundToSecondDecimal(pan));
                mGimbal.updateRelativeAttitude(Axis.PITCH, roundToSecondDecimal(tilt));
                mGimbal.updateRelativeAttitude(Axis.ROLL, 0);

                mGimbal.updateAbsoluteAttitude(Axis.YAW, roundToSecondDecimal(pan));
                mGimbal.updateAbsoluteAttitude(Axis.PITCH, roundToSecondDecimal(tilt));
                mGimbal.updateAbsoluteAttitude(Axis.ROLL, 0);

                if (tilt != lastPitch || pan != lastYaw) {
                    lastPitch = tilt;
                    lastYaw = pan;
                    mGimbal.notifyUpdated();
                }
            }
        }

        @Override
        public void onVelocityRange(float maxTilt, float maxPan) {
            mMaxSpeedRanges.clear();
            mMaxSpeedRanges.put(Axis.PITCH, DoubleRange.of(maxTilt, maxTilt));
            mMaxSpeedRanges.put(Axis.YAW, DoubleRange.of(maxPan, maxPan));
            mMaxSpeedRanges.put(Axis.ROLL, DoubleRange.of(0, 0));

            mMaxSpeeds.clear();
            mMaxSpeeds.put(Axis.PITCH, (double) maxTilt);
            mMaxSpeeds.put(Axis.YAW, (double) maxPan);
            mMaxSpeeds.put(Axis.ROLL, 0d);

            pitchRange = DoubleRange.of(maxTilt * -1, maxTilt);
            yawRange = DoubleRange.of(maxPan * -1, maxPan);

            MAX_SPEEDS_RANGE_SETTING.save(mDeviceDict, mMaxSpeedRanges);

            mMaxSpeedReceived = true;
            notifyIfAllReceived();
        }

        @Override
        public void onDefaultCameraOrientationV2(float tilt, float pan) {
            centerTilt = tilt;
            centerPan = pan;

            mCenterReceived = true;
            notifyIfAllReceived();
        }
    };

    private final ArsdkFeatureCommon.CameraSettingsState.Callback mCameraSettingsCallback = new ArsdkFeatureCommon.CameraSettingsState.Callback() {
        @Override
        public void onCameraSettingsChanged(float fov, float panmax, float panmin, float tiltmax, float tiltmin) {
            // store the values as they may be used later (when axis stabilization changes)
            mRelativeAttitudeBounds.clear();
            mRelativeAttitudeBounds.put(Axis.YAW, DoubleRange.of(panmin, panmax));
            mRelativeAttitudeBounds.put(Axis.PITCH, DoubleRange.of(tiltmin, tiltmax));
            mRelativeAttitudeBounds.put(Axis.ROLL, DoubleRange.of(0d, 0d));

            // store the values as they may be used later (when axis stabilization changes)
            mAbsoluteAttitudeBounds.clear();
            mAbsoluteAttitudeBounds.put(Axis.YAW, DoubleRange.of(panmin, panmax));
            mAbsoluteAttitudeBounds.put(Axis.PITCH, DoubleRange.of(tiltmin, tiltmax));
            mAbsoluteAttitudeBounds.put(Axis.ROLL, DoubleRange.of(0d, 0d));

            mBoundsReceived = true;
            notifyIfAllReceived();
        }
    };

    private final ArsdkFeatureArdrone3.PictureSettingsState.Callback mPictureSettingsCallback = new ArsdkFeatureArdrone3.PictureSettingsState.Callback() {
        @Override
        public void onVideoStabilizationModeChanged(@Nullable ArsdkFeatureArdrone3.PicturesettingsstateVideostabilizationmodechangedMode mode) {
            if (mode == null) return;

            mStabilizedAxes.clear();

            switch (mode) {
                case ROLL_PITCH:
                    mStabilizedAxes.add(Axis.PITCH);
                    mStabilizedAxes.add(Axis.ROLL);
                    break;
                case PITCH:
                    mStabilizedAxes.add(Axis.PITCH);
                    break;
                case ROLL:
                    mStabilizedAxes.add(Axis.ROLL);
                    break;
                case NONE:
                    break;
            }

            mStabilizeReceived = true;
            notifyIfAllReceived();
        }
    };

    @SuppressWarnings("ConstantConditions")
    private void notifyIfAllReceived() {
        if (mAttitudeReceived && mStabilizeReceived && mBoundsReceived && mMaxSpeedReceived && mCenterReceived) {
            final EnumSet<Axis> supportedAxes = EnumSet.allOf(Gimbal.Axis.class);

            SUPPORTED_AXES_SETTING.save(mDeviceDict, supportedAxes);
            mGimbal.updateSupportedAxes(supportedAxes);

            mGimbal.updateMaxSpeedRanges(mMaxSpeedRanges);
            mGimbal.updateMaxSpeeds(mMaxSpeeds);

            mGimbal.updateAttitudeBounds(Axis.YAW, mRelativeAttitudeBounds.get(Axis.YAW));
            mGimbal.updateAttitudeBounds(Axis.PITCH, mRelativeAttitudeBounds.get(Axis.PITCH));
            mGimbal.updateAttitudeBounds(Axis.ROLL, mRelativeAttitudeBounds.get(Axis.ROLL));

            mGimbal.updateStabilization(mStabilizedAxes);
            mGimbal.updateIsCalibrated(true);
            mGimbal.updateLockedAxes(EnumSet.of(Axis.ROLL));

            mGimbal.updateRelativeAttitude(Axis.YAW, mRelativeAttitude.get(Axis.YAW));
            mGimbal.updateRelativeAttitude(Axis.PITCH, mRelativeAttitude.get(Axis.PITCH));
            mGimbal.updateRelativeAttitude(Axis.ROLL, 0);

            mGimbal.updateAbsoluteAttitude(Axis.YAW, mAbsoluteAttitude.get(Axis.YAW));
            mGimbal.updateAbsoluteAttitude(Axis.PITCH, mAbsoluteAttitude.get(Axis.PITCH));
            mGimbal.updateAbsoluteAttitude(Axis.ROLL, 0);

            if (!mCenterSent) {
                sendCommand(ArsdkFeatureArdrone3.Camera.encodeOrientationV2(centerTilt, centerPan));
                mCenterSent = true;
            }

            mGimbal.notifyUpdated();
            mAllReceived = true;
        }
    }

    /**
     * Rounds the given value to the second decimal.
     *
     * @param value the value to round
     *
     * @return the rounded value
     */
    private static float roundToSecondDecimal(double value) {
        return Math.round(value * 100f) / 100f;
    }

    /** Backend of GimbalCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final GimbalCore.Backend mBackend = new GimbalCore.Backend() {

        @Override
        public boolean setMaxSpeed(@NonNull Axis axis, double speed) {
            boolean updating = applyMaxSpeed(axis, speed);
            MAX_SPEEDS_PRESET.save(mPresetDict, mMaxSpeeds);
            if (!updating) {
                mGimbal.notifyUpdated();
            }
            return updating;
        }

        @Override
        public boolean setStabilization(@NonNull Axis axis, boolean stabilized) {
//            applyStabilization(axis, stabilized);
            STABILIZED_AXES_PRESET.save(mPresetDict, mStabilizedAxes);

            if (stabilized) {
                for (Axis a : mStabilizedAxes) {
                    if (!a.equals(axis)) {
                        sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeVideoStabilizationMode(ArsdkFeatureArdrone3.PicturesettingsVideostabilizationmodeMode.ROLL_PITCH));
                        return true;
                    }
                }
                return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeVideoStabilizationMode(axis == Axis.PITCH ?
                        ArsdkFeatureArdrone3.PicturesettingsVideostabilizationmodeMode.PITCH :
                        ArsdkFeatureArdrone3.PicturesettingsVideostabilizationmodeMode.ROLL));
            } else {
                for (Axis a : mStabilizedAxes) {
                    if (!a.equals(axis)) {
                        sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeVideoStabilizationMode(a == Axis.PITCH ?
                                ArsdkFeatureArdrone3.PicturesettingsVideostabilizationmodeMode.PITCH :
                                ArsdkFeatureArdrone3.PicturesettingsVideostabilizationmodeMode.ROLL));
                        return true;
                    }
                }
                return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeVideoStabilizationMode(ArsdkFeatureArdrone3.PicturesettingsVideostabilizationmodeMode.NONE));
            }
        }

        @Override
        public boolean setOffset(@NonNull Axis axis, double offset) {
            return false;
        }

        @Override
        public void control(@NonNull Gimbal.ControlMode mode, @Nullable Double yaw, @Nullable Double pitch, @Nullable Double roll) {
            if (mAttitudeReceived) {
                final float pan = yaw != null ? yaw.floatValue() : 0;
                final float tilt = pitch != null ? pitch.floatValue() : 0;

                if (mode == Gimbal.ControlMode.POSITION) {
                    sendCommand(ArsdkFeatureArdrone3.Camera.encodeOrientationV2(tilt, pan));
                } else {
                    final float velocityTilt = (float) pitchRange.scaleFrom(tilt, velocityRange);
                    final float velocityPan = (float) yawRange.scaleFrom(pan, velocityRange);

                    sendCommand(ArsdkFeatureArdrone3.Camera.encodeVelocity(velocityTilt, velocityPan));
                }
            }
        }

        @Override
        public boolean startOffsetCorrectionProcess() {
            return false;
        }

        @Override
        public boolean stopOffsetCorrectionProcess() {
            return false;
        }

        @Override
        public boolean startCalibration() {
            return false;
        }

        @Override
        public boolean cancelCalibration() {
            return false;
        }
    };
}
