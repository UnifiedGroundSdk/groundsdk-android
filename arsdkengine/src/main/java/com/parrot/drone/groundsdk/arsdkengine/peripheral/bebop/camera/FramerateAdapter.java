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

import com.parrot.drone.groundsdk.device.peripheral.camera.CameraRecording;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCamera;

import androidx.annotation.NonNull;

/**
 * Utility class to adapt {@link ArsdkFeatureCamera.Framerate camera feature} to {@link CameraRecording.Framerate
 * groundsdk} recording resolutions.
 */
final class FramerateAdapter {

    @NonNull
    static CameraRecording.Framerate from(ArsdkFeatureArdrone3.PicturesettingsstateVideoframeratechangedFramerate framerate) {
        switch (framerate) {
            case E24_FPS:
                return CameraRecording.Framerate.FPS_24;
            case E25_FPS:
                return CameraRecording.Framerate.FPS_25;
            default:
                return CameraRecording.Framerate.FPS_30;
        }
    }

    @NonNull
    static ArsdkFeatureArdrone3.PicturesettingsVideoframerateFramerate from(CameraRecording.Framerate framerate) {
        switch (framerate) {
            case FPS_30:
                return ArsdkFeatureArdrone3.PicturesettingsVideoframerateFramerate.E30_FPS;
            case FPS_25:
                return ArsdkFeatureArdrone3.PicturesettingsVideoframerateFramerate.E25_FPS;
            default:
                return ArsdkFeatureArdrone3.PicturesettingsVideoframerateFramerate.E24_FPS;
        }
    }
}
