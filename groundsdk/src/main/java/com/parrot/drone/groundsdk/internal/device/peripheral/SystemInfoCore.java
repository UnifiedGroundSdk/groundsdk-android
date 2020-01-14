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

package com.parrot.drone.groundsdk.internal.device.peripheral;

import com.parrot.drone.groundsdk.device.peripheral.Peripheral;
import com.parrot.drone.groundsdk.device.peripheral.SystemInfo;
import com.parrot.drone.groundsdk.internal.component.ComponentDescriptor;
import com.parrot.drone.groundsdk.internal.component.ComponentStore;
import com.parrot.drone.groundsdk.internal.component.SingletonComponentCore;

import androidx.annotation.NonNull;

/** Core class for SystemInfo. */
public class SystemInfoCore extends SingletonComponentCore implements SystemInfo {

    /** Description of SystemInfo. */
    private static final ComponentDescriptor<Peripheral, SystemInfo> DESC = ComponentDescriptor.of(SystemInfo.class);

    /** Engine-specific backend for SystemInfo. */
    public interface Backend {

        /**
         * Triggers a factory reset.
         *
         * @return {@code true} if the operation could be initiated, otherwise {@code false}
         */
        boolean factoryReset();

        /**
         * Resets device settings.
         *
         * @return {@code true} if the operation could be initiated, otherwise {@code false}
         */
        boolean resetSettings();

        void reboot();
    }

    /** Engine peripheral backend. */
    @NonNull
    private final Backend mBackend;

    /** Device serial number. */
    @NonNull
    private String mSerial;

    /** Device firmware version. */
    @NonNull
    private String mFirmwareVersion;

    /** {@code true} if device firmware version is blacklisted. */
    private boolean mFirmwareBlacklisted;

    /** Device hardware version. */
    @NonNull
    private String mHardwareVersion;

    /** Device main CPU identifier. */
    @NonNull
    private String mCpuId;

    @NonNull
    private String mP7Id;

    /** Device board identifier. */
    @NonNull
    private String mBoardId;

    @NonNull
    private String controllerARCommandsVersion;

    @NonNull
    private String skyControllerARCommandsVersion;

    @NonNull
    private String deviceARCommandsVersion;

    @NonNull
    private String gpsSoftwareVersion;

    @NonNull
    private String gpsHardwareVersion;

    @NonNull
    private SkyControllerVariant skyControllerVariant;


    /** {@code true} when a factory reset is in progress. */
    private boolean mOngoingFactoryReset;

    /** {@code true} when a reset settings operation is in progress. */
    private boolean mOngoingResetSettings;

    /**
     * Constructor.
     *
     * @param peripheralStore store where this peripheral belongs
     * @param backend         backend used to forward actions to the engine
     */
    public SystemInfoCore(@NonNull ComponentStore<Peripheral> peripheralStore, @NonNull Backend backend) {
        super(DESC, peripheralStore);
        mBackend = backend;
        mSerial = "";
        mHardwareVersion = "";
        mFirmwareVersion = "";
        mCpuId = "";
        mP7Id = "";
        mBoardId = "";
        controllerARCommandsVersion = "";
        skyControllerARCommandsVersion = "";
        deviceARCommandsVersion = "";
        gpsSoftwareVersion = "";
        gpsHardwareVersion = "";
        skyControllerVariant = SkyControllerVariant.NOT_APPLICABLE;
    }

    @Override
    @NonNull
    public String getFirmwareVersion() {
        return mFirmwareVersion;
    }

    @Override
    public boolean isFirmwareBlacklisted() {
        return mFirmwareBlacklisted;
    }

    @NonNull
    @Override
    public String getHardwareVersion() {
        return mHardwareVersion;
    }

    @NonNull
    @Override
    public String getSerialNumber() {
        return mSerial;
    }

    @NonNull
    @Override
    public String getCpuIdentifier() {
        return mCpuId;
    }

    @NonNull
    @Override
    public String getP7Identifier() {
        return mP7Id;
    }

    @NonNull
    @Override
    public String getBoardIdentifier() {
        return mBoardId;
    }

    @NonNull
    public String getControllerARCommandsVersion() {
        return controllerARCommandsVersion;
    }

    @NonNull
    public String getSkyControllerARCommandsVersion() {
        return skyControllerARCommandsVersion;
    }

    @NonNull
    public String getDeviceARCommandsVersion() {
        return deviceARCommandsVersion;
    }

    @NonNull
    @Override
    public String getGpsSoftwareVersion() {
        return gpsSoftwareVersion;
    }

    @NonNull
    @Override
    public String getGpsHardwareVersion() {
        return gpsHardwareVersion;
    }


    @NonNull
    @Override
    public SkyControllerVariant getSkyControllerVariant() {
        return skyControllerVariant;
    }

    @Override
    public boolean isFactoryResetInProgress() {
        return mOngoingFactoryReset;
    }

    @Override
    public boolean factoryReset() {
        boolean inProgress = mBackend.factoryReset();
        if (inProgress && !mOngoingFactoryReset) {
            mOngoingFactoryReset = true;
            mChanged = true;
            notifyUpdated();
        }
        return inProgress;
    }

    @Override
    public boolean isResetSettingsInProgress() {
        return mOngoingResetSettings;
    }

    @Override
    public boolean resetSettings() {
        boolean inProgress = mBackend.resetSettings();
        if (inProgress && !mOngoingResetSettings) {
            mOngoingResetSettings = true;
            mChanged = true;
            notifyUpdated();
        }
        return inProgress;
    }

    @Override
    public void reboot() {
        mBackend.reboot();
    }

    /**
     * Updates the device serial number.
     *
     * @param serial new device serial number
     *
     * @return this, to allow call chaining
     */
    public final SystemInfoCore updateSerial(@NonNull String serial) {
        if (!serial.equals(mSerial)) {
            mSerial = serial;
            mChanged = true;
        }
        return this;
    }

    /**
     * Updates the device firmware version.
     *
     * @param version string representing the new device firmware version
     *
     * @return this, to allow call chaining
     */
    public final SystemInfoCore updateFirmwareVersion(@NonNull String version) {
        if (!version.equals(mFirmwareVersion)) {
            mFirmwareVersion = version;
            mChanged = true;
        }
        return this;
    }

    /**
     * Updates the blacklisted state of device firmware version.
     *
     * @param blacklisted whether or not the firmware version is blacklisted
     *
     * @return this, to allow call chaining
     */
    public final SystemInfoCore updateFirmwareBlacklisted(boolean blacklisted) {
        if (blacklisted != mFirmwareBlacklisted) {
            mFirmwareBlacklisted = blacklisted;
            mChanged = true;
        }
        return this;
    }

    /**
     * Updates the device hardware version.
     *
     * @param version string representing the new device hardware version
     *
     * @return this, to allow call chaining
     */
    public final SystemInfoCore updateHardwareVersion(@NonNull String version) {
        if (!version.equals(mHardwareVersion)) {
            mHardwareVersion = version;
            mChanged = true;
        }
        return this;
    }

    /**
     * Updates the drone main CPU identifier.
     *
     * @param cpuId new drone CPU id
     *
     * @return this, to allow call chaining
     */
    public final SystemInfoCore updateCpuId(@NonNull String cpuId) {
        if (!cpuId.equals(mCpuId)) {
            mCpuId = cpuId;
            mChanged = true;
        }
        return this;
    }

    public final SystemInfoCore updateP7Id(@NonNull String p7Id) {
        if (!p7Id.equals(mP7Id)) {
            mP7Id = p7Id;
            mChanged = true;
        }
        return this;
    }

    /**
     * Updates the device board identifier.
     *
     * @param boardId new board id
     *
     * @return this, to allow call chaining
     */
    public final SystemInfoCore updateBoardId(@NonNull String boardId) {
        if (!boardId.equals(mBoardId)) {
            mBoardId = boardId;
            mChanged = true;
        }
        return this;
    }

    public final SystemInfoCore updateControllerARCommandsVersion(@NonNull String version) {
        if (!controllerARCommandsVersion.equals(version)) {
            controllerARCommandsVersion = version;
            mChanged = true;
        }
        return this;
    }

    public final SystemInfoCore updateSkyControllerARCommandsVersion(@NonNull String version) {
        if (!skyControllerARCommandsVersion.equals(version)) {
            skyControllerARCommandsVersion = version;
            mChanged = true;
        }
        return this;
    }

    public final SystemInfoCore updateDeviceARCommandsVersion(@NonNull String version) {
        if (!deviceARCommandsVersion.equals(version)) {
            deviceARCommandsVersion = version;
            mChanged = true;
        }
        return this;
    }

    public final SystemInfoCore updateGpsSoftwareVersion(@NonNull String version) {
        if (!gpsSoftwareVersion.equals(version)) {
            gpsSoftwareVersion = version;
            mChanged = true;
        }
        return this;
    }

    public final SystemInfoCore updateGpsHardwareVersion(@NonNull String version) {
        if (!gpsHardwareVersion.equals(version)) {
            gpsHardwareVersion = version;
            mChanged = true;
        }
        return this;
    }

    public final SystemInfoCore updateSkyControllerVariant(@NonNull SkyControllerVariant variant) {
        if (!skyControllerVariant.equals(variant)) {
            skyControllerVariant = variant;
            mChanged = true;
        }
        return this;
    }

    /**
     * Clears the ongoing reset settings flag.
     *
     * @return this, to allow call chaining
     */
    public final SystemInfoCore clearOngoingResetSettingsFlag() {
        if (mOngoingResetSettings) {
            mOngoingResetSettings = false;
            mChanged = true;
        }
        return this;
    }

    /**
     * Clears the ongoing factory reset flag.
     *
     * @return this, to allow call chaining
     */
    public final SystemInfoCore clearOngoingFactoryResetFlag() {
        if (mOngoingFactoryReset) {
            mOngoingFactoryReset = false;
            mChanged = true;
        }
        return this;
    }
}
