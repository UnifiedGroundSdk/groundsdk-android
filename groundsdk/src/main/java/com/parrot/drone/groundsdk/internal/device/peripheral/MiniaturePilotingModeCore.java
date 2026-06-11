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

import com.parrot.drone.groundsdk.device.peripheral.MiniaturePilotingMode;
import com.parrot.drone.groundsdk.device.peripheral.Peripheral;
import com.parrot.drone.groundsdk.internal.component.ComponentDescriptor;
import com.parrot.drone.groundsdk.internal.component.ComponentStore;
import com.parrot.drone.groundsdk.internal.component.SingletonComponentCore;
import com.parrot.drone.groundsdk.internal.value.EnumSettingCore;
import com.parrot.drone.groundsdk.internal.value.SettingController;

/** Core class for MiniaturePilotingMode. */
public final class MiniaturePilotingModeCore extends SingletonComponentCore implements MiniaturePilotingMode {

    /** Description of MiniaturePilotingMode. */
    static final ComponentDescriptor<Peripheral, MiniaturePilotingMode> DESC =
            ComponentDescriptor.of(MiniaturePilotingMode.class);

    /** Engine-specific backend for MiniaturePilotingMode. */
    public interface Backend {

        /**
         * Sets the preferred piloting mode.
         *
         * @param mode preferred piloting mode to set
         *
         * @return {@code true} if the value could successfully be set or sent to the device, {@code false} otherwise
         */
        boolean setPreferredMode(@NonNull Mode mode);

        /**
         * Sends the momentary toggle piloting mode command to the device.
         */
        void toggleMode();
    }

    /** Backend used to forward actions to the engine. */
    @NonNull
    private final Backend mBackend;

    /** Preferred piloting mode setting. */
    @NonNull
    private final EnumSettingCore<Mode> mPreferredModeSetting;

    /**
     * Constructor.
     *
     * @param peripheralStore store where this peripheral belongs
     * @param backend         backend used to forward actions to the engine
     */
    public MiniaturePilotingModeCore(@NonNull ComponentStore<Peripheral> peripheralStore,
                                     @NonNull Backend backend) {
        super(DESC, peripheralStore);
        mBackend = backend;
        mPreferredModeSetting = new EnumSettingCore<>(Mode.EASY,
                new SettingController(this::onSettingChange),
                backend::setPreferredMode);
    }

    @NonNull
    @Override
    public EnumSettingCore<Mode> preferredMode() {
        return mPreferredModeSetting;
    }

    @Override
    public void toggleMode() {
        mBackend.toggleMode();
    }

    /**
     * Cancels all pending settings rollbacks.
     *
     * @return {@code this}, to allow chained calls
     */
    @NonNull
    public MiniaturePilotingModeCore cancelSettingsRollbacks() {
        mPreferredModeSetting.cancelRollback();
        return this;
    }

    /**
     * Notified when a user setting changes.
     * <p>
     * In case the change originates from the user modifying the setting value, updates the store to
     * show the setting is updating.
     *
     * @param fromUser {@code true} if the change originates from the user, otherwise {@code false}
     */
    private void onSettingChange(boolean fromUser) {
        mChanged = true;
        if (fromUser) {
            notifyUpdated();
        }
    }
}
