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

import com.parrot.drone.groundsdk.device.Drone;

/**
 * Defines a mapping entry for the SkyController 1 gamepad.
 * <p>
 * A mapping entry collects the drone model onto which the entry should apply, as well as the
 * {@link Type type} of the entry which defines the concrete subclass.
 * <p>
 * For SkyController 1, the drone model is always {@link Drone.Model#UNKNOWN} because the SC1 firmware
 * does not distinguish drone models in its mapping protocol. Entries are keyed by their action — two
 * entries with the same action and drone model are considered equal.
 * <p>
 * Applications cannot instantiate this class directly; use either {@link ButtonsMappingEntry} or
 * {@link AxisMappingEntry} depending on the desired entry type.
 */
public abstract class MappingEntry {

    /**
     * Type of the entry.
     */
    public enum Type {

        /**
         * Entry is actually a {@link ButtonsMappingEntry} and can be safely cast as such.
         *
         * @see #as(Class)
         */
        BUTTONS_MAPPING,

        /**
         * Entry is actually an {@link AxisMappingEntry} and can be safely cast as such.
         *
         * @see #as(Class)
         */
        AXIS_MAPPING,
    }

    /** Entry type. */
    @NonNull
    private final Type mType;

    /** Associated drone model. Always {@link Drone.Model#UNKNOWN} for SC1. */
    @NonNull
    private final Drone.Model mDroneModel;

    /**
     * Constructor.
     *
     * @param type       type of the entry
     * @param droneModel drone model onto which the entry should apply (use {@link Drone.Model#UNKNOWN} for SC1)
     */
    MappingEntry(@NonNull Type type, @NonNull Drone.Model droneModel) {
        mType = type;
        mDroneModel = droneModel;
    }

    /**
     * Gets the entry type.
     *
     * @return entry type
     */
    @NonNull
    public final Type getType() {
        return mType;
    }

    /**
     * Gets the associated drone model.
     * <p>
     * For SkyController 1 this is always {@link Drone.Model#UNKNOWN}.
     *
     * @return drone model onto which the entry applies
     */
    @NonNull
    public final Drone.Model getDroneModel() {
        return mDroneModel;
    }

    /**
     * Casts the entry to the specified subtype.
     * <p>
     * Applications should refer to the entry {@link Type type} to discover the proper subclass before
     * calling this method.
     *
     * @param entryClass entry subclass to cast to
     * @param <ENTRY>    type of entry class
     *
     * @return the entry instance, cast as requested
     *
     * @throws IllegalArgumentException if the entry cannot be cast to the specified subclass
     *
     * @see ButtonsMappingEntry
     * @see AxisMappingEntry
     */
    public <ENTRY extends MappingEntry> ENTRY as(@NonNull Class<ENTRY> entryClass) {
        try {
            return entryClass.cast(this);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("MappingEntry is not a " + entryClass.getSimpleName()
                                               + " [type: " + mType + "]");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MappingEntry that = (MappingEntry) o;

        return mType == that.mType && mDroneModel == that.mDroneModel;
    }

    @Override
    public int hashCode() {
        int result = mType.hashCode();
        result = 31 * result + mDroneModel.hashCode();
        return result;
    }
}
