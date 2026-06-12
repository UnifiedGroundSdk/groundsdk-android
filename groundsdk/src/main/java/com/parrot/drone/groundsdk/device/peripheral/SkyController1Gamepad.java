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

import com.parrot.drone.groundsdk.device.Drone;
import com.parrot.drone.groundsdk.device.RemoteControl;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.AxisInterpolator;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.AxisEvent;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.AxisMappingEntry;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.ButtonEvent;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.ButtonsMappingEntry;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.MappingEntry;

import java.util.Map;
import java.util.Set;

/**
 * Gamepad peripheral interface for {@link RemoteControl.Model#SKY_CONTROLLER SkyController 1} devices.
 * <p>
 * This peripheral allows configuring axis-to-action and button-to-action mappings stored on the remote control.
 * <p>
 * SkyController 1 uses the legacy {@code skyctrl} protocol and does <strong>not</strong> support:
 * <ul>
 *   <li>Input grabbing — there is no grab command in the skyctrl protocol.
 *       {@link #setButtonEventListener} and {@link #setAxisEventListener} are retained for API symmetry
 *       but will never fire in practice.</li>
 *   <li>Volatile mapping — not present in the skyctrl protocol.</li>
 *   <li>Axis reversal — not exposed by the skyctrl protocol.</li>
 *   <li>Multiple supported drone models — SC1 reports {@link Drone.Model#UNKNOWN} for all mappings.</li>
 * </ul>
 * Axis filters (interpolation curves) <strong>are</strong> supported via the skyctrl AxisFilters protocol,
 * exposed through the same {@link AxisInterpolator} enum as SC2/SC3 for API consistency.
 * Each {@link AxisInterpolator} value maps to a fixed {@code ARXF;CPx;CPy;} or {@code ARMF;} builder
 * string sent to the firmware (see {@link #setAxisInterpolator} for details).
 * <p>
 * Obtain this peripheral from a SkyController 1 remote control:
 * <pre>{@code remoteControl.getPeripheral(SkyController1Gamepad.class)}</pre>
 */
public interface SkyController1Gamepad extends Peripheral {

    /**
     * A physical button input on the SkyController 1 gamepad.
     * <p>
     * These correspond to Android keycode integers reported by the GamepadInfosState protocol.
     */
    enum Button {

        /** Left mini-joystick click (keycode 96). The small clickable joystick in the top-left corner. */
        LEFT_MINI_JS,

        /** Return-home button (keycode 97). */
        RETURN_HOME,

        /** Record button (keycode 98). */
        RECORD,

        /** Emergency cut-off button (keycode 99). */
        EMERGENCY,

        /** Right mini-joystick click (keycode 100). The small clickable joystick in the top-right corner. */
        RIGHT_MINI_JS,

        /** Takeoff / land button (keycode 101). */
        TAKEOFF_LAND,

        /** Home / menu button (keycode 103). */
        HOME,

        /** Back button (keycode 104). */
        BACK,
    }

    /**
     * A physical axis input on the SkyController 1 gamepad.
     * <p>
     * These correspond to axis id integers reported by the AxisMappingsState protocol.
     * <p>
     * Naming convention: "TOP_LEFT" and "TOP_RIGHT" refer to the small clickable mini-joysticks in
     * the top corners of the device. "LEFT_STICK" and "RIGHT_STICK" refer to the full-travel main
     * joysticks on the lower portion of the device.
     */
    enum Axis {

        /**
         * Top-left mini-joystick horizontal axis (axis id 0).
         * The small clickable joystick in the top-left corner of the device.
         */
        TOP_LEFT_HORIZONTAL,

        /**
         * Top-left mini-joystick vertical axis (axis id 1).
         * The small clickable joystick in the top-left corner of the device.
         */
        TOP_LEFT_VERTICAL,

        /**
         * Right main stick horizontal axis (axis id 2).
         * The full-travel joystick on the right side of the device.
         */
        RIGHT_STICK_HORIZONTAL,

        /**
         * Right main stick vertical axis (axis id 3).
         * The full-travel joystick on the right side of the device.
         */
        RIGHT_STICK_VERTICAL,

        /**
         * Left main stick horizontal axis (axis id 4).
         * The full-travel joystick on the lower-left of the device.
         */
        LEFT_STICK_HORIZONTAL,

        /**
         * Left main stick vertical axis (axis id 5).
         * The full-travel joystick on the lower-left of the device.
         */
        LEFT_STICK_VERTICAL,

        /**
         * Top-right mini-joystick horizontal axis (axis id 6).
         * The small clickable joystick in the top-right corner of the device.
         */
        TOP_RIGHT_HORIZONTAL,

        /**
         * Top-right mini-joystick vertical axis (axis id 7).
         * The small clickable joystick in the top-right corner of the device.
         */
        TOP_RIGHT_VERTICAL,
    }

    /**
     * Sets the application listener for button events.
     * <p>
     * <strong>Note:</strong> SkyController 1 does not support input grab — there is no grab command
     * in the skyctrl protocol (confirmed by exhaustive inspection of skyctrl.xml). This listener will
     * therefore never be called by the current SC1 engine. It is retained for API symmetry with SC2/SC3
     * and for future-proofing against firmware updates that may add grab support.
     *
     * @param listener the listener to register, or {@code null} to unregister
     *
     * @return this instance, for call chaining
     */
    @NonNull
    SkyController1Gamepad setButtonEventListener(@Nullable ButtonEvent.Listener listener);

    /**
     * Sets the application listener for axis events.
     * <p>
     * <strong>Note:</strong> SkyController 1 does not support input grab. This listener will never be
     * called by the current SC1 engine. Retained for API symmetry and future-proofing only.
     *
     * @param listener the listener to register, or {@code null} to unregister
     *
     * @return this instance, for call chaining
     */
    @NonNull
    SkyController1Gamepad setAxisEventListener(@Nullable AxisEvent.Listener listener);

    /**
     * Gets the drone models supported by this remote control.
     * <p>
     * For SkyController 1, this always returns a singleton set containing {@link Drone.Model#UNKNOWN},
     * because the SC1 firmware does not distinguish drone models in its mapping protocol.
     *
     * @return set of supported drone models
     */
    @NonNull
    Set<Drone.Model> getSupportedDroneModels();

    /**
     * Gets the currently active drone model.
     * <p>
     * For SkyController 1, this is always {@link Drone.Model#UNKNOWN}.
     *
     * @return currently active drone model
     */
    @Nullable
    Drone.Model getActiveDroneModel();

    /**
     * Gets the current mapping for a drone model.
     * <p>
     * Use {@link Drone.Model#UNKNOWN} for SkyController 1. Returns {@code null} if the model is not
     * in the set returned by {@link #getSupportedDroneModels()}.
     *
     * @param droneModel the drone model (use {@link Drone.Model#UNKNOWN} for SC1)
     *
     * @return set of current mapping entries, or {@code null} if model not supported
     */
    @Nullable
    Set<MappingEntry> getMapping(@NonNull Drone.Model droneModel);

    /**
     * Registers a mapping entry on the remote control.
     * <p>
     * Sends the corresponding {@code skyctrl.ButtonMappings.SetButtonMapping} or
     * {@code skyctrl.AxisMappings.SetAxisMapping} command to the firmware.
     *
     * @param mappingEntry mapping entry to register
     *
     * @see ButtonsMappingEntry
     * @see AxisMappingEntry
     */
    void registerMappingEntry(@NonNull MappingEntry mappingEntry);

    /**
     * Unregisters a mapping entry, removing its action on the remote control.
     * <p>
     * Sends a {@code SetButtonMapping} or {@code SetAxisMapping} command with {@code "No Action"} as
     * the mapping uid, effectively clearing the binding for that input.
     *
     * @param mappingEntry mapping entry to unregister
     */
    void unregisterMappingEntry(@NonNull MappingEntry mappingEntry);

    /**
     * Resets all mappings to their factory defaults.
     * <p>
     * Sends {@code skyctrl.ButtonMappings.DefaultButtonMapping} and
     * {@code skyctrl.AxisMappings.DefaultAxisMapping} to the remote control.
     */
    void resetDefaultMappings();

    /**
     * Sets the interpolation formula applied to an axis.
     * <p>
     * Translates the {@link AxisInterpolator} value to a fixed {@code ARMF} or {@code ARXF} builder
     * string and sends {@code skyctrl.AxisFilters.SetAxisFilter} to the firmware. The provisional
     * CPx/CPy values below are derived from the skyctrl protocol spec (CPx + CPy ≈ 1, CPx &gt; CPy)
     * and require bench confirmation against actual SC1 hardware before final release:
     * <ul>
     *   <li>{@link AxisInterpolator#LINEAR} &rarr; {@code ARMF;} (purely linear, firmware default)</li>
     *   <li>{@link AxisInterpolator#LIGHT_EXPONENTIAL} &rarr; {@code ARXF;0.65;0.35;}</li>
     *   <li>{@link AxisInterpolator#MEDIUM_EXPONENTIAL} &rarr; {@code ARXF;0.75;0.25;}</li>
     *   <li>{@link AxisInterpolator#STRONG_EXPONENTIAL} &rarr; {@code ARXF;0.85;0.15;}</li>
     *   <li>{@link AxisInterpolator#STRONGEST_EXPONENTIAL} &rarr; {@code ARXF;0.90;0.10;}</li>
     * </ul>
     *
     * @param axis         the axis to configure
     * @param interpolator the interpolator to apply
     */
    void setAxisInterpolator(@NonNull Axis axis, @NonNull AxisInterpolator interpolator);

    /**
     * Gets the interpolation formulas currently applied to all axes.
     * <p>
     * Reflects the last-received {@code skyctrl.AxisFiltersState.currentAxisFilters} callbacks.
     * Inbound {@code ARMF;} strings map to {@link AxisInterpolator#LINEAR}; inbound
     * {@code ARXF;CPx;CPy;} strings map to the nearest named preset by CPx threshold.
     *
     * @return map of axis to interpolator; may be empty if no filter state has been received yet
     */
    @NonNull
    Map<Axis, AxisInterpolator> getAxisInterpolators();
}
