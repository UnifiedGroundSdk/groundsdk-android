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

package com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1;

import androidx.annotation.NonNull;

import com.parrot.drone.groundsdk.device.peripheral.SkyController1Gamepad;

/**
 * An event that may be produced by a gamepad {@link SkyController1Gamepad.Button button} input.
 * <p>
 * The corresponding input has a button behavior, i.e. it can be either {@link State#PRESSED pressed} or
 * {@link State#RELEASED released}, and an event is sent each time that state changes along with the
 * current state.
 * <p>
 * <strong>Note:</strong> SkyController 1 does not support input grab — there is no grab command in the
 * skyctrl protocol. In practice this listener will never be called by the current SC1 engine. These
 * types are retained for API symmetry with SC2/SC3 and for future firmware compatibility.
 */
public enum ButtonEvent {

    /**
     * Event sent when the {@link SkyController1Gamepad.Button#LEFT_MINI_JS left mini-joystick} is
     * clicked or released.
     */
    LEFT_MINI_JS,

    /**
     * Event sent when the {@link SkyController1Gamepad.Button#RETURN_HOME return-home button} is
     * pressed or released.
     */
    RETURN_HOME,

    /**
     * Event sent when the {@link SkyController1Gamepad.Button#RECORD record button} is pressed or
     * released.
     */
    RECORD,

    /**
     * Event sent when the {@link SkyController1Gamepad.Button#EMERGENCY emergency cut-off button} is
     * pressed or released.
     */
    EMERGENCY,

    /**
     * Event sent when the {@link SkyController1Gamepad.Button#RIGHT_MINI_JS right mini-joystick} is
     * clicked or released.
     */
    RIGHT_MINI_JS,

    /**
     * Event sent when the {@link SkyController1Gamepad.Button#TAKEOFF_LAND takeoff/land button} is
     * pressed or released.
     */
    TAKEOFF_LAND,

    /**
     * Event sent when the {@link SkyController1Gamepad.Button#HOME home/menu button} is pressed or
     * released.
     */
    HOME,

    /**
     * Event sent when the {@link SkyController1Gamepad.Button#BACK back button} is pressed or
     * released.
     */
    BACK;

    /** State of the corresponding button. */
    public enum State {

        /** Button is pressed. */
        PRESSED,

        /** Button is released. */
        RELEASED
    }

    /**
     * Receives button events sent from the remote control when a corresponding input is grabbed.
     */
    public interface Listener {

        /**
         * Called back when a button event is received.
         *
         * @param event received button event
         * @param state current button state
         */
        void onButtonEvent(@NonNull ButtonEvent event, @NonNull State state);
    }
}
