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

package com.parrot.drone.groundsdk.internal.device.peripheral;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parrot.drone.groundsdk.device.peripheral.MiniatureUsbAccessory;
import com.parrot.drone.groundsdk.device.peripheral.Peripheral;
import com.parrot.drone.groundsdk.internal.component.ComponentDescriptor;
import com.parrot.drone.groundsdk.internal.component.ComponentStore;
import com.parrot.drone.groundsdk.internal.component.SingletonComponentCore;

/** Core class for MiniatureUsbAccessory. */
public class MiniatureUsbAccessoryCore extends SingletonComponentCore implements MiniatureUsbAccessory {

    /** Description of MiniatureUsbAccessory. */
    static final ComponentDescriptor<Peripheral, MiniatureUsbAccessory> DESC =
            ComponentDescriptor.of(MiniatureUsbAccessory.class);

    /** Engine-specific backend for MiniatureUsbAccessory. */
    public interface Backend {

        /**
         * Sends a command to open the claw accessory.
         *
         * @param accessoryId the ARSDK accessory id to target
         *
         * @return {@code true} if the command could be sent, otherwise {@code false}
         */
        boolean openClaw(int accessoryId);

        /**
         * Sends a command to close the claw accessory.
         *
         * @param accessoryId the ARSDK accessory id to target
         *
         * @return {@code true} if the command could be sent, otherwise {@code false}
         */
        boolean closeClaw(int accessoryId);

        /**
         * Sends a command to fire the gun accessory.
         *
         * @param accessoryId the ARSDK accessory id to target
         *
         * @return {@code true} if the command could be sent, otherwise {@code false}
         */
        boolean fireGun(int accessoryId);
    }

    /** Engine peripheral backend. */
    @NonNull
    private final Backend mBackend;

    /** Type of the currently attached accessory; {@code null} until reported by the drone. */
    @Nullable
    private AccessoryType mAccessoryType;

    /** ARSDK id of the currently attached accessory; -1 until reported by the drone. */
    private int mAccessoryId;

    /** Current claw state; {@code null} until reported by the drone. */
    @Nullable
    private ClawState mClawState;

    /** Current gun state; {@code null} until reported by the drone. */
    @Nullable
    private GunState mGunState;

    /**
     * Constructor.
     *
     * @param peripheralStore store where this peripheral belongs
     * @param backend         backend used to forward actions to the engine
     */
    public MiniatureUsbAccessoryCore(@NonNull ComponentStore<Peripheral> peripheralStore,
                                     @NonNull Backend backend) {
        super(DESC, peripheralStore);
        mBackend = backend;
        mAccessoryId = -1;
    }

    @Override
    @Nullable
    public AccessoryType getAccessoryType() {
        return mAccessoryType;
    }

    @Override
    @Nullable
    public ClawState getClawState() {
        return mClawState;
    }

    @Override
    @Nullable
    public GunState getGunState() {
        return mGunState;
    }

    @Override
    public boolean openClaw() {
        return mAccessoryType == AccessoryType.CLAW && mAccessoryId >= 0 && mBackend.openClaw(mAccessoryId);
    }

    @Override
    public boolean closeClaw() {
        return mAccessoryType == AccessoryType.CLAW && mAccessoryId >= 0 && mBackend.closeClaw(mAccessoryId);
    }

    @Override
    public boolean fireGun() {
        return mAccessoryType == AccessoryType.GUN && mAccessoryId >= 0 && mBackend.fireGun(mAccessoryId);
    }

    /**
     * Updates the attached accessory type and its ARSDK id.
     * <p>
     * Note: changes are not notified until {@link #notifyUpdated()} is called.
     *
     * @param type        accessory type
     * @param accessoryId ARSDK accessory id
     *
     * @return this, to allow call chaining
     */
    @NonNull
    public MiniatureUsbAccessoryCore updateAccessory(@NonNull AccessoryType type, int accessoryId) {
        if (mAccessoryType != type || mAccessoryId != accessoryId) {
            mAccessoryType = type;
            mAccessoryId = accessoryId;
            mChanged = true;
        }
        return this;
    }

    /**
     * Updates the current claw state.
     * <p>
     * Note: changes are not notified until {@link #notifyUpdated()} is called.
     *
     * @param state the new claw state
     *
     * @return this, to allow call chaining
     */
    @NonNull
    public MiniatureUsbAccessoryCore updateClawState(@NonNull ClawState state) {
        if (mClawState != state) {
            mClawState = state;
            mChanged = true;
        }
        return this;
    }

    /**
     * Updates the current gun state.
     * <p>
     * Note: changes are not notified until {@link #notifyUpdated()} is called.
     *
     * @param state the new gun state
     *
     * @return this, to allow call chaining
     */
    @NonNull
    public MiniatureUsbAccessoryCore updateGunState(@NonNull GunState state) {
        if (mGunState != state) {
            mGunState = state;
            mChanged = true;
        }
        return this;
    }
}
