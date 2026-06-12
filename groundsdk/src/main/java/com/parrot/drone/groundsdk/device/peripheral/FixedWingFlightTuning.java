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

import com.parrot.drone.groundsdk.Ref;
import com.parrot.drone.groundsdk.value.DoubleSetting;
import com.parrot.drone.groundsdk.value.EnumSetting;
import com.parrot.drone.groundsdk.value.IntSetting;

/**
 * FixedWingFlightTuning peripheral interface.
 * <p>
 * Exposes flight-tuning parameters specific to fixed-wing drones (Parrot Disco).
 * Groups two categories of settings:
 * <ul>
 * <li>Circling: default circling direction and minimum circling altitude.</li>
 * <li>Autonomous-flight maxima: the five speed/acceleration/rotation limits used
 *     by autonomous flight manoeuvres (moveBy, flightplan).</li>
 * </ul>
 * <p>
 * This peripheral can be obtained from a {@link Provider peripheral providing device} (such as a drone)
 * using:
 * <br><pre>    {@code device.getPeripheral(FixedWingFlightTuning.class)}</pre>
 *
 * @see Provider#getPeripheral(Class)
 * @see Provider#getPeripheral(Class, Ref.Observer)
 */
public interface FixedWingFlightTuning extends Peripheral {

    /**
     * Default circling direction used when circling mode is entered automatically or when the
     * CIRCLE piloting command is sent with direction {@code default}.
     */
    enum CirclingDirection {

        /** Circling clockwise. */
        CW,

        /** Circling counter-clockwise. */
        CCW
    }

    // ---- Circling settings ----

    /**
     * Gets the default circling direction setting.
     * <p>
     * Persistent: the value is retained across connections and re-sent to the drone on reconnection.
     *
     * @return the circling direction setting
     */
    @NonNull
    EnumSetting<CirclingDirection> circlingDirection();

    /**
     * Gets the minimum circling altitude setting.
     * <p>
     * Value is in metres. The drone will not descend below this altitude while circling.
     * Bounds are provided by the drone and may change when the global max-altitude setting changes.
     * <p>
     * Persistent: the value is retained across connections and re-sent to the drone on reconnection.
     *
     * @return the circling altitude setting
     */
    @NonNull
    IntSetting circlingAltitude();

    // ---- Autonomous-flight maxima ----

    /**
     * Gets the autonomous-flight maximum horizontal speed setting.
     * <p>
     * Value is in m/s. Only effective during autonomous flights (moveBy, flightplan).
     * Bounds are provided by the drone.
     * <p>
     * Persistent: the value is retained across connections and re-sent to the drone on reconnection.
     *
     * @return the autonomous-flight maximum horizontal speed setting
     */
    @NonNull
    DoubleSetting autonomousFlightMaxHorizontalSpeed();

    /**
     * Gets the autonomous-flight maximum vertical speed setting.
     * <p>
     * Value is in m/s. Only effective during autonomous flights (moveBy, flightplan).
     * Bounds are provided by the drone.
     * <p>
     * Persistent: the value is retained across connections and re-sent to the drone on reconnection.
     *
     * @return the autonomous-flight maximum vertical speed setting
     */
    @NonNull
    DoubleSetting autonomousFlightMaxVerticalSpeed();

    /**
     * Gets the autonomous-flight maximum horizontal acceleration setting.
     * <p>
     * Value is in m/s². Only effective during autonomous flights (moveBy, flightplan).
     * Bounds are provided by the drone.
     * <p>
     * Persistent: the value is retained across connections and re-sent to the drone on reconnection.
     *
     * @return the autonomous-flight maximum horizontal acceleration setting
     */
    @NonNull
    DoubleSetting autonomousFlightMaxHorizontalAcceleration();

    /**
     * Gets the autonomous-flight maximum vertical acceleration setting.
     * <p>
     * Value is in m/s². Only effective during autonomous flights (moveBy, flightplan).
     * Bounds are provided by the drone.
     * <p>
     * Persistent: the value is retained across connections and re-sent to the drone on reconnection.
     *
     * @return the autonomous-flight maximum vertical acceleration setting
     */
    @NonNull
    DoubleSetting autonomousFlightMaxVerticalAcceleration();

    /**
     * Gets the autonomous-flight maximum rotation speed setting.
     * <p>
     * Value is in deg/s. Only effective during autonomous flights (moveBy, flightplan).
     * Bounds are provided by the drone.
     * <p>
     * Persistent: the value is retained across connections and re-sent to the drone on reconnection.
     *
     * @return the autonomous-flight maximum rotation speed setting
     */
    @NonNull
    DoubleSetting autonomousFlightMaxRotationSpeed();
}
