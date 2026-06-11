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

import com.parrot.drone.groundsdk.Ref;
import com.parrot.drone.groundsdk.value.EnumSetting;

import androidx.annotation.NonNull;

/**
 * MiniaturePilotingMode peripheral interface.
 * <p>
 * Exposes the Mambo/Mambo FPV piloting mode controls: a persistent preferred mode setting with three
 * levels (EASY, MEDIUM, DIFFICULT) and a momentary toggle command that switches between easy and the
 * currently preferred mode while the drone is flying.
 * <p>
 * This peripheral can be obtained from a {@link Provider peripheral providing device} (such as a drone)
 * using:
 * <br><pre>    {@code device.getPeripheral(MiniaturePilotingMode.class)}</pre>
 *
 * @see Provider#getPeripheral(Class)
 * @see Provider#getPeripheral(Class, Ref.Observer)
 */
public interface MiniaturePilotingMode extends Peripheral {

    /**
     * Mambo FPV piloting modes.
     * <p>
     * The flight envelope of Mambo FPV is divided into three progressive modes.
     */
    enum Mode {

        /**
         * Easy mode (default).
         * <p>
         * Full horizontal stabilisation via downward camera and accelerometers, and full vertical
         * stabilisation via ultrasound, barometer and vertical accelerometer. The drone holds its
         * position when no piloting commands are sent.
         */
        EASY,

        /**
         * Medium mode.
         * <p>
         * Horizontal stabilisation is disabled. When no piloting command is received, the drone
         * holds 0° tilt instead of 0 m/s horizontal speed, resulting in a slight horizontal drift.
         */
        MEDIUM,

        /**
         * Difficult mode.
         * <p>
         * Both horizontal and vertical stabilisation are disabled. Height control becomes open-loop
         * (acceleration command), resulting in slight horizontal and vertical drift.
         */
        DIFFICULT
    }

    /**
     * Gets the preferred piloting mode setting.
     * <p>
     * This is a persistent setting: the value is retained across connections and sent to the
     * drone upon reconnection.
     *
     * @return the preferred piloting mode setting
     */
    @NonNull
    EnumSetting<Mode> preferredMode();

    /**
     * Sends a momentary toggle command to the drone.
     * <p>
     * Toggles between {@link Mode#EASY easy} mode and the currently preferred mode. This command
     * only takes effect while the drone is flying.
     */
    void toggleMode();
}
