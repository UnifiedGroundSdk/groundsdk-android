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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature.camera;

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
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Camera peripheral(s) controller for Anafi family drones. */
public final class MiniatureCameraController extends DronePeripheralController {

    private MainCameraCore mCameraCore;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public MiniatureCameraController(@NonNull DroneController droneController) {
        super(droneController);
        mCameraCore = new MainCameraCore(mComponentStore, mBackend);
    }

    @Override
    protected void onConnecting() {
        mCameraCore.mode().updateAvailableValues(EnumSet.allOf(Camera.Mode.class));

        mCameraCore.photoState().updateState(CameraPhoto.State.FunctionState.STOPPED);
        mCameraCore.recordingState().updateState(CameraRecording.State.FunctionState.STARTED);

        mCameraCore.mode().updateValue(Camera.Mode.RECORDING);

        mCameraCore.updateActiveFlag(true);
        mCameraCore.updateHdrActive(false);

        mCameraCore.autoRecord().updateSupportedFlag(true);
        mCameraCore.autoHdr().updateSupportedFlag(false);

        mCameraCore.style().updateSupportedStyles(Collections.singleton(CameraStyle.Style.STANDARD));
        mCameraCore.style().updateStyle(CameraStyle.Style.STANDARD);

        mCameraCore.createAlignmentIfNeeded().updateSupportedPitchRange(new DoubleRange() {
            @Override
            public double getLower() {
                return 0;
            }

            @Override
            public double getUpper() {
                return 0;
            }
        });
        mCameraCore.createAlignmentIfNeeded().updateSupportedRollRange(mCameraCore.createAlignmentIfNeeded().supportedPitchRange());
        mCameraCore.createAlignmentIfNeeded().updateSupportedYawRange(mCameraCore.createAlignmentIfNeeded().supportedPitchRange());

        mCameraCore.createZoomIfNeeded().updateAvailability(false);
        mCameraCore.createWhiteBalanceLockIfNeeded().updateLockable(false);

        final CameraRecordingSettingCore recording =  mCameraCore.recording();

        final Set<CameraRecording.Resolution> resolutions = new HashSet<>();

        resolutions.add(CameraRecording.Resolution.RES_VGA);
        resolutions.add(CameraRecording.Resolution.RES_480P);
        resolutions.add(CameraRecording.Resolution.RES_720P);

        final CameraRecordingSettingCore.Capability record = CameraRecordingSettingCore.Capability.of(Collections.singleton(CameraRecording.Mode.STANDARD),
                resolutions,
                Collections.singleton(CameraRecording.Framerate.FPS_30), false);

        recording.updateCapabilities(Collections.singleton(record));

        recording.updateMode(CameraRecording.Mode.STANDARD);
        recording.updateResolution(CameraRecording.Resolution.RES_VGA);
        recording.updateFramerate(CameraRecording.Framerate.FPS_30);

        final CameraPhotoSettingCore photo = mCameraCore.photo();

        final CameraPhotoSettingCore.Capability photos = CameraPhotoSettingCore.Capability.of(Collections.singleton(CameraPhoto.Mode.SINGLE),
                Collections.singleton(CameraPhoto.Format.FULL_FRAME),
                Collections.singleton(CameraPhoto.FileFormat.JPEG), false);

        photo.updateCapabilities(Collections.singleton(photos));

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

        if (featureId == ArsdkFeatureMinidrone.MinicamState.UID) {
            ArsdkFeatureMinidrone.MinicamState.decode(command, mMinicamStateCallbacks);
        } else if (featureId == ArsdkFeatureMinidrone.VideoSettingsState.UID) {
            ArsdkFeatureMinidrone.VideoSettingsState.decode(command, mVideoSettingsStateCallbacks);
        } else if (featureId == ArsdkFeatureMinidrone.MediaRecordState.UID) {
            ArsdkFeatureMinidrone.MediaRecordState.decode(command, mMediaRecordStateCallbacks);
        }
    }

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

        }

        @Override
        public boolean setWhiteBalance(@NonNull CameraWhiteBalance.Mode mode, @NonNull CameraWhiteBalance.Temperature temperature) {
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
            return false;
        }

        @Override
        public boolean setRecording(@NonNull CameraRecording.Mode mode, @Nullable CameraRecording.Resolution resolution, @Nullable CameraRecording.Framerate framerate, @Nullable CameraRecording.HyperlapseValue hyperlapse) {
            assert resolution != null;

            switch (resolution) {
                case RES_720P:
                    return sendCommand(ArsdkFeatureMinidrone.VideoSettings.encodeVideoResolution(ArsdkFeatureMinidrone.VideosettingsVideoresolutionType.HD));
                case RES_480P:
                    return sendCommand(ArsdkFeatureMinidrone.VideoSettings.encodeVideoResolution(ArsdkFeatureMinidrone.VideosettingsVideoresolutionType.HQ));
                case RES_VGA:
                    return sendCommand(ArsdkFeatureMinidrone.VideoSettings.encodeVideoResolution(ArsdkFeatureMinidrone.VideosettingsVideoresolutionType.VGA));
            }
            return true;
        }

        @Override
        public boolean setPhoto(@NonNull CameraPhoto.Mode mode, @Nullable CameraPhoto.Format format, @Nullable CameraPhoto.FileFormat fileFormat, @Nullable CameraPhoto.BurstValue burst, @Nullable CameraPhoto.BracketingValue bracketing, @Nullable Double timelapseInterval, @Nullable Double gpslapseInterval) {
            return mode == CameraPhoto.Mode.SINGLE;
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
            return false;
        }

        @Override
        public boolean setAutoHdr(boolean enable) {
            return false;
        }

        @Override
        public boolean setAutoRecord(boolean enable) {
            return sendCommand(ArsdkFeatureMinidrone.VideoSettings.encodeAutorecord(enable ? 1 : 0));
        }

        @Override
        public boolean startPhotoCapture() {
            mCameraCore.mode().updateValue(Camera.Mode.PHOTO);
            return sendCommand(ArsdkFeatureMinidrone.Minicam.encodePicture());
//            return sendCommand(ArsdkFeatureMinidrone.MediaRecord.encodePictureV2());

        }

        @Override
        public boolean stopPhotoCapture() {
            return false;
        }

        @Override
        public boolean startRecording() {
            mCameraCore.mode().updateValue(Camera.Mode.RECORDING);
            return sendCommand(ArsdkFeatureMinidrone.Minicam.encodeVideo(ArsdkFeatureMinidrone.MinicamVideoRecord.START));
        }

        @Override
        public boolean stopRecording() {
            return sendCommand(ArsdkFeatureMinidrone.Minicam.encodeVideo(ArsdkFeatureMinidrone.MinicamVideoRecord.STOP));
        }
    };

    private final ArsdkFeatureMinidrone.MinicamState.Callback mMinicamStateCallbacks = new ArsdkFeatureMinidrone.MinicamState.Callback() {
        @Override
        public void onPowerModeChanged(@Nullable ArsdkFeatureMinidrone.MinicamstatePowermodechangedPowerMode powerMode) {
        }

        @Override
        public void onProductSerialChanged(String serialNumber) {
        }

        @Override
        public void onStateChanged(@Nullable ArsdkFeatureMinidrone.MinicamstateStatechangedState state) {
        }

        @Override
        public void onVersionChanged(String software, String hardware) {
        }

        @Override
        public void onPictureChanged(@Nullable ArsdkFeatureMinidrone.MinicamstatePicturechangedState state, @Nullable ArsdkFeatureMinidrone.MinicamstatePicturechangedResult result) {
            assert state != null;
            assert result != null;

            switch (result) {
                case SUCCESS:
                    mCameraCore.photoState().updatePhotoCount(1);
                    break;
                case CONTINUOUS_SHOOTING:
                case FULL_QUEUE:
                case ERROR:
                    mCameraCore.photoState().updateState(CameraPhoto.State.FunctionState.ERROR_INTERNAL);
                    break;
                case FULL_DEVICE:
                case NO_SD:
                case SD_BAD_FORMAT:
                case SD_FORMATTING:
                    mCameraCore.photoState().updateState(CameraPhoto.State.FunctionState.ERROR_INSUFFICIENT_STORAGE);
                    break;
            }

            switch (state) {
                case READY:
                    mCameraCore.photoState().updateState(CameraPhoto.State.FunctionState.STOPPED);
                    break;
                case BUSY:
                    mCameraCore.photoState().updateState(CameraPhoto.State.FunctionState.STARTED);
                    break;
                case NOT_AVAILABLE:
                    break;
            }

            mCameraCore.notifyUpdated();
        }

        @Override
        public void onVideoStateChanged(@Nullable ArsdkFeatureMinidrone.MinicamstateVideostatechangedState state, @Nullable ArsdkFeatureMinidrone.MinicamstateVideostatechangedError error) {
            assert state != null;
            assert error != null;
            
            switch (state) {
                case NOTAVAILABLE:
                case STOPPED:
                    switch (error) {
                        case OK:
                            mCameraCore.recordingState().updateState(CameraRecording.State.FunctionState.STOPPED);
                            break;

                        case UNKNOWN:
                        case CAMERA_KO:
                        case LOWBATTERY:
                            mCameraCore.recordingState().updateState(CameraRecording.State.FunctionState.ERROR_INTERNAL);
                            break;
                        case MEMORYFULL:
                        case NO_SD:
                            mCameraCore.recordingState().updateState(CameraRecording.State.FunctionState.ERROR_INSUFFICIENT_STORAGE_SPACE);
                            break;
                    }
                    break;
                case STARTED:
                    mCameraCore.recordingState().updateState(CameraRecording.State.FunctionState.STARTED);
                    break;
            }

            mCameraCore.notifyUpdated();
        }

        @Override
        public void onMassStorageFormatChanged(int state) {
        }
    };

    private final ArsdkFeatureMinidrone.VideoSettingsState.Callback mVideoSettingsStateCallbacks = new ArsdkFeatureMinidrone.VideoSettingsState.Callback() {
        @Override
        public void onAutorecordChanged(int enabled) {
            mCameraCore.autoRecord().setEnabled(enabled == 1);
            mCameraCore.notifyUpdated();
        }

        @Override
        public void onVideoResolutionChanged(@Nullable ArsdkFeatureMinidrone.VideosettingsstateVideoresolutionchangedType type) {
            assert type != null;
            final CameraRecording.Resolution res = ResolutionAdapter.from(type);
            mCameraCore.recording().updateMode(CameraRecording.Mode.STANDARD);
            mCameraCore.recording().updateResolution(res);
            mCameraCore.recording().updateFramerate(CameraRecording.Framerate.FPS_30);
            mCameraCore.notifyUpdated();
        }
    };

    private final ArsdkFeatureMinidrone.MediaRecordState.Callback mMediaRecordStateCallbacks = new ArsdkFeatureMinidrone.MediaRecordState.Callback() {
        @Override
        public void onPictureStateChangedV2(@Nullable ArsdkFeatureMinidrone.MediarecordstatePicturestatechangedv2State state, @Nullable ArsdkFeatureMinidrone.MediarecordstatePicturestatechangedv2Error error) {
            assert state != null;
            assert error != null;

            ArsdkFeatureMinidrone.MinicamstatePicturechangedState camstate;
            ArsdkFeatureMinidrone.MinicamstatePicturechangedResult camresult;

            switch (state) {
                case READY:
                    camstate = ArsdkFeatureMinidrone.MinicamstatePicturechangedState.READY;
                    break;
                case BUSY:
                    camstate = ArsdkFeatureMinidrone.MinicamstatePicturechangedState.BUSY;
                    break;
                case NOTAVAILABLE:
                default:
                    camstate = ArsdkFeatureMinidrone.MinicamstatePicturechangedState.NOT_AVAILABLE;
                    break;

            }

            switch (error) {

                case OK:
                    camresult = ArsdkFeatureMinidrone.MinicamstatePicturechangedResult.SUCCESS;
                    break;
                case MEMORYFULL:
                    camresult = ArsdkFeatureMinidrone.MinicamstatePicturechangedResult.FULL_DEVICE;
                    break;
                case UNKNOWN:
                case LOWBATTERY:
                case CAMERA_KO:
                default:
                    camresult = ArsdkFeatureMinidrone.MinicamstatePicturechangedResult.ERROR;
                    break;
            }

            mMinicamStateCallbacks.onPictureChanged(camstate, camresult);
        }
    };
}
