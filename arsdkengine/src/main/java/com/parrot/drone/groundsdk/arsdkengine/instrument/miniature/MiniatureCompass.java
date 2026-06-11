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

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.instrument.DroneInstrumentController;
import com.parrot.drone.groundsdk.internal.Maths;
import com.parrot.drone.groundsdk.internal.device.instrument.CompassCore;
import com.parrot.drone.groundsdk.internal.utility.SystemHeading;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.parrot.drone.groundsdk.arsdkengine.devicecontroller.MiniatureFamilyDroneController.setFromQuaternion2;

/** Compass instrument controller for Minidrone family drones. */
public class MiniatureCompass extends DroneInstrumentController implements SystemHeading.Monitor {

    private final MiniatureCompass miniatureCompass;
    private final DroneController droneController;

    /** The compass from which this object is the backend. */
    @NonNull
    private final CompassCore compassCore;

    /**
     * Phone compass heading (degrees, clockwise from geographic north) captured at connect time,
     * or {@code null} if the {@link SystemHeading} utility is unavailable on this device.
     * <p>
     * {@code setFromQuaternion2} yields yaw relative to the NED frame established at drone
     * startup.  Adding the takeoff heading rotates that NED-relative yaw to geographic north.
     * Once the first quaternion arrives the monitor is released; only the captured value is kept.
     */
    @Nullable
    private Double referenceHeading;

    /** {@code true} once {@link SystemHeading} has been disposed and may not be used. */
    private boolean systemHeadingDisposed;

    @Nullable
    private SystemHeading systemHeading;

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
        systemHeading = droneController.getEngine().getUtility(SystemHeading.class);
        if (systemHeading != null) {
            systemHeading.monitorWith(miniatureCompass);
        }
        // Publish regardless; heading will be updated on every quaternion event.
        compassCore.publish();
    }

    @Override
    public void onHeadingChanged(double heading) {
        // Keep updating until the first quaternion arrives and we dispose the monitor.
        referenceHeading = heading;
    }

    @Override
    public void onDisconnected() {
        disposeSystemHeading();
        referenceHeading = null;
        systemHeadingDisposed = false;
        compassCore.unpublish();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureMinidrone.NavigationDataState.UID) {
            ArsdkFeatureMinidrone.NavigationDataState.decode(command, mNavigationDataStateCallback);
        }
    }

    /** Releases the {@link SystemHeading} monitor if it has not already been released. */
    private void disposeSystemHeading() {
        if (!systemHeadingDisposed && systemHeading != null) {
            systemHeading.disposeMonitor(miniatureCompass);
            systemHeadingDisposed = true;
            systemHeading = null;
        }
    }

    /** Callbacks called when a command of the feature NavigationDataState is decoded. */
    private final ArsdkFeatureMinidrone.NavigationDataState.Callback mNavigationDataStateCallback =
            new ArsdkFeatureMinidrone.NavigationDataState.Callback() {

                @Override
                public void onDroneQuaternion(float qW, float qX, float qY, float qZ, int ts) {
                    // Stop tracking the phone compass once quaternions are flowing; keep
                    // whatever heading was last reported as the takeoff reference.
                    disposeSystemHeading();

                    // setFromQuaternion2 returns [pitch, roll, yaw] where yaw is the
                    // drone's heading relative to the NED frame fixed at startup (radians).
                    final double[] euler = setFromQuaternion2(qW, qX, qY, qZ);

                    // Convert NED-relative yaw to degrees in [0, 360).
                    final double nedYawDeg = Maths.radiansToBoundedDegrees(euler[2]);

                    // Rotate to geographic north if a phone compass reference is available.
                    // Without it we publish the NED-relative yaw; relative motion is still
                    // correct even if the absolute north offset is unknown.
                    final double geographicYawDeg;
                    if (referenceHeading != null) {
                        geographicYawDeg = (nedYawDeg + referenceHeading) % 360.0;
                    } else {
                        geographicYawDeg = nedYawDeg;
                    }

                    compassCore.updateHeading(geographicYawDeg).notifyUpdated();
                }
            };
}
