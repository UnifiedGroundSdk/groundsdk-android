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

/** Camera peripheral(s) controller for Anafi family drones. */
public final class BebopCameraController extends DronePeripheralController {

    private MainCameraCore mCameraCore;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public BebopCameraController(@NonNull DroneController droneController) {
        super(droneController);
        mCameraCore = new MainCameraCore(mComponentStore, mBackend);
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

        mCameraCore.exposureCompensation().updateAvailableValues(EnumSet.noneOf(CameraEvCompensation.class));
        mCameraCore.exposureCompensation().updateAvailableValues(EnumSet.allOf(CameraEvCompensation.class));

        final CameraRecordingSettingCore recording =  mCameraCore.recording();
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
    }

    @Override
    protected void onPresetChange() {
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
            mCameraCore.exposureCompensation().updateValue(ExposureAdapter.from(value));
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

            if (enabled == 1) {
                mCameraCore.photo().updateMode(CameraPhoto.Mode.TIME_LAPSE);
            } else {
                mCameraCore.photo().updateMode(CameraPhoto.Mode.SINGLE);
            }

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
            mCameraCore.recording().updateFramerate(FramerateAdapter.from(framerate));
            mCameraCore.notifyUpdated();
        }

        @Override
        public void onVideoResolutionsChanged(@Nullable ArsdkFeatureArdrone3.PicturesettingsstateVideoresolutionschangedType type) {
            if (type == null) return;
            mCameraCore.recording().updateResolution(ResolutionAdapter.from(type));
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
        public boolean setExposure(@NonNull CameraExposure.Mode mode, @NonNull CameraExposure.ShutterSpeed manualShutterSpeed, @NonNull CameraExposure.IsoSensitivity manualIsoSensitivity, @NonNull CameraExposure.IsoSensitivity maxIsoSensitivity) {
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
            mCameraCore.mode().updateValue(mode);
            mCameraCore.notifyUpdated();
            return false;
        }

        @Override
        public boolean setEvCompensation(@NonNull CameraEvCompensation ev) {
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
            return sendCommand(ArsdkFeatureArdrone3.MediaRecord.encodePictureV2());
        }

        @Override
        public boolean stopPhotoCapture() {
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
