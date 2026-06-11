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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.device.peripheral.MiniaturePilotingMode;
import com.parrot.drone.groundsdk.internal.device.peripheral.MiniaturePilotingModeCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

/** MiniaturePilotingMode peripheral controller for Miniature family drones. */
public final class MiniaturePilotingModePeripheral extends DronePeripheralController {

    /** Key used to access the preset dictionary for this peripheral's settings. */
    private static final String SETTINGS_KEY = "miniaturePilotingMode";

    // preset store bindings

    /** Preferred piloting mode preset entry. */
    private static final StorageEntry<MiniaturePilotingMode.Mode> PREFERRED_MODE_PRESET =
            StorageEntry.ofEnum("preferredMode", MiniaturePilotingMode.Mode.class);

    /** The peripheral from which this object is the backend. */
    @NonNull
    private final MiniaturePilotingModeCore mPilotingMode;

    /** Dictionary containing current preset values for this peripheral. */
    @Nullable
    private PersistentStore.Dictionary mPresetDict;

    /** Last preferred mode received from the device; {@code null} until first event is received. */
    @Nullable
    private MiniaturePilotingMode.Mode mPreferredMode;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public MiniaturePilotingModePeripheral(@NonNull DroneController droneController) {
        super(droneController);
        mPresetDict = offlineSettingsEnabled()
                ? mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY) : null;
        mPilotingMode = new MiniaturePilotingModeCore(mComponentStore, mBackend);
    }

    @Override
    protected void onConnected() {
        applyPresets();
        mPilotingMode.publish();
    }

    @Override
    protected void onDisconnected() {
        mPilotingMode.cancelSettingsRollbacks();
        mPilotingMode.unpublish();
    }

    @Override
    protected void onPresetChange() {
        mPresetDict = mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY);
        if (isConnected()) {
            applyPresets();
            mPilotingMode.notifyUpdated();
        }
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureMinidrone.PilotingSettingsState.UID) {
            ArsdkFeatureMinidrone.PilotingSettingsState.decode(command, mPilotingSettingsStateCallback);
        }
    }

    /**
     * Applies the component's persisted presets by sending any stale value to the drone.
     */
    private void applyPresets() {
        applyPreferredMode(PREFERRED_MODE_PRESET.load(mPresetDict));
    }

    /**
     * Applies preferred piloting mode.
     * <ul>
     * <li>Uses the last received value from the device if the given value is null;</li>
     * <li>Sends the value to the drone if it differs from the last received value;</li>
     * <li>Updates the component's setting accordingly.</li>
     * </ul>
     *
     * @param mode preferred piloting mode to apply, or {@code null} to use the last received value
     *
     * @return {@code true} if a command was sent to the device and the setting should arm its updating flag
     */
    private boolean applyPreferredMode(@Nullable MiniaturePilotingMode.Mode mode) {
        if (mode == null) {
            mode = mPreferredMode;
        }
        if (mode == null) {
            return false;
        }

        boolean updating = mode != mPreferredMode && sendCommand(
                ArsdkFeatureMinidrone.PilotingSettings.encodePreferredPilotingMode(modeToArsdk(mode)));

        mPreferredMode = mode;
        mPilotingMode.preferredMode().updateValue(mode);

        return updating;
    }

    /**
     * Converts a {@link MiniaturePilotingMode.Mode} to the corresponding ARSDK encoder enum.
     *
     * @param mode SDK mode
     *
     * @return corresponding ARSDK enum value
     */
    @NonNull
    private static ArsdkFeatureMinidrone.PilotingsettingsPreferredpilotingmodeMode modeToArsdk(
            @NonNull MiniaturePilotingMode.Mode mode) {
        switch (mode) {
            case EASY:      return ArsdkFeatureMinidrone.PilotingsettingsPreferredpilotingmodeMode.EASY;
            case MEDIUM:    return ArsdkFeatureMinidrone.PilotingsettingsPreferredpilotingmodeMode.MEDIUM;
            case DIFFICULT: return ArsdkFeatureMinidrone.PilotingsettingsPreferredpilotingmodeMode.DIFFICULT;
            default:        return ArsdkFeatureMinidrone.PilotingsettingsPreferredpilotingmodeMode.EASY;
        }
    }

    /**
     * Converts an ARSDK preferred-mode-changed enum to the corresponding SDK mode.
     *
     * @param arsdkMode ARSDK enum value
     *
     * @return corresponding SDK mode, or {@code null} if the value is unrecognised
     */
    @Nullable
    private static MiniaturePilotingMode.Mode modeFromArsdk(
            @Nullable ArsdkFeatureMinidrone.PilotingsettingsstatePreferredpilotingmodechangedMode arsdkMode) {
        if (arsdkMode == null) {
            return null;
        }
        switch (arsdkMode) {
            case EASY:      return MiniaturePilotingMode.Mode.EASY;
            case MEDIUM:    return MiniaturePilotingMode.Mode.MEDIUM;
            case DIFFICULT: return MiniaturePilotingMode.Mode.DIFFICULT;
            default:        return null;
        }
    }

    /** Callbacks called when a command of the feature ArsdkFeatureMinidrone.PilotingSettingsState is decoded. */
    private final ArsdkFeatureMinidrone.PilotingSettingsState.Callback mPilotingSettingsStateCallback =
            new ArsdkFeatureMinidrone.PilotingSettingsState.Callback() {

                @Override
                public void onPreferredPilotingModeChanged(
                        @Nullable ArsdkFeatureMinidrone.PilotingsettingsstatePreferredpilotingmodechangedMode mode) {
                    MiniaturePilotingMode.Mode sdkMode = modeFromArsdk(mode);
                    if (sdkMode == null) {
                        return;
                    }
                    mPreferredMode = sdkMode;
                    mPilotingMode.preferredMode().updateValue(sdkMode);
                    mPilotingMode.notifyUpdated();
                }
            };

    /** Backend of MiniaturePilotingModeCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final MiniaturePilotingModeCore.Backend mBackend = new MiniaturePilotingModeCore.Backend() {

        @Override
        public boolean setPreferredMode(@NonNull MiniaturePilotingMode.Mode mode) {
            boolean updating = applyPreferredMode(mode);
            PREFERRED_MODE_PRESET.save(mPresetDict, mode);
            if (!updating) {
                mPilotingMode.notifyUpdated();
            }
            return updating;
        }

        @Override
        public void toggleMode() {
            sendCommand(ArsdkFeatureMinidrone.Piloting.encodeTogglePilotingMode());
        }
    };
}
