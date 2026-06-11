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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature;

import androidx.annotation.NonNull;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.internal.device.peripheral.HeadlightsCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

/**
 * Headlights peripheral controller for Miniature family drones.
 * <p>
 * The peripheral is only published once a {@code HeadlightsState.intensityChanged} event is
 * received from the drone; this proves the LED accessory is present (Mambo with LED cannon).
 * The event-gated publish pattern prevents the peripheral from appearing on Mambos that do not
 * carry the LED accessory.
 */
public final class MiniatureHeadlights extends DronePeripheralController {

    /** The headlights peripheral for which this object is the backend. */
    @NonNull
    private final HeadlightsCore mHeadlights;

    /**
     * {@code true} once a {@code HeadlightsState.intensityChanged} event has been received during
     * the current connection, confirming that the LED accessory is present.
     */
    private boolean mSupported;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public MiniatureHeadlights(@NonNull DroneController droneController) {
        super(droneController);
        mHeadlights = new HeadlightsCore(mComponentStore, mBackend);
    }

    @Override
    protected void onConnected() {
        // Publish is deferred until a HeadlightsState.intensityChanged event is received
        // (mSupported gate in the callback below).
    }

    @Override
    protected void onDisconnected() {
        mHeadlights.cancelSettingsRollbacks();
        mSupported = false;
        mHeadlights.unpublish();
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureCommon.HeadlightsState.UID) {
            ArsdkFeatureCommon.HeadlightsState.decode(command, mHeadlightsStateCallback);
        }
    }

    /** Callbacks called when a command of the feature ArsdkFeatureCommon.HeadlightsState is decoded. */
    private final ArsdkFeatureCommon.HeadlightsState.Callback mHeadlightsStateCallback =
            new ArsdkFeatureCommon.HeadlightsState.Callback() {

                @Override
                public void onIntensityChanged(int left, int right) {
                    mHeadlights.updateIntensities(left, right);
                    if (!mSupported) {
                        // First event: capability confirmed — publish the peripheral.
                        mSupported = true;
                        mHeadlights.publish();
                    } else {
                        mHeadlights.notifyUpdated();
                    }
                }
            };

    /** Backend of HeadlightsCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final HeadlightsCore.Backend mBackend =
            (left, right) -> sendCommand(ArsdkFeatureCommon.Headlights.encodeIntensity(left, right));
}
