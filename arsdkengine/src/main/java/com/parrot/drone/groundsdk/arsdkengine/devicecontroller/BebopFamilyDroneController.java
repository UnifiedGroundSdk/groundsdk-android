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
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiAlarms;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiAltimeter;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiAttitudeIndicator;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiBatteryInfo;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiCameraExposure;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiCompass;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiFlightMeter;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiFlyingIndicators;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiGps;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiPhotoProgressIndicator;
import com.parrot.drone.groundsdk.arsdkengine.instrument.anafi.AnafiSpeedometer;
import com.parrot.drone.groundsdk.arsdkengine.instrument.bebop.BebopRadio;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.AnafiBeeper;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.AnafiGeofence;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.AnafiMagnetometer;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.AnafiMotors;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.AnafiSystemInfo;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.AnafiTargetTracker;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.anafi.wifi.AnafiWifiAccessPoint;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.BebopRemovableUserStorage;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.BebopStreamServer;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.DiscoPitot;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.camera.BebopAntiFlicker;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.camera.BebopCameraController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.gimbal.BebopGimbal;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.media.BebopMediaStore;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.common.DebugDevToolbox;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.common.SensorsState;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.common.updater.FirmwareUpdaterProtocol;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.common.updater.UpdaterController;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.PilotingCommand;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.anafi.AnafiAnimationPilotingItf;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.anafi.AnafiFollowMePilotingItf;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.anafi.AnafiGuidedPilotingItf;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.anafi.AnafiLookAtPilotingItf;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.anafi.AnafiManualPilotingItf;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.anafi.AnafiPointOfInterestPilotingItf;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.anafi.AnafiReturnHomePilotingItf;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.bebop.BebopFlightPlanPilotingItf;
import com.parrot.drone.groundsdk.device.Drone;
import com.parrot.drone.groundsdk.internal.GroundSdkConfig;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Drone controller for all drones of the Anafi family. */
public class BebopFamilyDroneController extends DroneController {

    /**
     * Constructor.
     *
     * @param engine arsdk engine instance
     * @param uid    controlled device uid
     * @param model  controlled device model
     * @param name   controlled device initial name
     */
    public BebopFamilyDroneController(@NonNull ArsdkEngine engine, @NonNull String uid, @NonNull Drone.Model model,
                                      @NonNull String name) {
        /* Manual piloting interface, also the default interface when no other interface is active. */
        super(engine, uid, model, name, new PilotingCommand.Encoder.Bebop(),
                AnafiManualPilotingItf::new, EphemerisUploadProtocol::ftpUpload);

        registerComponentControllers(
                // always active piloting interfaces
                new AnafiAnimationPilotingItf(this),
                // non-default piloting interfaces
                new AnafiReturnHomePilotingItf(mActivationController),
                new BebopFlightPlanPilotingItf(mActivationController),
                new AnafiLookAtPilotingItf(mActivationController),
                new AnafiFollowMePilotingItf(mActivationController),
                new AnafiPointOfInterestPilotingItf(mActivationController),
                new AnafiGuidedPilotingItf(mActivationController),
                // instruments
                new AnafiAlarms(this),
                new AnafiAltimeter(this),
                new AnafiAttitudeIndicator(this),
                new AnafiCompass(this),
                new AnafiFlyingIndicators(this),
                new AnafiGps(this),
                new AnafiSpeedometer(this),
                new BebopRadio(this),
                new AnafiBatteryInfo(this),
                new AnafiFlightMeter(this),
                new AnafiCameraExposure(this),
                new AnafiPhotoProgressIndicator(this),
                // peripherals
                new AnafiMagnetometer(this),
                new DiscoPitot(this),
                new AnafiSystemInfo(this),
                new AnafiBeeper(this),
                new AnafiMotors(this),
                new SensorsState(this),
                new AnafiGeofence(this),
                new BebopMediaStore(this),
                new BebopRemovableUserStorage(this),
                GroundSdkConfig.get().isDevToolboxEnabled() ? new DebugDevToolbox(this) : null,
                UpdaterController.create(this, FirmwareUpdaterProtocol.Ftp::new),
//                FtpReportDownloader.create(this),
//                BebopFlightDataDownloader.create(this),
//                FtpFlightLogDownloader.create(this),
                new AnafiWifiAccessPoint(this),
                new BebopCameraController(this),
                new BebopAntiFlicker(this),
                new BebopGimbal(this),
                new AnafiTargetTracker(this),
//                new AnafiPreciseHome(this),
//                new AnafiThermalControl(this),
                new BebopStreamServer(this)
//                new AnafiLeds(this)
//                new AnafiPilotingControl(this)
        );
    }

    @Override
    void sendDate(@NonNull Date currentDate) {
//        sendCommand(ArsdkFeatureCommon.Common.encodeCurrentDateTime(Iso8601.toBaseDateAndTimeFormat(currentDate)));

        sendCommand(ArsdkFeatureCommon.Common.encodeCurrentDate(Iso8601.toBaseDateOnlyFormat(currentDate)));
        sendCommand(ArsdkFeatureCommon.Common.encodeCurrentTime(Iso8601.toBaseTimeOnlyFormat(currentDate)));

    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        super.onCommandReceived(command);
        if (command.getFeatureId() == ArsdkFeatureArdrone3.PilotingState.UID) {
            ArsdkFeatureArdrone3.PilotingState.decode(command, mPilotingStateCallback);
        }
    }

    /** Callbacks called when a command of the feature ArsdkFeatureArdrone3.PilotingState is decoded. */
    private final ArsdkFeatureArdrone3.PilotingState.Callback mPilotingStateCallback =
            new ArsdkFeatureArdrone3.PilotingState.Callback() {

                @Override
                public void onFlyingStateChanged(
                        @Nullable ArsdkFeatureArdrone3.PilotingstateFlyingstatechangedState state) {
                    updateLandedState(state == ArsdkFeatureArdrone3.PilotingstateFlyingstatechangedState.LANDED ||
                                      state == ArsdkFeatureArdrone3.PilotingstateFlyingstatechangedState.EMERGENCY);
                }
            };
}
