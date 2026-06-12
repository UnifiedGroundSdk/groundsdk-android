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

import com.parrot.drone.groundsdk.device.Drone;
import com.parrot.drone.groundsdk.device.RemoteControl;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.AxisInterpolator;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.AxisMappableAction;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.ButtonsMappableAction;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller2.AxisEvent;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller2.AxisMappingEntry;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller2.ButtonEvent;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller2.ButtonsMappingEntry;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller2.MappingEntry;
import com.parrot.drone.groundsdk.value.OptionalBooleanSetting;

import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Gamepad peripheral interface for {@link RemoteControl.Model#SKY_CONTROLLER_2 SkyController2}
 * and {@link RemoteControl.Model#SKY_CONTROLLER_2P SkyController2+} devices.
 * <p>
 * SC2 and SC2+ share the same hardware button/axis layout and both use the mapper feature
 * (same as SkyController 3). All mapping operations are therefore supported: volatile mapping,
 * grab, axis interpolation, and axis reversal.
 * <p>
 * To start receiving events, {@link Button buttons} and/or {@link Axis axes} must be grabbed
 * and event listeners must be provided.
 * <p>
 * A mapping defines a set of actions that may each be triggered by a specific combination of
 * input events (buttons and/or axes) produced by the remote control.
 * <p>
 * Obtain from a SkyController 2 / 2+ RemoteControl using:
 * <pre>{@code remoteControl.getPeripheral(SkyController2Gamepad.class)}</pre>
 */
public interface SkyController2Gamepad extends Peripheral {

    /**
     * A physical button input on SkyController 2 / 2+.
     * <p>
     * Each button may produce one or more {@link ButtonEvent button events} when grabbed.
     */
    enum Button {

        /**
         * Settings button (mapper MASK_BUTTON_0).
         * Produces {@link ButtonEvent#SETTINGS} when grabbed.
         */
        SETTINGS,

        /**
         * Return-home button (mapper MASK_BUTTON_1).
         * Produces {@link ButtonEvent#RETURN_HOME} when grabbed.
         */
        RETURN_HOME,

        /**
         * Takeoff / land button (mapper MASK_BUTTON_2).
         * Produces {@link ButtonEvent#TAKEOFF_LAND} when grabbed.
         */
        TAKEOFF_LAND,

        /**
         * B button (mapper MASK_BUTTON_3).
         * Produces {@link ButtonEvent#BUTTON_B} when grabbed.
         */
        BUTTON_B,

        /**
         * A button (mapper MASK_BUTTON_4).
         * Produces {@link ButtonEvent#BUTTON_A} when grabbed.
         */
        BUTTON_A,

        /**
         * Rear left button (mapper MASK_BUTTON_5).
         * Produces {@link ButtonEvent#REAR_LEFT_BUTTON} when grabbed.
         * Produces {@link VirtualGamepad.Event#CANCEL} when VirtualGamepad is grabbed.
         */
        REAR_LEFT_BUTTON,

        /**
         * Rear right button (mapper MASK_BUTTON_6).
         * Produces {@link ButtonEvent#REAR_RIGHT_BUTTON} when grabbed.
         * Produces {@link VirtualGamepad.Event#OK} when VirtualGamepad is grabbed.
         */
        REAR_RIGHT_BUTTON,
    }

    /**
     * A physical axis input on SkyController 2 / 2+.
     * <p>
     * Each axis may produce both {@link AxisEvent axis events} and {@link ButtonEvent button events}
     * (when it reaches the start or end of its course) when grabbed.
     */
    enum Axis {

        /**
         * Left stick horizontal axis (mapper MASK_AXIS_0).
         * Produces {@link ButtonEvent#LEFT_STICK_LEFT} and {@link ButtonEvent#LEFT_STICK_RIGHT}
         * and {@link AxisEvent#LEFT_STICK_HORIZONTAL} when grabbed.
         * Produces {@link VirtualGamepad.Event#LEFT} and {@link VirtualGamepad.Event#RIGHT}
         * when VirtualGamepad is grabbed.
         */
        LEFT_STICK_HORIZONTAL,

        /**
         * Left stick vertical axis (mapper MASK_AXIS_1).
         * Produces {@link ButtonEvent#LEFT_STICK_UP} and {@link ButtonEvent#LEFT_STICK_DOWN}
         * and {@link AxisEvent#LEFT_STICK_VERTICAL} when grabbed.
         * Produces {@link VirtualGamepad.Event#UP} and {@link VirtualGamepad.Event#DOWN}
         * when VirtualGamepad is grabbed.
         */
        LEFT_STICK_VERTICAL,

        /**
         * Right stick horizontal axis (mapper MASK_AXIS_2).
         * Produces {@link ButtonEvent#RIGHT_STICK_LEFT} and {@link ButtonEvent#RIGHT_STICK_RIGHT}
         * and {@link AxisEvent#RIGHT_STICK_HORIZONTAL} when grabbed.
         */
        RIGHT_STICK_HORIZONTAL,

        /**
         * Right stick vertical axis (mapper MASK_AXIS_3).
         * Produces {@link ButtonEvent#RIGHT_STICK_UP} and {@link ButtonEvent#RIGHT_STICK_DOWN}
         * and {@link AxisEvent#RIGHT_STICK_VERTICAL} when grabbed.
         */
        RIGHT_STICK_VERTICAL,

        /**
         * Left slider axis (mapper MASK_AXIS_4).
         * Produces {@link ButtonEvent#LEFT_SLIDER_DOWN} and {@link ButtonEvent#LEFT_SLIDER_UP}
         * and {@link AxisEvent#LEFT_SLIDER} when grabbed.
         */
        LEFT_SLIDER,

        /**
         * Right slider axis (mapper MASK_AXIS_5).
         * Produces {@link ButtonEvent#RIGHT_SLIDER_DOWN} and {@link ButtonEvent#RIGHT_SLIDER_UP}
         * and {@link AxisEvent#RIGHT_SLIDER} when grabbed.
         */
        RIGHT_SLIDER,
    }

    /**
     * Sets the application listener for button events.
     * <p>
     * The listener will be called whenever a grabbed {@link Button} or {@link Axis} (at its
     * extremes) produces a {@link ButtonEvent}.
     *
     * @param listener the listener to register, or {@code null} to unregister
     * @return this instance, for call chaining
     */
    @NonNull
    SkyController2Gamepad setButtonEventListener(@Nullable ButtonEvent.Listener listener);

    /**
     * Sets the application listener for axis events.
     * <p>
     * The listener will be called whenever a grabbed {@link Axis} produces an {@link AxisEvent}.
     *
     * @param listener the listener to register, or {@code null} to unregister
     * @return this instance, for call chaining
     */
    @NonNull
    SkyController2Gamepad setAxisEventListener(@Nullable AxisEvent.Listener listener);

    /**
     * Grabs the specified inputs.
     * <p>
     * When a button or axis is grabbed, the remote control stops forwarding events from that
     * input to the connected drone and instead delivers them to the registered listeners.
     * <p>
     * Ungrabbed inputs are automatically released: inputs present in the current grab state but
     * absent from the new call will be released.
     *
     * @param buttons set of buttons to grab
     * @param axes    set of axes to grab
     */
    void grabInputs(@NonNull Set<Button> buttons, @NonNull Set<Axis> axes);

    /**
     * Gets the currently grabbed buttons.
     *
     * @return set of currently grabbed buttons (may be empty)
     */
    @NonNull
    Set<Button> getGrabbedButtons();

    /**
     * Gets the currently grabbed axes.
     *
     * @return set of currently grabbed axes (may be empty)
     */
    @NonNull
    Set<Axis> getGrabbedAxes();

    /**
     * Gets the current state of all grabbed button events.
     * <p>
     * Contains the latest known {@link ButtonEvent.State} for every grabbed input's button event.
     * Events not currently grabbed are absent.
     *
     * @return map of button event to its current state
     */
    @NonNull
    Map<ButtonEvent, ButtonEvent.State> getGrabbedButtonsState();

    /**
     * Gets the drone models supported by the remote control.
     *
     * @return set of supported drone models
     */
    @NonNull
    Set<Drone.Model> getSupportedDroneModels();

    /**
     * Gets the currently active drone model.
     *
     * @return currently active drone model, or {@code null} if none
     */
    @Nullable
    Drone.Model getActiveDroneModel();

    /**
     * Gets the mapping for a specific drone model.
     *
     * @param droneModel the drone model whose mapping to retrieve
     * @return set of current mapping entries for that model, or {@code null} if model not supported
     */
    @Nullable
    Set<MappingEntry> getMapping(@NonNull Drone.Model droneModel);

    /**
     * Registers a mapping entry on the remote control.
     * <p>
     * If an existing entry with the same action and drone model is present it will be replaced.
     *
     * @param mappingEntry mapping entry to register
     */
    void registerMappingEntry(@NonNull MappingEntry mappingEntry);

    /**
     * Unregisters (removes) a mapping entry from the remote control.
     *
     * @param mappingEntry mapping entry to unregister
     */
    void unregisterMappingEntry(@NonNull MappingEntry mappingEntry);

    /**
     * Resets mappings for a specific drone model to factory defaults.
     *
     * @param droneModel drone model whose mappings to reset
     */
    void resetDefaultMappings(@NonNull Drone.Model droneModel);

    /**
     * Resets all mappings (for all supported drone models) to factory defaults.
     */
    void resetAllDefaultMappings();

    /**
     * Sets the interpolation formula for an axis for a specific drone model.
     *
     * @param droneModel   drone model onto which the interpolator should apply
     * @param axis         the axis to configure
     * @param interpolator the interpolator to apply
     */
    void setAxisInterpolator(@NonNull Drone.Model droneModel, @NonNull Axis axis,
                             @NonNull AxisInterpolator interpolator);

    /**
     * Gets all axis interpolators currently set for a specific drone model.
     *
     * @param droneModel drone model whose axis interpolators to retrieve
     * @return map of axis to interpolator, or {@code null} if model not supported
     */
    @Nullable
    Map<Axis, AxisInterpolator> getAxisInterpolators(@NonNull Drone.Model droneModel);

    /**
     * Reverses the direction of an axis for a specific drone model.
     *
     * @param droneModel drone model onto which the reversal should apply
     * @param axis       the axis to reverse
     */
    void reverseAxis(@NonNull Drone.Model droneModel, @NonNull Axis axis);

    /**
     * Gets the set of reversed axes for a specific drone model.
     *
     * @param droneModel drone model whose reversed axes to retrieve
     * @return set of reversed axes, or {@code null} if model not supported
     */
    @Nullable
    Set<Axis> getReversedAxes(@NonNull Drone.Model droneModel);

    /**
     * Gets the volatile mapping setting.
     * <p>
     * When volatile mapping is enabled, all mapping customisations are cleared when the remote
     * control disconnects from the application.
     *
     * @return volatile mapping setting
     */
    @NonNull
    OptionalBooleanSetting volatileMapping();
}
