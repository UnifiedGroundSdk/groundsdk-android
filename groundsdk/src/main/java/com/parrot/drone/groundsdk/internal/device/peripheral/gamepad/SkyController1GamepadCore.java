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

package com.parrot.drone.groundsdk.internal.device.peripheral.gamepad;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parrot.drone.groundsdk.device.Drone;
import com.parrot.drone.groundsdk.device.peripheral.Peripheral;
import com.parrot.drone.groundsdk.device.peripheral.SkyController1Gamepad;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.AxisInterpolator;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.AxisEvent;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.ButtonEvent;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.MappingEntry;
import com.parrot.drone.groundsdk.internal.component.ComponentDescriptor;
import com.parrot.drone.groundsdk.internal.component.ComponentStore;
import com.parrot.drone.groundsdk.internal.component.SingletonComponentCore;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Core class for the SkyController1Gamepad peripheral.
 * <p>
 * This class is simpler than {@link SkyController3GamepadCore} because the SkyController 1 uses the legacy
 * skyctrl protocol rather than the mapper feature. Specifically, there is no grab support, no axis reversal,
 * no volatile mapping, and axis interpolators are not per-drone-model (SC1 has only one implicit drone model,
 * always {@link Drone.Model#UNKNOWN}).
 */
public final class SkyController1GamepadCore extends SingletonComponentCore implements SkyController1Gamepad {

    /** Description of SkyController1Gamepad. */
    private static final ComponentDescriptor<Peripheral, SkyController1Gamepad> DESC =
            ComponentDescriptor.of(SkyController1Gamepad.class);

    /**
     * Engine-specific backend for the SkyController1Gamepad.
     * <p>
     * All methods are symmetric with {@link SkyController3GamepadCore.Backend} except:
     * <ul>
     *   <li>No {@code droneModel} parameter on {@link #setAxisInterpolator} — SC1 has no per-model expo.</li>
     *   <li>No {@code setReversedAxis} — axis reversal is not exposed by the skyctrl protocol.</li>
     *   <li>No {@code setGrabbedInputs} — grab is not supported by the skyctrl protocol.</li>
     *   <li>No {@code setVolatileMapping} — not present in the skyctrl protocol.</li>
     *   <li>{@link #resetMappings} takes no {@code droneModel} parameter — SC1 has only one implicit model.</li>
     * </ul>
     */
    public interface Backend {

        /**
         * Registers or unregisters the given mapping entry via the skyctrl protocol.
         * <p>
         * For {@link MappingEntry.Type#BUTTONS_MAPPING} entries, sends
         * {@code skyctrl.ButtonMappings.SetButtonMapping(key_id, mapping_uid_or_no_action)}.
         * For {@link MappingEntry.Type#AXIS_MAPPING} entries, sends
         * {@code skyctrl.AxisMappings.SetAxisMapping(axis_id, mapping_uid_or_no_action)}.
         * When {@code register} is {@code false}, {@code "No Action"} is used as the mapping uid.
         *
         * @param mappingEntry mapping entry to register or unregister
         * @param register     {@code true} to register the entry, {@code false} to unregister it
         */
        void setupMappingEntry(@NonNull MappingEntry mappingEntry, boolean register);

        /**
         * Sets an axis interpolator via the skyctrl AxisFilters protocol.
         * <p>
         * Translates the {@link AxisInterpolator} value to an {@code ARMF;} or {@code ARXF;CPx;CPy;} builder
         * string and sends {@code skyctrl.AxisFilters.SetAxisFilter(axis_id, filter_string)} to the firmware.
         *
         * @param axis         axis onto which the interpolator must apply
         * @param interpolator axis interpolator to apply
         */
        void setAxisInterpolator(@NonNull Axis axis, @NonNull AxisInterpolator interpolator);

        /**
         * Resets all mappings to their factory defaults.
         * <p>
         * Sends {@code skyctrl.ButtonMappings.DefaultButtonMapping} and
         * {@code skyctrl.AxisMappings.DefaultAxisMapping} to the firmware.
         */
        void resetMappings();
    }

    /** Engine peripheral backend. */
    @NonNull
    private final Backend mBackend;

    /**
     * Mappings, keyed by drone model.
     * <p>
     * For SC1 this map always has at most one entry, keyed by {@link Drone.Model#UNKNOWN}.
     */
    @NonNull
    private final Map<Drone.Model, Set<MappingEntry>> mMappings;

    /**
     * Axis interpolators, keyed by axis.
     * <p>
     * Unlike SC3, SC1 interpolators are not per-drone-model; there is only one implicit model.
     */
    @NonNull
    private final EnumMap<Axis, AxisInterpolator> mAxisInterpolators;

    /**
     * Supported drone models.
     * <p>
     * Always {@code {UNKNOWN}} for SC1 — the firmware does not distinguish drone models.
     */
    @NonNull
    private EnumSet<Drone.Model> mSupportedModels;

    /** Currently active drone model. Always {@link Drone.Model#UNKNOWN} for SC1. */
    @Nullable
    private Drone.Model mActiveDroneModel;

    /** Application button event listener. */
    @Nullable
    private ButtonEvent.Listener mButtonEventListener;

    /** Application axis event listener. */
    @Nullable
    private AxisEvent.Listener mAxisEventListener;

    /**
     * Constructor.
     *
     * @param peripheralStore store where this peripheral belongs
     * @param backend         backend used to forward actions to the engine
     */
    public SkyController1GamepadCore(@NonNull ComponentStore<Peripheral> peripheralStore,
                                     @NonNull Backend backend) {
        super(DESC, peripheralStore);
        mBackend = backend;
        mSupportedModels = EnumSet.of(Drone.Model.UNKNOWN);
        mMappings = new EnumMap<>(Drone.Model.class);
        mAxisInterpolators = new EnumMap<>(Axis.class);
    }

    @Override
    public void unpublish() {
        mButtonEventListener = null;
        mAxisEventListener = null;
        mMappings.clear();
        mAxisInterpolators.clear();
        super.unpublish();
    }

    // ---- SkyController1Gamepad interface ----

    @NonNull
    @Override
    public SkyController1Gamepad setButtonEventListener(@Nullable ButtonEvent.Listener listener) {
        mButtonEventListener = listener;
        return this;
    }

    @NonNull
    @Override
    public SkyController1Gamepad setAxisEventListener(@Nullable AxisEvent.Listener listener) {
        mAxisEventListener = listener;
        return this;
    }

    @NonNull
    @Override
    public Set<Drone.Model> getSupportedDroneModels() {
        return mSupportedModels.isEmpty() ? EnumSet.noneOf(Drone.Model.class) : EnumSet.copyOf(mSupportedModels);
    }

    @Nullable
    @Override
    public Drone.Model getActiveDroneModel() {
        return mActiveDroneModel;
    }

    @Nullable
    @Override
    public Set<MappingEntry> getMapping(@NonNull Drone.Model droneModel) {
        if (!mSupportedModels.contains(droneModel)) {
            return null;
        }
        Set<MappingEntry> entries = mMappings.get(droneModel);
        return entries == null ? new HashSet<MappingEntry>() : new HashSet<>(entries);
    }

    @Override
    public void registerMappingEntry(@NonNull MappingEntry mappingEntry) {
        mBackend.setupMappingEntry(mappingEntry, true);
    }

    @Override
    public void unregisterMappingEntry(@NonNull MappingEntry mappingEntry) {
        mBackend.setupMappingEntry(mappingEntry, false);
    }

    @Override
    public void resetDefaultMappings() {
        mBackend.resetMappings();
    }

    @Override
    public void setAxisInterpolator(@NonNull Axis axis, @NonNull AxisInterpolator interpolator) {
        if (mAxisInterpolators.get(axis) != interpolator) {
            mBackend.setAxisInterpolator(axis, interpolator);
        }
    }

    @NonNull
    @Override
    public Map<Axis, AxisInterpolator> getAxisInterpolators() {
        return new EnumMap<>(mAxisInterpolators);
    }

    // ---- Update methods called from Sc1Gamepad ----

    /**
     * Updates the set of supported drone models.
     *
     * @param supportedModels new set of supported drone models
     *
     * @return this, to allow call chaining
     */
    public SkyController1GamepadCore updateSupportedDroneModels(@NonNull EnumSet<Drone.Model> supportedModels) {
        if (!mSupportedModels.equals(supportedModels)) {
            mSupportedModels = supportedModels;
            mChanged = true;
        }
        return this;
    }

    /**
     * Updates all current buttons mapping entries.
     * <p>
     * Replaces all existing {@link MappingEntry.Type#BUTTONS_MAPPING} entries with the provided collection.
     *
     * @param mappings new collection of buttons mapping entries
     *
     * @return this, to allow call chaining
     */
    public SkyController1GamepadCore updateButtonsMappings(@NonNull Collection<MappingEntry> mappings) {
        return updateMappings(mappings, MappingEntry.Type.BUTTONS_MAPPING);
    }

    /**
     * Updates all current axis mapping entries.
     * <p>
     * Replaces all existing {@link MappingEntry.Type#AXIS_MAPPING} entries with the provided collection.
     *
     * @param mappings new collection of axis mapping entries
     *
     * @return this, to allow call chaining
     */
    public SkyController1GamepadCore updateAxisMappings(@NonNull Collection<MappingEntry> mappings) {
        return updateMappings(mappings, MappingEntry.Type.AXIS_MAPPING);
    }

    /**
     * Updates all axis interpolators from the firmware's AxisFiltersState callback batch.
     * <p>
     * Unlike SC3, this is a flat map keyed by axis only — SC1 has no per-drone-model axis settings.
     *
     * @param interpolators new map of axis interpolators
     *
     * @return this, to allow call chaining
     */
    public SkyController1GamepadCore updateAxisInterpolators(@NonNull Map<Axis, AxisInterpolator> interpolators) {
        mAxisInterpolators.clear();
        mAxisInterpolators.putAll(interpolators);
        mChanged = true;
        return this;
    }

    /**
     * Updates the active drone model.
     * <p>
     * For SC1 this is always {@link Drone.Model#UNKNOWN}.
     *
     * @param model new active drone model
     *
     * @return this, to allow call chaining
     */
    public SkyController1GamepadCore updateActiveDroneModel(@NonNull Drone.Model model) {
        if (mActiveDroneModel != model) {
            mActiveDroneModel = model;
            mChanged = true;
        }
        return this;
    }

    /**
     * Dispatches a button event to the registered listener.
     * <p>
     * In practice this is never called by the current SC1 engine because SkyController 1 does not support
     * input grab (no grab command in the skyctrl protocol). Retained for API symmetry and future-proofing.
     *
     * @param event button event to forward
     * @param state button state
     */
    public void notifyButtonEvent(@NonNull ButtonEvent event, @NonNull ButtonEvent.State state) {
        if (mButtonEventListener != null) {
            mButtonEventListener.onButtonEvent(event, state);
        }
    }

    /**
     * Dispatches an axis event to the registered listener.
     * <p>
     * In practice this is never called by the current SC1 engine because SkyController 1 does not support
     * input grab (no grab command in the skyctrl protocol). Retained for API symmetry and future-proofing.
     *
     * @param event axis event to forward
     * @param value axis value, in range [-100, 100]
     */
    public void notifyAxisEvent(@NonNull AxisEvent event, @IntRange(from = -100, to = 100) int value) {
        if (mAxisEventListener != null) {
            mAxisEventListener.onAxisEvent(event, value);
        }
    }

    /**
     * Updates buttons or axis mapping entries.
     * <p>
     * All existing mapping entries of the target type are removed and replaced by the provided mapping entries.
     *
     * @param mappings mapping entries to update
     * @param target   type of mapping entries to update
     *
     * @return this, to allow call chaining
     */
    private SkyController1GamepadCore updateMappings(@NonNull Collection<MappingEntry> mappings,
                                                     @NonNull MappingEntry.Type target) {
        // remove all current mapping entries matching target type
        for (Set<MappingEntry> droneMapping : mMappings.values()) {
            Iterator<MappingEntry> iterator = droneMapping.iterator();
            while (iterator.hasNext()) {
                MappingEntry entry = iterator.next();
                if (target == entry.getType()) {
                    iterator.remove();
                    mChanged = true;
                }
            }
        }
        // add all provided mappings
        for (MappingEntry entry : mappings) {
            Drone.Model model = entry.getDroneModel();
            Set<MappingEntry> droneMapping = mMappings.get(model);
            if (droneMapping == null) {
                droneMapping = new HashSet<>();
                mMappings.put(model, droneMapping);
            }
            mChanged |= droneMapping.add(entry);
        }
        return this;
    }
}
