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
import androidx.annotation.Nullable;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.device.peripheral.MiniatureUsbAccessory;
import com.parrot.drone.groundsdk.internal.device.peripheral.MiniatureUsbAccessoryCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureGeneric;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

/**
 * MiniatureUsbAccessory peripheral controller for Mambo (Miniature family) drones.
 * <p>
 * The peripheral is published event-gated: it is only exposed to the application after the drone
 * reports a claw or gun accessory via {@code UsbAccessoryState.ClawState} or
 * {@code UsbAccessoryState.GunState}, and unpublished when the drone reports an empty or removed
 * accessory list.
 */
public final class MiniatureUsbAccessoryPeripheral extends DronePeripheralController {

    /** The peripheral exposed to the application. */
    @NonNull
    private final MiniatureUsbAccessoryCore mAccessory;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public MiniatureUsbAccessoryPeripheral(@NonNull DroneController droneController) {
        super(droneController);
        mAccessory = new MiniatureUsbAccessoryCore(mComponentStore, mBackend);
    }

    @Override
    protected void onConnected() {
        // publish is deferred until the drone reports an attached accessory via state events
    }

    @Override
    protected void onDisconnected() {
        mAccessory.unpublish();
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureMinidrone.UsbAccessoryState.UID) {
            ArsdkFeatureMinidrone.UsbAccessoryState.decode(command, mUsbAccessoryStateCallback);
        }
    }

    /** Callbacks called when a command of the feature ArsdkFeatureMinidrone.UsbAccessoryState is decoded. */
    private final ArsdkFeatureMinidrone.UsbAccessoryState.Callback mUsbAccessoryStateCallback =
            new ArsdkFeatureMinidrone.UsbAccessoryState.Callback() {

                @Override
                public void onClawState(int id,
                                        @Nullable ArsdkFeatureMinidrone.UsbaccessorystateClawstateState state,
                                        int listFlags) {
                    if (ArsdkFeatureGeneric.ListFlags.EMPTY.inBitField(listFlags)
                            || ArsdkFeatureGeneric.ListFlags.REMOVE.inBitField(listFlags)) {
                        mAccessory.unpublish();
                        return;
                    }
                    if (state == null) {
                        return;
                    }
                    mAccessory.updateAccessory(MiniatureUsbAccessory.AccessoryType.CLAW, id)
                              .updateClawState(clawStateFrom(state))
                              .notifyUpdated();
                    mAccessory.publish();
                }

                @Override
                public void onGunState(int id,
                                       @Nullable ArsdkFeatureMinidrone.UsbaccessorystateGunstateState state,
                                       int listFlags) {
                    if (ArsdkFeatureGeneric.ListFlags.EMPTY.inBitField(listFlags)
                            || ArsdkFeatureGeneric.ListFlags.REMOVE.inBitField(listFlags)) {
                        mAccessory.unpublish();
                        return;
                    }
                    if (state == null) {
                        return;
                    }
                    mAccessory.updateAccessory(MiniatureUsbAccessory.AccessoryType.GUN, id)
                              .updateGunState(gunStateFrom(state))
                              .notifyUpdated();
                    mAccessory.publish();
                }
            };

    /** Backend of MiniatureUsbAccessoryCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final MiniatureUsbAccessoryCore.Backend mBackend = new MiniatureUsbAccessoryCore.Backend() {

        @Override
        public boolean openClaw(int accessoryId) {
            return sendCommand(ArsdkFeatureMinidrone.UsbAccessory.encodeClawControl(
                    accessoryId, ArsdkFeatureMinidrone.UsbaccessoryClawcontrolAction.OPEN));
        }

        @Override
        public boolean closeClaw(int accessoryId) {
            return sendCommand(ArsdkFeatureMinidrone.UsbAccessory.encodeClawControl(
                    accessoryId, ArsdkFeatureMinidrone.UsbaccessoryClawcontrolAction.CLOSE));
        }

        @Override
        public boolean fireGun(int accessoryId) {
            return sendCommand(ArsdkFeatureMinidrone.UsbAccessory.encodeGunControl(
                    accessoryId, ArsdkFeatureMinidrone.UsbaccessoryGuncontrolAction.FIRE));
        }
    };

    /**
     * Converts an ARSDK claw state enum value to the public API enum.
     *
     * @param state ARSDK claw state
     *
     * @return corresponding public API claw state
     */
    @NonNull
    private static MiniatureUsbAccessory.ClawState clawStateFrom(
            @NonNull ArsdkFeatureMinidrone.UsbaccessorystateClawstateState state) {
        switch (state) {
            case OPENED:  return MiniatureUsbAccessory.ClawState.OPENED;
            case OPENING: return MiniatureUsbAccessory.ClawState.OPENING;
            case CLOSED:  return MiniatureUsbAccessory.ClawState.CLOSED;
            case CLOSING: return MiniatureUsbAccessory.ClawState.CLOSING;
            default:      return MiniatureUsbAccessory.ClawState.OPENED;
        }
    }

    /**
     * Converts an ARSDK gun state enum value to the public API enum.
     *
     * @param state ARSDK gun state
     *
     * @return corresponding public API gun state
     */
    @NonNull
    private static MiniatureUsbAccessory.GunState gunStateFrom(
            @NonNull ArsdkFeatureMinidrone.UsbaccessorystateGunstateState state) {
        switch (state) {
            case READY: return MiniatureUsbAccessory.GunState.READY;
            case BUSY:  return MiniatureUsbAccessory.GunState.BUSY;
            default:    return MiniatureUsbAccessory.GunState.READY;
        }
    }
}
