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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.camera;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.device.peripheral.camera.Camera;
import com.parrot.drone.groundsdk.device.peripheral.camera.CameraEvCompensation;
import com.parrot.drone.groundsdk.device.peripheral.camera.CameraExposure;
import com.parrot.drone.groundsdk.device.peripheral.camera.CameraExposureLock;
import com.parrot.drone.groundsdk.device.peripheral.camera.CameraPhoto;
import com.parrot.drone.groundsdk.device.peripheral.camera.CameraRecording;
import com.parrot.drone.groundsdk.device.peripheral.camera.CameraStyle;
import com.parrot.drone.groundsdk.device.peripheral.camera.CameraWhiteBalance;
import com.parrot.drone.groundsdk.device.peripheral.camera.CameraZoom;
import com.parrot.drone.groundsdk.internal.device.peripheral.camera.CameraCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.camera.CameraPhotoSettingCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.camera.CameraRecordingSettingCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.camera.MainCameraCore;
import com.parrot.drone.groundsdk.value.DoubleRange;
import com.parrot.drone.groundsdk.value.IntegerRange;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Camera peripheral controller for Bebop/Disco family drones.
 *
 * <p><b>Timelapse protocol notes (ardrone3):</b>
 * Timelapse mode is configured via {@code PictureSettings.TimelapseSelection(enabled, interval)},
 * but capture is started and stopped via {@code MediaRecord.VideoV2(START/STOP)} — the same
 * command used for normal video recording. {@code MediaRecord.PictureV2} is only for single-shot
 * photos. Stopping a timelapse therefore sends {@code VideoV2(STOP)}.
 *
 * <p><b>Known firmware-level defects (Bebop/Disco):</b>
 * Timelapse support has known firmware-level defects on Bebop and Disco hardware as documented in
 * the community fork README. This controller now speaks the protocol correctly; bench validation
 * of actual capture behaviour on physical hardware is pending.
 */
public final class BebopCameraController extends DronePeripheralController {

    /** Key used to access preset and device dictionaries for this peripheral's settings. */
    private static final String SETTINGS_KEY = "bebopCamera";

    // preset store entries

    /** Camera mode preset entry (RECORDING or PHOTO). */
    private static final StorageEntry<Camera.Mode> MODE_PRESET =
            StorageEntry.ofEnum("mode", Camera.Mode.class);

    /** Recording resolution preset entry. */
    private static final StorageEntry<CameraRecording.Resolution> RECORDING_RESOLUTION_PRESET =
            StorageEntry.ofEnum("recordingResolution", CameraRecording.Resolution.class);

    /** Recording framerate preset entry. */
    private static final StorageEntry<CameraRecording.Framerate> RECORDING_FRAMERATE_PRESET =
            StorageEntry.ofEnum("recordingFramerate", CameraRecording.Framerate.class);

    /** Photo mode preset entry (SINGLE or TIME_LAPSE). */
    private static final StorageEntry<CameraPhoto.Mode> PHOTO_MODE_PRESET =
            StorageEntry.ofEnum("photoMode", CameraPhoto.Mode.class);

    /** EV compensation preset entry. */
    private static final StorageEntry<CameraEvCompensation> EV_COMPENSATION_PRESET =
            StorageEntry.ofEnum("evCompensation", CameraEvCompensation.class);

    // device-specific store entries (firmware-reported ranges)

    /**
     * Firmware EV range device entry (EnumSet of supported CameraEvCompensation values).
     * <p>Populated from {@code PictureSettingsState.ExpositionChanged(value, min, max)}.
     * The ardrone3 protocol delivers min/max as floats and does not provide a discrete list,
     * so we enumerate all CameraEvCompensation values whose float equivalent falls within
     * [min, max] (0.33-step increments).
     */
    private static final StorageEntry<EnumSet<CameraEvCompensation>> EV_RANGE_SETTING =
            StorageEntry.ofEnumSet("evRange", CameraEvCompensation.class);

    private MainCameraCore mCameraCore;

    /** Dictionary containing device-specific values (firmware-reported ranges). */
    @Nullable
    private final PersistentStore.Dictionary mDeviceDict;

    /** Dictionary containing current preset values. */
    @Nullable
    private PersistentStore.Dictionary mPresetDict;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public BebopCameraController(@NonNull DroneController droneController) {
        super(droneController);
        mCameraCore = new MainCameraCore(mComponentStore, mBackend);
        mDeviceDict = offlineSettingsEnabled()
                ? mDeviceController.getDeviceDict().getDictionary(SETTINGS_KEY) : null;
        mPresetDict = offlineSettingsEnabled()
                ? mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY) : null;
    }

    @Override
    protected void onConnecting() {
        mCameraCore.mode().updateAvailableValues(EnumSet.allOf(Camera.Mode.class));

        mCameraCore.photoState().updateState(CameraPhoto.State.FunctionState.STOPPED);
        mCameraCore.recordingState().updateState(CameraRecording.State.FunctionState.STOPPED);

        mCameraCore.mode().updateValue(Camera.Mode.RECORDING);

        mCameraCore.updateActiveFlag(true);
        mCameraCore.updateHdrActive(false);

        mCameraCore.autoRecord().updateSupportedFlag(true);
        mCameraCore.autoHdr().updateSupportedFlag(false);

        mCameraCore.style().updateSupportedStyles(EnumSet.noneOf(CameraStyle.Style.class));
        mCameraCore.style().updateSupportedStyles(EnumSet.of(CameraStyle.Style.STANDARD));
        mCameraCore.style().updateStyle(CameraStyle.Style.STANDARD);

        mCameraCore.style().saturation().updateBounds(IntegerRange.of(0,0));
        mCameraCore.style().contrast().updateBounds(IntegerRange.of(0,0));
        mCameraCore.style().sharpness().updateBounds(IntegerRange.of(0,0));

        mCameraCore.createAlignmentIfNeeded().updateSupportedPitchRange(DoubleRange.of(0, 0));
        mCameraCore.createAlignmentIfNeeded().updateSupportedRollRange(mCameraCore.createAlignmentIfNeeded().supportedPitchRange());
        mCameraCore.createAlignmentIfNeeded().updateSupportedYawRange(mCameraCore.createAlignmentIfNeeded().supportedPitchRange());

        mCameraCore.createZoomIfNeeded().updateAvailability(false);
        mCameraCore.createWhiteBalanceLockIfNeeded().updateLockable(false);

        final EnumSet<CameraWhiteBalance.Mode> whiteBalanceModes = EnumSet.noneOf(CameraWhiteBalance.Mode.class);

        whiteBalanceModes.add(CameraWhiteBalance.Mode.AUTOMATIC);
        whiteBalanceModes.add(CameraWhiteBalance.Mode.INCANDESCENT);
        whiteBalanceModes.add(CameraWhiteBalance.Mode.DAYLIGHT);
        whiteBalanceModes.add(CameraWhiteBalance.Mode.CLOUDY);
        whiteBalanceModes.add(CameraWhiteBalance.Mode.COOL_WHITE_FLUORESCENT);

        mCameraCore.whiteBalance().updateSupportedModes(whiteBalanceModes);
        mCameraCore.whiteBalance().updateSupportedTemperatures(EnumSet.noneOf(CameraWhiteBalance.Temperature.class));

        // EV capabilities: use firmware-reported range when available; fall back to full enum.
        // ardrone3 PictureSettingsState.ExpositionChanged delivers min/max on connect, so the
        // persisted range (if any) reflects what this firmware actually supports.
        EnumSet<CameraEvCompensation> evRange = EV_RANGE_SETTING.load(mDeviceDict);
        if (evRange == null || evRange.isEmpty()) {
            evRange = EnumSet.allOf(CameraEvCompensation.class);
        }
        mCameraCore.exposureCompensation().updateAvailableValues(evRange);

        // Recording capabilities: ardrone3 VideoResolutionsChanged and VideoFramerateChanged
        // report the current value only, not a supported range. Keep a static capability list
        // matching what Bebop/Disco hardware supports.
        final CameraRecordingSettingCore recording = mCameraCore.recording();
        final EnumSet<CameraRecording.Resolution> resolutions = EnumSet.noneOf(CameraRecording.Resolution.class);

        resolutions.add(CameraRecording.Resolution.RES_720P);
        resolutions.add(CameraRecording.Resolution.RES_1080P);

        final EnumSet<CameraRecording.Framerate> framerates = EnumSet.noneOf(CameraRecording.Framerate.class);

        framerates.add(CameraRecording.Framerate.FPS_24);
        framerates.add(CameraRecording.Framerate.FPS_25);
        framerates.add(CameraRecording.Framerate.FPS_30);

        final CameraRecordingSettingCore.Capability recordCapability = CameraRecordingSettingCore.Capability.of(
                EnumSet.of(CameraRecording.Mode.STANDARD),
                resolutions,
                framerates, false);

        recording.updateCapabilities(Collections.singleton(recordCapability));
        recording.updateMode(CameraRecording.Mode.STANDARD);

        final CameraPhotoSettingCore photo = mCameraCore.photo();
        final Collection<CameraPhotoSettingCore.Capability> photoCapabilities = new HashSet<>();

        final CameraPhotoSettingCore.Capability single = CameraPhotoSettingCore.Capability.of(
                EnumSet.of(CameraPhoto.Mode.SINGLE),
                EnumSet.of(CameraPhoto.Format.FULL_FRAME),
                EnumSet.of(CameraPhoto.FileFormat.JPEG), false);

        final CameraPhotoSettingCore.Capability timelapse = CameraPhotoSettingCore.Capability.of(
                EnumSet.of(CameraPhoto.Mode.TIME_LAPSE),
                EnumSet.of(CameraPhoto.Format.FULL_FRAME),
                EnumSet.of(CameraPhoto.FileFormat.JPEG), false);

        photoCapabilities.add(single);
        photoCapabilities.add(timelapse);
        photo.updateCapabilities(photoCapabilities);

        photo.updateMode(CameraPhoto.Mode.SINGLE);
        photo.updateFormat(CameraPhoto.Format.FULL_FRAME);
        photo.updateFileFormat(CameraPhoto.FileFormat.JPEG);

        // Apply persisted presets so the UI reflects the last user-chosen values while
        // we wait for the firmware state events to arrive.
        applyPresets();
    }

    /**
     * Applies persisted preset values to the camera core.
     * <p>Called on connecting (before firmware events arrive) and on preset change.
     */
    private void applyPresets() {
        Camera.Mode mode = MODE_PRESET.load(mPresetDict);
        if (mode != null) {
            mCameraCore.mode().updateValue(mode);
        }

        CameraRecording.Resolution resolution = RECORDING_RESOLUTION_PRESET.load(mPresetDict);
        if (resolution != null) {
            mCameraCore.recording().updateResolution(resolution);
        }

        CameraRecording.Framerate framerate = RECORDING_FRAMERATE_PRESET.load(mPresetDict);
        if (framerate != null) {
            mCameraCore.recording().updateFramerate(framerate);
        }

        CameraPhoto.Mode photoMode = PHOTO_MODE_PRESET.load(mPresetDict);
        if (photoMode != null) {
            mCameraCore.photo().updateMode(photoMode);
        }

        CameraEvCompensation ev = EV_COMPENSATION_PRESET.load(mPresetDict);
        if (ev != null) {
            mCameraCore.exposureCompensation().updateValue(ev);
        }
    }

    @Override
    protected void onConnected() {
        mCameraCore.publish();
    }

    @Override
    protected void onDisconnected() {
        mCameraCore.unpublish();
    }

    @Override
    protected void onForgetting() {
        if (mDeviceDict != null) {
            mDeviceDict.clear().commit();
        }
        mCameraCore.unpublish();
    }

    @Override
    protected void onPresetChange() {
        // Reload the preset dictionary (the underlying store may have switched to a different preset).
        mPresetDict = mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY);
        if (isConnected()) {
            applyPresets();
        }
        mCameraCore.notifyUpdated();
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        final int featureId = command.getFeatureId();

        if (featureId == ArsdkFeatureArdrone3.PictureSettingsState.UID) {
            ArsdkFeatureArdrone3.PictureSettingsState.decode(command, mPictureSettingsCallbacks);
        } else if (featureId == ArsdkFeatureArdrone3.MediaRecordState.UID) {
            ArsdkFeatureArdrone3.MediaRecordState.decode(command, mRecordStateCallbacks);
        } else if (featureId == ArsdkFeatureArdrone3.MediaRecordEvent.UID) {
            ArsdkFeatureArdrone3.MediaRecordEvent.decode(command, mRecordEventCallbacks);
        }
    }

    private int massStorageId = 0;

    private ArsdkFeatureArdrone3.PictureSettingsState.Callback mPictureSettingsCallbacks = new ArsdkFeatureArdrone3.PictureSettingsState.Callback() {
        @Override
        public void onPictureFormatChanged(@Nullable ArsdkFeatureArdrone3.PicturesettingsstatePictureformatchangedType type) {

        }

        @Override
        public void onAutoWhiteBalanceChanged(@Nullable ArsdkFeatureArdrone3.PicturesettingsstateAutowhitebalancechangedType type) {
            if (type == null) return;

            switch (type) {
                case AUTO:
                    mCameraCore.whiteBalance().updateMode(CameraWhiteBalance.Mode.AUTOMATIC);
                    break;
                case TUNGSTEN:
                    mCameraCore.whiteBalance().updateMode(CameraWhiteBalance.Mode.INCANDESCENT);
                    break;
                case DAYLIGHT:
                    mCameraCore.whiteBalance().updateMode(CameraWhiteBalance.Mode.DAYLIGHT);
                    break;
                case CLOUDY:
                    mCameraCore.whiteBalance().updateMode(CameraWhiteBalance.Mode.CLOUDY);
                    break;
                case COOL_WHITE:
                    mCameraCore.whiteBalance().updateMode(CameraWhiteBalance.Mode.COOL_WHITE_FLUORESCENT);
                    break;
            }

            mCameraCore.notifyUpdated();
        }

        @Override
        public void onExpositionChanged(float value, float min, float max) {
            // Derive the supported EV set from the firmware-reported range.
            // ardrone3 uses 0.33-step increments; enumerate all CameraEvCompensation values
            // whose float equivalent falls within [min, max] (inclusive, with a small epsilon
            // to guard against floating-point rounding).
            final float EV_EPSILON = 0.01f;
            final EnumSet<CameraEvCompensation> supported =
                    EnumSet.noneOf(CameraEvCompensation.class);
            for (CameraEvCompensation ev : CameraEvCompensation.values()) {
                float f = ExposureAdapter.from(ev);
                if (f >= min - EV_EPSILON && f <= max + EV_EPSILON) {
                    supported.add(ev);
                }
            }
            if (!supported.isEmpty()) {
                EV_RANGE_SETTING.save(mDeviceDict, supported);
                mCameraCore.exposureCompensation().updateAvailableValues(supported);
            }
            mCameraCore.exposureCompensation().updateValue(ExposureAdapter.from(value));
            EV_COMPENSATION_PRESET.save(mPresetDict, ExposureAdapter.from(value));
            mCameraCore.notifyUpdated();
        }

        @Override
        public void onSaturationChanged(float value, float min, float max) {
            mCameraCore.style().saturation().updateBounds(IntegerRange.of(Math.round(min), Math.round(max)));
            mCameraCore.style().saturation().updateValue(Math.round(value));
            mCameraCore.notifyUpdated();
        }

        @Override
        public void onTimelapseChanged(int enabled, float interval, float mininterval, float maxinterval) {
            final DoubleRange range = DoubleRange.of(mininterval, maxinterval);
            mCameraCore.photo().updateTimelapseIntervalRange(range);

            mCameraCore.photo().updateTimelapseInterval(interval);

            final CameraPhoto.Mode photoMode =
                    enabled == 1 ? CameraPhoto.Mode.TIME_LAPSE : CameraPhoto.Mode.SINGLE;
            mCameraCore.photo().updateMode(photoMode);
            PHOTO_MODE_PRESET.save(mPresetDict, photoMode);

            mCameraCore.notifyUpdated();
        }

        @Override
        public void onVideoAutorecordChanged(int enabled, int storageId) {
            massStorageId = storageId;
            mCameraCore.autoRecord().updateValue(enabled == 1);
            mCameraCore.notifyUpdated();
        }

        // covered in gimbal
        @Override
        public void onVideoStabilizationModeChanged(@Nullable ArsdkFeatureArdrone3.PicturesettingsstateVideostabilizationmodechangedMode mode) {}

        @Override
        public void onVideoRecordingModeChanged(@Nullable ArsdkFeatureArdrone3.PicturesettingsstateVideorecordingmodechangedMode mode) {
        }

        @Override
        public void onVideoFramerateChanged(@Nullable ArsdkFeatureArdrone3.PicturesettingsstateVideoframeratechangedFramerate framerate) {
            if (framerate == null) return;
            final CameraRecording.Framerate fr = FramerateAdapter.from(framerate);
            mCameraCore.recording().updateFramerate(fr);
            RECORDING_FRAMERATE_PRESET.save(mPresetDict, fr);
            mCameraCore.notifyUpdated();
        }

        @Override
        public void onVideoResolutionsChanged(@Nullable ArsdkFeatureArdrone3.PicturesettingsstateVideoresolutionschangedType type) {
            if (type == null) return;
            final CameraRecording.Resolution res = ResolutionAdapter.from(type);
            mCameraCore.recording().updateResolution(res);
            RECORDING_RESOLUTION_PRESET.save(mPresetDict, res);
            mCameraCore.notifyUpdated();
        }
    };

    private ArsdkFeatureArdrone3.MediaRecordState.Callback mRecordStateCallbacks = new ArsdkFeatureArdrone3.MediaRecordState.Callback() {
        @Override
        public void onPictureStateChangedV2(@Nullable ArsdkFeatureArdrone3.MediarecordstatePicturestatechangedv2State state, @Nullable ArsdkFeatureArdrone3.MediarecordstatePicturestatechangedv2Error error) {
            if (state == null || error == null) return;

            final CameraPhoto.State.FunctionState functionState;

            switch (state) {
                case READY:
                    functionState = CameraPhoto.State.FunctionState.STOPPED;
                    break;
                case BUSY:
                    functionState = CameraPhoto.State.FunctionState.STARTED;
                    break;
                case NOTAVAILABLE:
                default:
                    switch (error) {
                        case OK:
                            functionState = CameraPhoto.State.FunctionState.STOPPED;
                            break;
                        case LOWBATTERY:
                        case UNKNOWN:
                        case CAMERA_KO:
                            functionState = CameraPhoto.State.FunctionState.ERROR_INTERNAL;
                            break;
                        case MEMORYFULL:
                            functionState = CameraPhoto.State.FunctionState.ERROR_INSUFFICIENT_STORAGE;
                            break;
                        default:
                            functionState = CameraPhoto.State.FunctionState.UNAVAILABLE;
                    }
                    break;
            }

            if (!functionState.equals(mCameraCore.photoState().get())) {
                mCameraCore.photoState().updateState(functionState);
                mCameraCore.notifyUpdated();
            }
        }

        @Override
        public void onVideoStateChangedV2(@Nullable ArsdkFeatureArdrone3.MediarecordstateVideostatechangedv2State state, @Nullable ArsdkFeatureArdrone3.MediarecordstateVideostatechangedv2Error error) {
            if (state == null || error == null) return;

            final CameraRecording.State.FunctionState functionState;

            switch (state) {
                case STOPPED:
                    functionState = CameraRecording.State.FunctionState.STOPPED;
                    break;
                case STARTED:
                    functionState = CameraRecording.State.FunctionState.STARTED;
                    break;
                case NOTAVAILABLE:
                default:
                    switch (error) {
                        case OK:
                            functionState = CameraRecording.State.FunctionState.STOPPED;
                            break;
                        case MEMORYFULL:
                            functionState = CameraRecording.State.FunctionState.ERROR_INSUFFICIENT_STORAGE_SPACE;
                            break;
                        case CAMERA_KO:
                        case UNKNOWN:
                        case LOWBATTERY:
                            functionState = CameraRecording.State.FunctionState.ERROR_INTERNAL;
                            break;
                        default:
                            functionState = CameraRecording.State.FunctionState.UNAVAILABLE;
                            break;
                    }
                    break;
            }

            if (!functionState.equals(mCameraCore.recordingState().get())) {
                mCameraCore.recordingState().updateState(functionState);
                mCameraCore.notifyUpdated();
            }
        }
    };

    private ArsdkFeatureArdrone3.MediaRecordEvent.Callback mRecordEventCallbacks = new ArsdkFeatureArdrone3.MediaRecordEvent.Callback() {
        @Override
        public void onPictureEventChanged(@Nullable ArsdkFeatureArdrone3.MediarecordeventPictureeventchangedEvent event, @Nullable ArsdkFeatureArdrone3.MediarecordeventPictureeventchangedError error) {
            if (event == null || error == null) return;

            final CameraPhoto.State.FunctionState functionState;

            switch (event) {
                case TAKEN:
                    functionState = CameraPhoto.State.FunctionState.STOPPED;
                    break;
                case FAILED:
                default:
                    switch (error) {
                        case MEMORYFULL:
                            functionState = CameraPhoto.State.FunctionState.ERROR_INSUFFICIENT_STORAGE;
                            break;
                        case OK:
                            functionState = CameraPhoto.State.FunctionState.STOPPED;
                            break;
                        case BUSY:
                        case LOWBATTERY:
                        case UNKNOWN:
                            functionState = CameraPhoto.State.FunctionState.ERROR_INTERNAL;
                            break;
                        case NOTAVAILABLE:
                        default:
                            functionState = CameraPhoto.State.FunctionState.UNAVAILABLE;
                            break;
                    }
                    break;
            }

            if (!functionState.equals(mCameraCore.photoState().get())) {
                mCameraCore.photoState().updateState(functionState);
                mCameraCore.notifyUpdated();
            }
        }

        @Override
        public void onVideoEventChanged(@Nullable ArsdkFeatureArdrone3.MediarecordeventVideoeventchangedEvent event, @Nullable ArsdkFeatureArdrone3.MediarecordeventVideoeventchangedError error) {
            if (event == null || error == null) return;

            final CameraRecording.State.FunctionState functionState;

            switch (event) {
                case START:
                    functionState = CameraRecording.State.FunctionState.STARTED;
                    break;
                case STOP:
                    functionState = CameraRecording.State.FunctionState.STOPPED;
                    break;
                case FAILED:
                default:
                    switch (error) {
                        case MEMORYFULL:
                            functionState = CameraRecording.State.FunctionState.ERROR_INSUFFICIENT_STORAGE_SPACE;
                            break;
                        case OK:
                        case AUTOSTOPPED:
                            functionState = CameraRecording.State.FunctionState.STOPPED;
                            break;
                        case UNKNOWN:
                        case BUSY:
                        case LOWBATTERY:
                            functionState = CameraRecording.State.FunctionState.ERROR_INTERNAL;
                            break;
                        case NOTAVAILABLE:
                        default:
                            functionState = CameraRecording.State.FunctionState.UNAVAILABLE;
                            break;
                    }
                    break;
            }

            if (!functionState.equals(mCameraCore.recordingState().get())) {
                mCameraCore.recordingState().updateState(functionState);
                mCameraCore.notifyUpdated();
            }
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final CameraCore.Backend mBackend = new CameraCore.Backend() {
        @Override
        public boolean setMaxZoomSpeed(double speed) {
            return false;
        }

        @Override
        public boolean setQualityDegradationAllowance(boolean allowed) {
            return false;
        }

        @Override
        public void control(@NonNull CameraZoom.ControlMode mode, double target) {
            //TODO: would be cool if this could be passed to the gl video stream view
        }

        @Override
        public boolean setWhiteBalance(@NonNull CameraWhiteBalance.Mode mode, @NonNull CameraWhiteBalance.Temperature temperature) {
            switch (mode) {
                case AUTOMATIC:
                    return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeAutoWhiteBalanceSelection(ArsdkFeatureArdrone3.PicturesettingsAutowhitebalanceselectionType.AUTO));
                case INCANDESCENT:
                    return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeAutoWhiteBalanceSelection(ArsdkFeatureArdrone3.PicturesettingsAutowhitebalanceselectionType.TUNGSTEN));
                case COOL_WHITE_FLUORESCENT:
                    return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeAutoWhiteBalanceSelection(ArsdkFeatureArdrone3.PicturesettingsAutowhitebalanceselectionType.COOL_WHITE));
                case DAYLIGHT:
                    return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeAutoWhiteBalanceSelection(ArsdkFeatureArdrone3.PicturesettingsAutowhitebalanceselectionType.DAYLIGHT));
                case CLOUDY:
                    return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeAutoWhiteBalanceSelection(ArsdkFeatureArdrone3.PicturesettingsAutowhitebalanceselectionType.CLOUDY));
            }

            return false;
        }

        @Override
        public boolean setWhiteBalanceLock(boolean locked) {
            return false;
        }

        @Override
        public boolean setStyle(@NonNull CameraStyle.Style style) {
            return false;
        }

        @Override
        public boolean setStyleParameters(int saturation, int contrast, int sharpness) {
            if (saturation != mCameraCore.style().saturation().getValue()) {
                return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeSaturationSelection(saturation));
            }
            return false;
        }

        @Override
        public boolean setRecording(@NonNull CameraRecording.Mode mode, @Nullable CameraRecording.Resolution resolution, @Nullable CameraRecording.Framerate framerate, @Nullable CameraRecording.HyperlapseValue hyperlapse) {

            boolean sent = false;

            if (mode == CameraRecording.Mode.STANDARD) {
                if (resolution != null && !resolution.equals(mCameraCore.recording().resolution())) {
                    sent = sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeVideoResolutions(ResolutionAdapter.from(resolution)));
                }

                if (framerate != null && !framerate.equals(mCameraCore.recording().framerate())) {
                    sent = sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeVideoFramerate(FramerateAdapter.from(framerate)));
                }
            }

            return sent;
        }

        @Override
        public boolean setPhoto(@NonNull CameraPhoto.Mode mode, @Nullable CameraPhoto.Format format, @Nullable CameraPhoto.FileFormat fileFormat, @Nullable CameraPhoto.BurstValue burst, @Nullable CameraPhoto.BracketingValue bracketing, @Nullable Double timelapseInterval, @Nullable Double gpslapseInterval) {
            final float interval = timelapseInterval != null ? timelapseInterval.floatValue() : (float) mCameraCore.photo().timelapseInterval();
            switch (mode) {
                case SINGLE:
                    return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeTimelapseSelection(0, interval));
                case TIME_LAPSE:
                    return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeTimelapseSelection(1, interval));
            }
            return false;
        }

        @Override
        public boolean setExposure(@NonNull CameraExposure.Mode mode,
                                   @NonNull CameraExposure.ShutterSpeed manualShutterSpeed,
                                   @NonNull CameraExposure.IsoSensitivity manualIsoSensitivity,
                                   @NonNull CameraExposure.IsoSensitivity maxIsoSensitivity,
                                   @NonNull CameraExposure.AutoExposureMeteringMode autoExposureMeteringMode) {
            return false;
        }

        @Override
        public boolean setExposureLock(@NonNull CameraExposureLock.Mode mode, double centerX, double centerY) {
            return false;
        }

        @Override
        public boolean setAlignment(double yaw, double pitch, double roll) {
            return false;
        }

        @Override
        public boolean resetAlignment() {
            return false;
        }

        @Override
        public boolean setMode(@NonNull Camera.Mode mode) {
            // ardrone3 has no explicit "set camera mode" command; mode is implicit from the
            // timelapse-enabled state reported by PictureSettingsState.TimelapseChanged.
            // Persist the preference so it survives reconnect. Do NOT call updateValue or
            // notifyUpdated here — the SettingController rollback contract requires the backend
            // to return true only when a command was sent (and confirmation will arrive later).
            // Since no command is sent, return false and let the firmware-confirmed
            // onTimelapseChanged event drive the mode update via updateValue.
            MODE_PRESET.save(mPresetDict, mode);
            return false;
        }

        @Override
        public boolean setEvCompensation(@NonNull CameraEvCompensation ev) {
            EV_COMPENSATION_PRESET.save(mPresetDict, ev);
            return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeExpositionSelection(ExposureAdapter.from(ev)));
        }

        @Override
        public boolean setAutoHdr(boolean enable) {
            return false;
        }

        @Override
        public boolean setAutoRecord(boolean enable) {
            return sendCommand(ArsdkFeatureArdrone3.PictureSettings.encodeVideoAutorecordSelection(enable ? 1 : 0, massStorageId));
        }

        @Override
        public boolean startPhotoCapture() {
            if (mCameraCore.photo().mode() == CameraPhoto.Mode.TIME_LAPSE) {
                // Timelapse capture is started via VideoV2 START (same as normal video).
                // PictureV2 is single-shot only and must not be sent for timelapse mode.
                return sendCommand(ArsdkFeatureArdrone3.MediaRecord.encodeVideoV2(
                        ArsdkFeatureArdrone3.MediarecordVideov2Record.START));
            }
            // SINGLE (and any other non-timelapse mode): single shot via PictureV2.
            return sendCommand(ArsdkFeatureArdrone3.MediaRecord.encodePictureV2());
        }

        @Override
        public boolean stopPhotoCapture() {
            if (mCameraCore.photo().mode() == CameraPhoto.Mode.TIME_LAPSE) {
                // Timelapse capture is stopped via VideoV2 STOP. There is no separate
                // "stop timelapse" command; disabling timelapse mode via TimelapseSelection
                // configures the mode but does not stop an in-progress capture.
                return sendCommand(ArsdkFeatureArdrone3.MediaRecord.encodeVideoV2(
                        ArsdkFeatureArdrone3.MediarecordVideov2Record.STOP));
            }
            // Single-shot photos complete atomically; no stop command is needed.
            return false;
        }

        @Override
        public boolean startRecording() {
            return sendCommand(ArsdkFeatureArdrone3.MediaRecord.encodeVideoV2(ArsdkFeatureArdrone3.MediarecordVideov2Record.START));
        }

        @Override
        public boolean stopRecording() {
            return sendCommand(ArsdkFeatureArdrone3.MediaRecord.encodeVideoV2(ArsdkFeatureArdrone3.MediarecordVideov2Record.STOP));
        }
    };
}
