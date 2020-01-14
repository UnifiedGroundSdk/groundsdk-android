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
import com.parrot.drone.groundsdk.device.Drone;
import com.parrot.drone.groundsdk.device.peripheral.motor.MotorDetail;
import com.parrot.drone.groundsdk.device.peripheral.motor.MotorError;
import com.parrot.drone.groundsdk.value.BooleanSetting;

import java.util.EnumMap;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * CopterMotors peripheral interface for copter drones.
 * <p>
 * Allows to query the error status of each of the copter's motors.
 * <p>
 * This peripheral can be obtained from a {@link Drone} drone using:
 * <pre>{@code drone.getPeripheral(CopterMotors.class)}</pre>
 *
 * @see Peripheral.Provider#getPeripheral(Class)
 * @see Peripheral.Provider#getPeripheral(Class, Ref.Observer)
 */
public interface CopterMotors extends Peripheral {

    /**
     * Represents a copter's motor.
     */
    enum Motor {

        /** Front-left motor when looking at the copter from above. */
        FRONT_LEFT,

        /** Front-right motor when looking at the copter from above. */
        FRONT_RIGHT,

        /** Rear-left motor when looking at the copter from above. */
        REAR_LEFT,

        /** Rear-right motor when looking at the copter from above. */
        REAR_RIGHT
    }

    /**
     * Gets a motor's latest error status.
     *
     * @param motor motor whose status must be retrieved
     *
     * @return latest error status of the provided motor
     */
    @NonNull
    MotorError getLatestError(@NonNull Motor motor);

    /**
     * Gets all motors currently undergoing some error.
     *
     * @return a set of all motors that are currently in error
     */
    @NonNull
    Set<Motor> getMotorsCurrentlyInError();

    EnumMap<Motor, MotorDetail> motors();

    BooleanSetting cutOutMode();
}
