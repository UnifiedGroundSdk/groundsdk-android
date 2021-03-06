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

package com.parrot.drone.groundsdk.internal.value;

import com.parrot.drone.groundsdk.value.OptionalDouble;

/**
 * Implementation class for {@code OptionalDouble}.
 */
public final class OptionalDoubleCore extends OptionalDouble {

    /** Current value if set once and never reset, otherwise {@code Double.NaN}. */
    private double mValue;

    /**
     * Constructor.
     */
    public OptionalDoubleCore() {
        mValue = Double.NaN;
    }

    @Override
    public double getValue() {
        return mValue;
    }

    @Override
    public boolean isAvailable() {
        return !Double.isNaN(mValue);
    }

    /**
     * Sets the current value.
     * <p>
     * Setting the value automatically makes {@link #isAvailable()} report {@code true}.
     * </p><p>
     * Note: don't call this method with {@code Double.NaN} value. Instead, call {@link #resetValue()}.
     * </p>
     *
     * @param value the new value to set.
     *
     * @return {@code true} if the internal value did change, otherwise {@code false}
     */
    public boolean setValue(double value) {
        if (Double.compare(mValue, value) != 0) {
            mValue = value;
            return true;
        }
        return false;
    }

    /**
     * Resets the value.
     * <p>
     * Resetting the value automatically makes {@link #isAvailable()} report {@code false}.
     * </p>
     *
     * @return {@code true} if the internal value did change, otherwise {@code false}
     */
    public boolean resetValue() {
        return setValue(Double.NaN);
    }
}
