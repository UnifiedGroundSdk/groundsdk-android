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

package com.parrot.drone.groundsdk.arsdkengine.devicecontroller;

import com.parrot.drone.groundsdk.arsdkengine.ArsdkEngine;
import com.parrot.drone.groundsdk.arsdkengine.Iso8601;
import com.parrot.drone.groundsdk.arsdkengine.ephemeris.EphemerisUploadProtocol;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiBatteryInfo;
import com.parrot.drone.groundsdk.arsdkengine.instrument.miniature.MiniatureAlarms;
import com.parrot.drone.groundsdk.arsdkengine.instrument.miniature.MiniatureAltimeter;
import com.parrot.drone.groundsdk.arsdkengine.instrument.miniature.MiniatureAttitudeIndicator;
import com.parrot.drone.groundsdk.arsdkengine.instrument.miniature.MiniatureCompass;
import com.parrot.drone.groundsdk.arsdkengine.instrument.miniature.MiniatureFlyingIndicators;
import com.parrot.drone.groundsdk.arsdkengine.instrument.miniature.MiniatureGps;
import com.parrot.drone.groundsdk.arsdkengine.instrument.miniature.MiniatureRadio;
import com.parrot.drone.groundsdk.arsdkengine.instrument.miniature.MiniatureSpeedometer;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.AnafiLeds;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.AnafiSystemInfo;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.camera.AnafiAntiFlicker;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.wifi.AnafiWifiAccessPoint;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.common.DebugDevToolbox;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.common.SensorsState;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.common.updater.FirmwareUpdaterProtocol;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.common.updater.UpdaterController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature.MiniatureMotors;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature.MiniatureRemovableUserStorage;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature.MiniatureStreamServer;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature.camera.MiniatureCameraController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature.media.MiniatureMediaStore;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.PilotingCommand;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.miniature.MiniatureManualPilotingItf;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.miniature.MiniatureReturnHomePilotingItf;
import com.parrot.drone.groundsdk.device.Drone;
import com.parrot.drone.groundsdk.internal.GroundSdkConfig;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Drone controller for all drones of the Anafi family. */
public class MiniatureFamilyDroneController extends DroneController {

    /**
     * Constructor.
     *
     * @param engine arsdk engine instance
     * @param uid    controlled device uid
     * @param model  controlled device model
     * @param name   controlled device initial name
     */
    public MiniatureFamilyDroneController(@NonNull ArsdkEngine engine, @NonNull String uid, @NonNull Drone.Model model,
                                          @NonNull String name) {
        /* Manual piloting interface, also the default interface when no other interface is active. */
        super(engine, uid, model, name, new PilotingCommand.Encoder.Mambo(),
                MiniatureManualPilotingItf::new, EphemerisUploadProtocol::httpUpload);

        registerComponentControllers(
                // always active piloting interfaces
                // non-default piloting interfaces
                new MiniatureReturnHomePilotingItf(mActivationController),
                // instruments
                new MiniatureAlarms(this),
                new MiniatureAltimeter(this),
                new MiniatureAttitudeIndicator(this),
                new MiniatureCompass(this),
                new MiniatureFlyingIndicators(this),
                new MiniatureGps(this),
                new MiniatureSpeedometer(this),
                new MiniatureRadio(engine.getContext(), this),
                new AnafiBatteryInfo(this),
//                new AnafiFlightMeter(this),
//                new AnafiCameraExposure(this),
//                new AnafiPhotoProgressIndicator(this),
                // peripherals
//                new AnafiMagnetometer(this),
                new AnafiSystemInfo(this),
                new SensorsState(this),

//                new AnafiBeeper(this),
                new MiniatureMotors(this),
//                new AnafiGeofence(this),
                new MiniatureMediaStore(this),
                new MiniatureRemovableUserStorage(this),
                GroundSdkConfig.get().isDevToolboxEnabled() ? new DebugDevToolbox(this) : null,
                UpdaterController.create(this, FirmwareUpdaterProtocol.Ftp::new),
//                HttpReportDownloader.create(this),
//                BebopFlightDataDownloader.create(this),
//                HttpFlightLogDownloader.create(this),
                new AnafiWifiAccessPoint(this),
                new MiniatureCameraController(this),
                new AnafiAntiFlicker(this),
//                new BebopGimbal(this),
//                new AnafiTargetTracker(this),
//                new AnafiPreciseHome(this),
//                new AnafiThermalControl(this),
                new MiniatureStreamServer(this),
                new AnafiLeds(this)
//                new AnafiPilotingControl(this)
        );
    }

    @Override
    void sendDate(@NonNull Date currentDate) {
        sendCommand(ArsdkFeatureCommon.Common.encodeCurrentDate(Iso8601.toBaseDateOnlyFormat(currentDate)));
        sendCommand(ArsdkFeatureCommon.Common.encodeCurrentTime(Iso8601.toBaseTimeOnlyFormat(currentDate)));
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        super.onCommandReceived(command);
        if (command.getFeatureId() == ArsdkFeatureMinidrone.PilotingState.UID) {
            ArsdkFeatureMinidrone.PilotingState.decode(command, mPilotingStateCallback);
        }
    }

    /** Callbacks called when a command of the feature PilotingState is decoded. */
    private final ArsdkFeatureMinidrone.PilotingState.Callback mPilotingStateCallback =
            new ArsdkFeatureMinidrone.PilotingState.Callback() {

                @Override
                public void onFlyingStateChanged(
                        @Nullable ArsdkFeatureMinidrone.PilotingstateFlyingstatechangedState state) {
                    updateLandedState(state == ArsdkFeatureMinidrone.PilotingstateFlyingstatechangedState.LANDED ||
                            state == ArsdkFeatureMinidrone.PilotingstateFlyingstatechangedState.EMERGENCY);
                }
            };

    public static double[] setFromQuaternion2(final float q0, final float q2, final float q1, final float q3) {

        double q2sqr = q2 * q2;
        double t0 = -2.0 * (q2sqr + q3 * q3) + 1.0;
        double t1 = +2.0 * (q1 * q2 + q0 * q3);
        double t2 = -2.0 * (q1 * q3 - q0 * q2);
        double t3 = +2.0 * (q2 * q3 + q0 * q1);
        double t4 = -2.0 * (q1 * q1 + q2sqr) + 1.0;

        t2 = t2 > 1.0 ? 1.0 : t2;
        t2 = t2 < -1.0 ? -1.0 : t2;

        final double pitch = Math.asin(t2);
        final double roll = Math.atan2(t3, t4);
        final double yaw = Math.atan2(t1, t0);

        return new double[] { pitch, roll, yaw };
    }
}
