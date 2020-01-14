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

import com.parrot.drone.groundsdk.device.peripheral.camera.CameraEvCompensation;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;

import androidx.annotation.NonNull;

/**
 * Utility class to adapt {@link ArsdkFeatureArdrone3.PictureSettingsState onExpositionChanged float} to/from {@link CameraEvCompensation
 * groundsdk} compensation enum
 */
final class ExposureAdapter {

    /**
     * Converts a {@code float} to its {@code CameraEvCompensation} equivalent.
     *
     * @param exposure camera feature exposition to convert
     *
     * @return the groundsdk CameraEvCompensation equivalent
     */
    @NonNull
    static CameraEvCompensation from(@NonNull float exposure) {
        if (exposure <= -3) {
            return CameraEvCompensation.EV_MINUS_3;
        }
        if (exposure <= -2.67) {
            return CameraEvCompensation.EV_MINUS_2_67;
        }
        if (exposure <= -2.33) {
            return CameraEvCompensation.EV_MINUS_2_33;
        }
        if (exposure <= -2) {
            return CameraEvCompensation.EV_MINUS_2;
        }
        if (exposure <= -1.67) {
            return CameraEvCompensation.EV_MINUS_1_67;
        }
        if (exposure <= -1.33) {
            return CameraEvCompensation.EV_MINUS_1_33;
        }
        if (exposure <= -1) {
            return CameraEvCompensation.EV_MINUS_1;
        }
        if (exposure <= -0.67) {
            return CameraEvCompensation.EV_MINUS_0_67;
        }
        if (exposure <= -0.33) {
            return CameraEvCompensation.EV_MINUS_0_33;
        }
        if (exposure <= 0) {
            return CameraEvCompensation.EV_0;
        }
        if (exposure <= 0.33) {
            return CameraEvCompensation.EV_0_33;
        }
        if (exposure <= 0.67) {
            return CameraEvCompensation.EV_0_67;
        }
        if (exposure <= 1) {
            return CameraEvCompensation.EV_1;
        }
        if (exposure <= 1.33) {
            return CameraEvCompensation.EV_1_33;
        }
        if (exposure <= 1.67) {
            return CameraEvCompensation.EV_1_67;
        }
        if (exposure <= 2) {
            return CameraEvCompensation.EV_2;
        }
        if (exposure <= 2.33) {
            return CameraEvCompensation.EV_2_33;
        }
        if (exposure <= 2.67) {
            return CameraEvCompensation.EV_MINUS_2_67;
        }

        return CameraEvCompensation.EV_3;
    }

    static float from(@NonNull CameraEvCompensation cameraEvCompensation) {
        switch (cameraEvCompensation) {
            case EV_MINUS_3:
                return -3f;
            case EV_MINUS_2_67:
                return -2.67f;
            case EV_MINUS_2_33:
                return -2.33f;
            case EV_MINUS_2:
                return -2f;
            case EV_MINUS_1_67:
                return -1.67f;
            case EV_MINUS_1_33:
                return -1.33f;
            case EV_MINUS_1:
                return -1f;
            case EV_MINUS_0_67:
                return -0.67f;
            case EV_MINUS_0_33:
                return -0.33f;
            case EV_0:
                return 0f;
            case EV_0_33:
                return 0.33f;
            case EV_0_67:
                return 0.67f;
            case EV_1:
                return 1f;
            case EV_1_33:
                return 1.33f;
            case EV_1_67:
                return 1.67f;
            case EV_2:
                return 2f;
            case EV_2_33:
                return 2.33f;
            case EV_2_67:
                return 2.67f;
            case EV_3:
                return 3f;
        }

        return 0f;
    }
}
