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

package com.parrot.drone.groundsdk.arsdkengine.instrument.miniature;

import android.util.Log;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.instrument.DroneInstrumentController;
import com.parrot.drone.groundsdk.internal.Maths;
import com.parrot.drone.groundsdk.internal.device.instrument.CompassCore;
import com.parrot.drone.groundsdk.internal.utility.SystemHeading;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;

import static com.parrot.drone.groundsdk.arsdkengine.devicecontroller.MiniatureFamilyDroneController.setFromQuaternion2;

/** Compass instrument controller for Minidrone family drones. */
public class MiniatureCompass extends DroneInstrumentController implements SystemHeading.Monitor {

    private final MiniatureCompass miniatureCompass;
    private final DroneController droneController;

    /** The compass from which this object is the backend. */
    @NonNull
    private final CompassCore compassCore;

    private SystemHeading systemHeading = null;
    private double referenceHeading = Double.MIN_VALUE;

    /**
     * Constructor.
     *
     * @param droneController The drone controller that owns this component controller.
     */

    public MiniatureCompass(@NonNull DroneController droneController) {
        super(droneController);

        miniatureCompass = this;
        this.droneController = droneController;
        compassCore = new CompassCore(mComponentStore);
    }

    @Override
    public void onConnected() {
        try {
            systemHeading = droneController.getEngine().getUtilityOrThrow(SystemHeading.class);
            systemHeading.monitorWith(miniatureCompass);
        } catch (AssertionError ex) {
            Log.w("MiniatureCompass", "unable to acticate system heading: " + ex.getMessage(), ex);
        }
        compassCore.publish();
    }

    @Override
    public void onHeadingChanged(double heading) {
        referenceHeading = heading;
    }

    @Override
    public void onDisconnected() {
        if (systemHeading != null) {
            systemHeading.disposeMonitor(miniatureCompass);
        }

        compassCore.unpublish();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureMinidrone.NavigationDataState.UID) {
            ArsdkFeatureMinidrone.NavigationDataState.decode(command, mNavigationDataStateCallback);
        }
    }

    /** Callbacks called when a command of the feature ArsdkFeatureArdrone3.PilotingState is decoded. */
    private final ArsdkFeatureMinidrone.NavigationDataState.Callback mNavigationDataStateCallback =
            new ArsdkFeatureMinidrone.NavigationDataState.Callback() {

                @Override
                public void onDroneQuaternion(float qW, float qX, float qY, float qZ, int ts) {
                    if (systemHeading != null && referenceHeading != Double.MIN_VALUE) {
                        systemHeading.disposeMonitor(miniatureCompass);
                        systemHeading = null;
                    } else if (referenceHeading == Double.MIN_VALUE) return;

                    final double[] results = setFromQuaternion2(qW, qX, qY, qZ);

                    final double localYaw = Maths.radiansToBoundedDegrees(results[2]);
                    final double worldYaw;

                    if (referenceHeading != Double.MIN_VALUE) {
                        worldYaw = (localYaw + referenceHeading) % 360;
                    } else {
                        worldYaw  = localYaw;
                    }

                    compassCore.updateHeading(worldYaw).notifyUpdated();
                }
            };
}
