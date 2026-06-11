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

import com.parrot.drone.groundsdk.device.peripheral.Headlights;
import com.parrot.drone.groundsdk.device.peripheral.Peripheral;
import com.parrot.drone.groundsdk.internal.component.ComponentDescriptor;
import com.parrot.drone.groundsdk.internal.component.ComponentStore;
import com.parrot.drone.groundsdk.internal.component.SingletonComponentCore;
import com.parrot.drone.groundsdk.internal.value.IntSettingCore;
import com.parrot.drone.groundsdk.internal.value.SettingController;
import com.parrot.drone.groundsdk.value.IntegerRange;

/** Core class for the Headlights peripheral. */
public class HeadlightsCore extends SingletonComponentCore implements Headlights {

    /** Description of Headlights. */
    private static final ComponentDescriptor<Peripheral, Headlights> DESC =
            ComponentDescriptor.of(Headlights.class);

    /** Engine-specific backend for Headlights. */
    public interface Backend {

        /**
         * Sends updated LED intensities to the device.
         *
         * @param left  left LED intensity value (0–255)
         * @param right right LED intensity value (0–255)
         *
         * @return {@code true} if the command was sent to the device, otherwise {@code false}
         */
        boolean setIntensities(int left, int right);
    }

    /** Intensity range: 0–255 (u8 protocol values). */
    private static final IntegerRange INTENSITY_RANGE = IntegerRange.of(0, 255);

    /** Engine backend. */
    @NonNull
    private final Backend mBackend;

    /** Left headlight intensity setting. */
    @NonNull
    private final IntSettingCore mLeftIntensity;

    /** Right headlight intensity setting. */
    @NonNull
    private final IntSettingCore mRightIntensity;

    /**
     * Constructor.
     *
     * @param peripheralStore store where this peripheral belongs
     * @param backend         backend used to forward actions to the engine
     */
    public HeadlightsCore(@NonNull ComponentStore<Peripheral> peripheralStore, @NonNull Backend backend) {
        super(DESC, peripheralStore);
        mBackend = backend;
        mLeftIntensity = new IntSettingCore(new SettingController(this::onSettingChange),
                value -> sendIntensities(value, null));
        mLeftIntensity.updateBounds(INTENSITY_RANGE);

        mRightIntensity = new IntSettingCore(new SettingController(this::onSettingChange),
                value -> sendIntensities(null, value));
        mRightIntensity.updateBounds(INTENSITY_RANGE);
    }

    /**
     * Sends intensities to the device, substituting the current value of whichever side is not changing.
     *
     * @param left  new left intensity, or {@code null} to keep the current left value
     * @param right new right intensity, or {@code null} to keep the current right value
     *
     * @return {@code true} if the command was sent to the device, otherwise {@code false}
     */
    private boolean sendIntensities(@Nullable Integer left, @Nullable Integer right) {
        return mBackend.setIntensities(left != null ? left : mLeftIntensity.getValue(),
                right != null ? right : mRightIntensity.getValue());
    }

    @Override
    public void unpublish() {
        super.unpublish();
        cancelSettingsRollbacks();
    }

    @NonNull
    @Override
    public IntSettingCore leftIntensity() {
        return mLeftIntensity;
    }

    @NonNull
    @Override
    public IntSettingCore rightIntensity() {
        return mRightIntensity;
    }

    /**
     * Updates both LED intensities from a device event.
     * <p>
     * Changes are not notified until {@link #notifyUpdated()} is called.
     *
     * @param left  left LED intensity received from the device
     * @param right right LED intensity received from the device
     *
     * @return {@code this}, to allow chained calls
     */
    @NonNull
    public HeadlightsCore updateIntensities(int left, int right) {
        mLeftIntensity.updateValue(left);
        mRightIntensity.updateValue(right);
        return this;
    }

    /**
     * Cancels all pending settings rollbacks.
     *
     * @return {@code this}, to allow chained calls
     */
    @NonNull
    public HeadlightsCore cancelSettingsRollbacks() {
        mLeftIntensity.cancelRollback();
        mRightIntensity.cancelRollback();
        return this;
    }

    /**
     * Notified when a user setting changes.
     * <p>
     * In case the change originates from the user modifying the setting value, triggers a
     * {@link #notifyUpdated()} so the peripheral change is propagated immediately.
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
