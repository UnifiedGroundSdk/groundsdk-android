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

import com.parrot.drone.groundsdk.device.peripheral.FixedWingFlightTuning;
import com.parrot.drone.groundsdk.device.peripheral.Peripheral;
import com.parrot.drone.groundsdk.internal.component.ComponentDescriptor;
import com.parrot.drone.groundsdk.internal.component.ComponentStore;
import com.parrot.drone.groundsdk.internal.component.SingletonComponentCore;
import com.parrot.drone.groundsdk.internal.value.DoubleSettingCore;
import com.parrot.drone.groundsdk.internal.value.EnumSettingCore;
import com.parrot.drone.groundsdk.internal.value.IntSettingCore;
import com.parrot.drone.groundsdk.internal.value.SettingController;

/** Core class for FixedWingFlightTuning. */
public final class FixedWingFlightTuningCore extends SingletonComponentCore implements FixedWingFlightTuning {

    /** Description of FixedWingFlightTuning. */
    static final ComponentDescriptor<Peripheral, FixedWingFlightTuning> DESC =
            ComponentDescriptor.of(FixedWingFlightTuning.class);

    /** Engine-specific backend for the circling settings group. */
    public interface CirclingBackend {

        /**
         * Sets the default circling direction.
         *
         * @param direction circling direction to set
         *
         * @return {@code true} if the value could successfully be set or sent to the device, {@code false} otherwise
         */
        boolean setCirclingDirection(@NonNull CirclingDirection direction);

        /**
         * Sets the minimum circling altitude.
         *
         * @param altitude circling altitude in metres to set
         *
         * @return {@code true} if the value could successfully be set or sent to the device, {@code false} otherwise
         */
        boolean setCirclingAltitude(int altitude);
    }

    /** Engine-specific backend for the autonomous-flight maxima settings group. */
    public interface AutonomousFlightBackend {

        /**
         * Sets the autonomous-flight maximum horizontal speed.
         *
         * @param speed maximum horizontal speed in m/s to set
         *
         * @return {@code true} if the value could successfully be set or sent to the device, {@code false} otherwise
         */
        boolean setAutonomousFlightMaxHorizontalSpeed(double speed);

        /**
         * Sets the autonomous-flight maximum vertical speed.
         *
         * @param speed maximum vertical speed in m/s to set
         *
         * @return {@code true} if the value could successfully be set or sent to the device, {@code false} otherwise
         */
        boolean setAutonomousFlightMaxVerticalSpeed(double speed);

        /**
         * Sets the autonomous-flight maximum horizontal acceleration.
         *
         * @param acceleration maximum horizontal acceleration in m/s² to set
         *
         * @return {@code true} if the value could successfully be set or sent to the device, {@code false} otherwise
         */
        boolean setAutonomousFlightMaxHorizontalAcceleration(double acceleration);

        /**
         * Sets the autonomous-flight maximum vertical acceleration.
         *
         * @param acceleration maximum vertical acceleration in m/s² to set
         *
         * @return {@code true} if the value could successfully be set or sent to the device, {@code false} otherwise
         */
        boolean setAutonomousFlightMaxVerticalAcceleration(double acceleration);

        /**
         * Sets the autonomous-flight maximum rotation speed.
         *
         * @param speed maximum yaw rotation speed in deg/s to set
         *
         * @return {@code true} if the value could successfully be set or sent to the device, {@code false} otherwise
         */
        boolean setAutonomousFlightMaxRotationSpeed(double speed);
    }

    /** Circling direction setting. */
    @NonNull
    private final EnumSettingCore<CirclingDirection> mCirclingDirection;

    /** Circling altitude setting. */
    @NonNull
    private final IntSettingCore mCirclingAltitude;

    /** Autonomous-flight maximum horizontal speed setting. */
    @NonNull
    private final DoubleSettingCore mAutonomousFlightMaxHorizontalSpeed;

    /** Autonomous-flight maximum vertical speed setting. */
    @NonNull
    private final DoubleSettingCore mAutonomousFlightMaxVerticalSpeed;

    /** Autonomous-flight maximum horizontal acceleration setting. */
    @NonNull
    private final DoubleSettingCore mAutonomousFlightMaxHorizontalAcceleration;

    /** Autonomous-flight maximum vertical acceleration setting. */
    @NonNull
    private final DoubleSettingCore mAutonomousFlightMaxVerticalAcceleration;

    /** Autonomous-flight maximum rotation speed setting. */
    @NonNull
    private final DoubleSettingCore mAutonomousFlightMaxRotationSpeed;

    /**
     * Constructor.
     *
     * @param peripheralStore   store where this peripheral belongs
     * @param circlingBackend   backend used to forward circling setting changes to the engine
     * @param autonomousBackend backend used to forward autonomous-flight setting changes to the engine
     */
    public FixedWingFlightTuningCore(@NonNull ComponentStore<Peripheral> peripheralStore,
                                     @NonNull CirclingBackend circlingBackend,
                                     @NonNull AutonomousFlightBackend autonomousBackend) {
        super(DESC, peripheralStore);
        mCirclingDirection = new EnumSettingCore<>(CirclingDirection.CW,
                new SettingController(this::onSettingChange),
                circlingBackend::setCirclingDirection);
        mCirclingAltitude = new IntSettingCore(
                new SettingController(this::onSettingChange),
                circlingBackend::setCirclingAltitude);
        mAutonomousFlightMaxHorizontalSpeed = new DoubleSettingCore(
                new SettingController(this::onSettingChange),
                autonomousBackend::setAutonomousFlightMaxHorizontalSpeed);
        mAutonomousFlightMaxVerticalSpeed = new DoubleSettingCore(
                new SettingController(this::onSettingChange),
                autonomousBackend::setAutonomousFlightMaxVerticalSpeed);
        mAutonomousFlightMaxHorizontalAcceleration = new DoubleSettingCore(
                new SettingController(this::onSettingChange),
                autonomousBackend::setAutonomousFlightMaxHorizontalAcceleration);
        mAutonomousFlightMaxVerticalAcceleration = new DoubleSettingCore(
                new SettingController(this::onSettingChange),
                autonomousBackend::setAutonomousFlightMaxVerticalAcceleration);
        mAutonomousFlightMaxRotationSpeed = new DoubleSettingCore(
                new SettingController(this::onSettingChange),
                autonomousBackend::setAutonomousFlightMaxRotationSpeed);
    }

    @Override
    public void unpublish() {
        super.unpublish();
        cancelSettingsRollbacks();
    }

    @NonNull
    @Override
    public EnumSettingCore<CirclingDirection> circlingDirection() {
        return mCirclingDirection;
    }

    @NonNull
    @Override
    public IntSettingCore circlingAltitude() {
        return mCirclingAltitude;
    }

    @NonNull
    @Override
    public DoubleSettingCore autonomousFlightMaxHorizontalSpeed() {
        return mAutonomousFlightMaxHorizontalSpeed;
    }

    @NonNull
    @Override
    public DoubleSettingCore autonomousFlightMaxVerticalSpeed() {
        return mAutonomousFlightMaxVerticalSpeed;
    }

    @NonNull
    @Override
    public DoubleSettingCore autonomousFlightMaxHorizontalAcceleration() {
        return mAutonomousFlightMaxHorizontalAcceleration;
    }

    @NonNull
    @Override
    public DoubleSettingCore autonomousFlightMaxVerticalAcceleration() {
        return mAutonomousFlightMaxVerticalAcceleration;
    }

    @NonNull
    @Override
    public DoubleSettingCore autonomousFlightMaxRotationSpeed() {
        return mAutonomousFlightMaxRotationSpeed;
    }

    /**
     * Cancels all pending settings rollbacks.
     *
     * @return {@code this}, to allow chained calls
     */
    @NonNull
    public FixedWingFlightTuningCore cancelSettingsRollbacks() {
        mCirclingDirection.cancelRollback();
        mCirclingAltitude.cancelRollback();
        mAutonomousFlightMaxHorizontalSpeed.cancelRollback();
        mAutonomousFlightMaxVerticalSpeed.cancelRollback();
        mAutonomousFlightMaxHorizontalAcceleration.cancelRollback();
        mAutonomousFlightMaxVerticalAcceleration.cancelRollback();
        mAutonomousFlightMaxRotationSpeed.cancelRollback();
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
