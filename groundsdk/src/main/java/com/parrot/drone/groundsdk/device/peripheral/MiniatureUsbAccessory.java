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

package com.parrot.drone.groundsdk.device.peripheral;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parrot.drone.groundsdk.Ref;

/**
 * MiniatureUsbAccessory peripheral interface.
 * <p>
 * Exposes control and state for USB accessories (claw and gun) supported by the Parrot Mambo
 * minidrone. The peripheral is only published while the drone reports an accessory attached.
 * <p>
 * This peripheral can be obtained from a {@link Provider peripheral providing device} (such as a
 * drone) using:
 * <pre>{@code device.getPeripheral(MiniatureUsbAccessory.class)}</pre>
 *
 * @see Provider#getPeripheral(Class)
 * @see Provider#getPeripheral(Class, Ref.Observer)
 */
public interface MiniatureUsbAccessory extends Peripheral {

    /**
     * Type of USB accessory currently attached.
     */
    enum AccessoryType {
        /** A claw (gripper) accessory. */
        CLAW,
        /** A gun (launcher) accessory. */
        GUN
    }

    /**
     * State of the claw accessory.
     */
    enum ClawState {
        /** Claw is fully opened. */
        OPENED,
        /** Claw open in progress. */
        OPENING,
        /** Claw is fully closed. */
        CLOSED,
        /** Claw close in progress. */
        CLOSING
    }

    /**
     * State of the gun accessory.
     */
    enum GunState {
        /** Gun is ready to fire. */
        READY,
        /** Gun is busy (not ready to fire). */
        BUSY
    }

    /**
     * Returns the type of the currently attached accessory.
     *
     * @return the attached accessory type, or {@code null} if no accessory type is known yet
     */
    @Nullable
    AccessoryType getAccessoryType();

    /**
     * Returns the current state of the claw accessory.
     * <p>
     * Only meaningful when {@link #getAccessoryType()} returns {@link AccessoryType#CLAW}.
     *
     * @return the current claw state, or {@code null} if not yet received from the drone
     */
    @Nullable
    ClawState getClawState();

    /**
     * Returns the current state of the gun accessory.
     * <p>
     * Only meaningful when {@link #getAccessoryType()} returns {@link AccessoryType#GUN}.
     *
     * @return the current gun state, or {@code null} if not yet received from the drone
     */
    @Nullable
    GunState getGunState();

    /**
     * Sends a command to open the claw.
     * <p>
     * No-op if no claw accessory is attached.
     *
     * @return {@code true} if the command could be sent, otherwise {@code false}
     */
    boolean openClaw();

    /**
     * Sends a command to close the claw.
     * <p>
     * No-op if no claw accessory is attached.
     *
     * @return {@code true} if the command could be sent, otherwise {@code false}
     */
    boolean closeClaw();

    /**
     * Sends a command to fire the gun.
     * <p>
     * No-op if no gun accessory is attached.
     *
     * @return {@code true} if the command could be sent, otherwise {@code false}
     */
    boolean fireGun();
}
