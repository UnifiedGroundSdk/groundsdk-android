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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.camera;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.device.peripheral.AntiFlicker;
import com.parrot.drone.groundsdk.internal.device.peripheral.AntiFlickerCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureArdrone3;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** AntiFlicker peripheral controller for Anafi family drones. */
public final class BebopAntiFlicker extends DronePeripheralController {

    /** Key used to access preset and range dictionaries for this peripheral controller. */
    private static final String SETTINGS_KEY = "antiFlicker";

    // preset store bindings

    /** Anti-flickering mode preset entry. */
    private static final StorageEntry<AntiFlicker.Mode> MODE_PRESET =
            StorageEntry.ofEnum("mode", AntiFlicker.Mode.class);

    // device specific store bindings

    /** Supported anti-flickering modes device setting. */
    private static final StorageEntry<EnumSet<AntiFlicker.Mode>> SUPPORTED_MODES_SETTING =
            StorageEntry.ofEnumSet("supportedModes", AntiFlicker.Mode.class);

    /** AntiFlicker peripheral for which this object is the backend. */
    @NonNull
    private final AntiFlickerCore mAntiFlicker;

    /** Dictionary containing device specific values for this peripheral, such as settings ranges, supported status. */
    @Nullable
    private final PersistentStore.Dictionary mDeviceDict;

    /** Dictionary containing current preset values for this peripheral. */
    @Nullable
    private PersistentStore.Dictionary mPresetDict;

    /** Anti-flickering mode. */
    @Nullable
    private AntiFlicker.Mode mMode;

    /** Latest anti-flicker mode received from drone. */
    @Nullable
    private ArsdkFeatureArdrone3.AntiflickeringSetmodeMode mReceivedMode;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public BebopAntiFlicker(@NonNull DroneController droneController) {
        super(droneController);
        mAntiFlicker = new AntiFlickerCore(mComponentStore, mBackend);
        mPresetDict = offlineSettingsEnabled() ? mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY) : null;
        mDeviceDict = offlineSettingsEnabled() ? mDeviceController.getDeviceDict().getDictionary(SETTINGS_KEY) : null;
        loadPersistedData();
        if (isPersisted()) {
            mAntiFlicker.publish();
        }
    }

    @Override
    protected void onConnecting() {
        mReceivedMode = null;
    }

    @Override
    protected void onConnected() {
        applyPresets();
        mAntiFlicker.publish();
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureArdrone3.AntiflickeringState.UID) {
            ArsdkFeatureArdrone3.AntiflickeringState.decode(command, mArsdkFeatureCameraCallback);
        }
    }

    @Override
    protected void onDisconnected() {
        // clear all non saved settings
        mAntiFlicker.cancelSettingsRollbacks()
                    .updateValue(AntiFlicker.Value.UNKNOWN);

        if (offlineSettingsEnabled()) {
            mAntiFlicker.notifyUpdated();
        } else {
            mAntiFlicker.unpublish();
        }
    }

    @Override
    protected void onPresetChange() {
        mPresetDict = mDeviceController.getPresetDict().getDictionary(SETTINGS_KEY);
        if (isConnected()) {
            applyPresets();
        }
        mAntiFlicker.notifyUpdated();
    }

    @Override
    protected void onForgetting() {
        if (mDeviceDict != null) {
            mDeviceDict.clear().commit();
        }
        mAntiFlicker.unpublish();
    }

    /**
     * Tells whether device specific settings are persisted for this component.
     *
     * @return {@code true} if the component has persisted device settings, otherwise {@code false}
     */
    private boolean isPersisted() {
        return mDeviceDict != null && !mDeviceDict.isNew();
    }

    /**
     * Loads presets and settings from persistent storage and updates the component accordingly.
     */
    private void loadPersistedData() {
        final Collection<AntiFlicker.Mode> values = new ArrayList<>(Arrays.asList(AntiFlicker.Mode.values()));
        values.remove(AntiFlicker.Mode.OFF);

        mAntiFlicker.mode().updateAvailableValues(values);
        applyPresets();
    }

    /**
     * Applies component's persisted presets.
     */
    private void applyPresets() {
        applyMode(MODE_PRESET.load(mPresetDict));
    }

    /**
     * Applies anti-flicker mode
     * <ul>
     * <li>Finds an appropriate fallback value if the given value is null, or unsupported;</li>
     * <li>Sends the computed value to the drone in case it differs from the last received value;</li>
     * <li>Updates the component's setting accordingly.</li>
     * </ul>
     * <p>
     * Controls monitoring of the current location electric frequency depending on the requested {@code mode}
     *
     * @param mode value to apply
     *
     * @return {@code true} if a command was sent to the device and the component's setting should arm its updating
     *         flag
     */
    private boolean applyMode(@Nullable AntiFlicker.Mode mode) {
        if (mode == null || !mAntiFlicker.mode().getAvailableValues().contains(mode)) {
            if (mMode == null) {
                return false;
            }
            mode = mMode;
        }

        boolean updating = (mMode != mode)
                           && sendMode(mode);

        mMode = mode;
        mAntiFlicker.mode().updateValue(mMode);

        return updating;
    }

    /**
     * Sends selected anti-flickering mode to the device.
     *
     * @param mode             anti-flickering mode to set
     *                         emulated}, gives the appropriate frequency to use from current geolocation. May be {@code
     *                         null}, in which case the implementation may try to apply a suitable fallback by itself
     *
     * @return {@code true} if any command was sent to the device, otherwise {@code false}
     */
    private boolean sendMode(@NonNull AntiFlicker.Mode mode) {
        ArsdkFeatureArdrone3.AntiflickeringSetmodeMode arsdkMode = from(mode);

        if (arsdkMode != mReceivedMode) {
            final boolean res = sendCommand(ArsdkFeatureArdrone3.Antiflickering.encodeSetMode(arsdkMode));

            switch (arsdkMode) {
                case AUTO:
                    return res;
                case FIXEDFIFTYHERTZ:
                    return sendCommand(ArsdkFeatureArdrone3.Antiflickering.encodeElectricFrequency(ArsdkFeatureArdrone3.AntiflickeringElectricfrequencyFrequency.FIFTYHERTZ));
                case FIXEDSIXTYHERTZ:
                    return sendCommand(ArsdkFeatureArdrone3.Antiflickering.encodeElectricFrequency(ArsdkFeatureArdrone3.AntiflickeringElectricfrequencyFrequency.SIXTYHERTZ));
            }
        }

        return false;
    }

    /** Callbacks called when a command of the feature ArsdkFeatureCamera is decoded. */
    private final ArsdkFeatureArdrone3.AntiflickeringState.Callback mArsdkFeatureCameraCallback = new ArsdkFeatureArdrone3.AntiflickeringState.Callback() {

        @Override
        public void onElectricFrequencyChanged(@Nullable ArsdkFeatureArdrone3.AntiflickeringstateElectricfrequencychangedFrequency frequency) {
            if (frequency == null) return;

            switch (frequency) {
                case FIFTYHERTZ:
                    mAntiFlicker.updateValue(AntiFlicker.Value.HZ_50);
                    break;
                case SIXTYHERTZ:
                    mAntiFlicker.updateValue(AntiFlicker.Value.HZ_60);
                    break;
            }
            mAntiFlicker.notifyUpdated();
        }

        @Override
        public void onModeChanged(@Nullable ArsdkFeatureArdrone3.AntiflickeringstateModechangedMode newMode) {
            final ArsdkFeatureArdrone3.AntiflickeringSetmodeMode mode = ArsdkFeatureArdrone3.AntiflickeringSetmodeMode.fromValue(newMode == null ? 0 : newMode.value);
            if (mode == null) return;

            mReceivedMode = mode;

            switch (mode) {
                case AUTO:
                    mAntiFlicker.updateValue(AntiFlicker.Value.OFF);
                    mMode = AntiFlicker.Mode.AUTO;
                    break;
                case FIXEDFIFTYHERTZ:
                    mAntiFlicker.updateValue(AntiFlicker.Value.HZ_50);
                    mMode = AntiFlicker.Mode.HZ_50;
                    break;
                case FIXEDSIXTYHERTZ:
                    mAntiFlicker.updateValue(AntiFlicker.Value.HZ_60);
                    mMode = AntiFlicker.Mode.HZ_60;
                    break;
            }

            if (isConnected()) {
                mAntiFlicker.mode()
                        .updateValue(mMode);
            }

            mAntiFlicker.notifyUpdated();
        }
    };

    /** Backend of AntiFlickerCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final AntiFlickerCore.Backend mBackend = new AntiFlickerCore.Backend() {

        @Override
        public boolean setMode(@NonNull AntiFlicker.Mode mode) {
            boolean updating = applyMode(mode);
            MODE_PRESET.save(mPresetDict, mode);
            if (!updating) {
                mAntiFlicker.notifyUpdated();
            }
            return updating;
        }
    };

    /**
     * Converts a {@code AntiFlicker.Mode} to its {@code ArsdkFeatureCamera.AntiflickerMode} equivalent.
     *
     * @param mode groundsdk anti-flicker mode to convert
     *
     * @return the camera feature anti-flicker mode equivalent
     */
    @NonNull
    static ArsdkFeatureArdrone3.AntiflickeringSetmodeMode from(@NonNull AntiFlicker.Mode mode) {
        switch (mode) {
            case OFF:
            case AUTO:
                return ArsdkFeatureArdrone3.AntiflickeringSetmodeMode.AUTO;
            case HZ_50:
                return ArsdkFeatureArdrone3.AntiflickeringSetmodeMode.FIXEDFIFTYHERTZ;
            case HZ_60:
                return ArsdkFeatureArdrone3.AntiflickeringSetmodeMode.FIXEDSIXTYHERTZ;
        }
        return null;
    }
}
