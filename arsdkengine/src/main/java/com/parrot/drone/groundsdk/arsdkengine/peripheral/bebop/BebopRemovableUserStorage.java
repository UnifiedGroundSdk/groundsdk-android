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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.device.peripheral.RemovableUserStorage;
import com.parrot.drone.groundsdk.internal.device.peripheral.RemovableUserStorageCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.EnumSet;

import androidx.annotation.NonNull;

/** RemovableUserStorage peripheral controller for Anafi family drones. */
public class BebopRemovableUserStorage extends DronePeripheralController {

    /** The removable user storage from which this object is the backend. */
    @NonNull
    private final RemovableUserStorageCore mRemovableUserStorage;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public BebopRemovableUserStorage(@NonNull DroneController droneController) {
        super(droneController);
        mRemovableUserStorage = new RemovableUserStorageCore(mComponentStore, (type, name) -> false);
    }

    @Override
    protected void onConnected() {
        mRemovableUserStorage.publish();
    }

    @Override
    protected void onDisconnected() {
        mRemovableUserStorage.unpublish();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureCommon.CommonState.UID) {
            ArsdkFeatureCommon.CommonState.decode(command, mCommonStateCallbacks);
        }
    }

    private final ArsdkFeatureCommon.CommonState.Callback mCommonStateCallbacks = new ArsdkFeatureCommon.CommonState.Callback() {

        private String name;
        private long capacity;

        @Override
        public void onMassStorageStateListChanged(int massStorageId, String name) {
            this.name = name;
            mRemovableUserStorage.updateMediaInfo(name, capacity * 1000000);
        }

        @Override
        public void onMassStorageInfoStateListChanged(int massStorageId, long size, long usedSize, int plugged, int full, int internal) {
            capacity = size;
            mRemovableUserStorage.updateMediaInfo(name, capacity * 1000000);
            mRemovableUserStorage.updateAvailableSpace((capacity - usedSize) * 1000000);
            mRemovableUserStorage.updateCanFormat(false);
            mRemovableUserStorage.updateState(plugged == 1 ? RemovableUserStorage.State.READY : RemovableUserStorage.State.ERROR);
            mRemovableUserStorage.updateSupportedFormattingTypes(EnumSet.noneOf(RemovableUserStorage.FormattingType.class));

            mRemovableUserStorage.notifyUpdated();
        }
    };
}
