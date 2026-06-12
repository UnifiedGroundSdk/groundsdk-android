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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.skycontroller.gamepad;

import android.annotation.SuppressLint;
import android.util.Log;
import android.util.SparseArray;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.RCController;
import com.parrot.drone.groundsdk.device.Drone;
import com.parrot.drone.groundsdk.device.peripheral.SkyController1Gamepad;
import com.parrot.drone.groundsdk.device.peripheral.VirtualGamepad;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.AxisInterpolator;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.AxisMappableAction;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.ButtonsMappableAction;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.AxisEvent;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.AxisMappingEntry;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.ButtonEvent;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.ButtonsMappingEntry;
import com.parrot.drone.groundsdk.device.peripheral.gamepad.skycontroller1.MappingEntry;
import com.parrot.drone.groundsdk.internal.device.peripheral.gamepad.SkyController1GamepadCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureSkyctrl;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;
import com.parrot.drone.sdkcore.ulog.ULog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import static com.parrot.drone.groundsdk.arsdkengine.Logging.TAG_GAMEPAD;

/**
 * SkyController1Gamepad peripheral controller for SkyController 1 remote control.
 * <p>
 * SkyController 1 uses the legacy {@code skyctrl} protocol rather than {@code ArsdkFeatureMapper}. This
 * means that grab, axis reversal, volatile mapping, and VirtualGamepad navigation are all unsupported.
 * <p>
 * The legacy skyctrl callbacks that were previously centralised in {@link GamepadControllerBase} are
 * owned exclusively by this class. {@link #onCommandReceived} only routes skyctrl UIDs; the mapper
 * feature path in the base class is intentionally bypassed for SC1 firmware.
 */
public final class Sc1Gamepad extends GamepadControllerBase {

    /** The SkyController1Gamepad peripheral for which this object is the backend. */
    @NonNull
    private final SkyController1GamepadCore mGamepad;

    /**
     * Currently known button mapping entries, keyed by SC1 key id (the Android keycode integer that
     * identifies the physical button in the skyctrl protocol).
     */
    @NonNull
    private final HashMap<Integer, MappingEntry> mButtonMappings;

    /**
     * Currently known axis mapping entries, keyed by SC1 axis id (0–7, as reported by the skyctrl
     * AxisMappingsState protocol).
     */
    @NonNull
    private final HashMap<Integer, MappingEntry> mAxisMappings;

    /**
     * Accumulated axis interpolators during a {@code currentAxisFilters} batch, keyed by
     * {@link SkyController1Gamepad.Axis}.
     * <p>
     * Cleared and rebuilt each time the firmware sends the full {@code onAllCurrentFiltersSent} batch.
     */
    @NonNull
    private final EnumMap<SkyController1Gamepad.Axis, AxisInterpolator> mPendingInterpolators;

    /**
     * Constructor.
     *
     * @param deviceController the device controller that owns this peripheral controller.
     */
    @SuppressLint("UseSparseArrays") // SparseArray has no values() method
    public Sc1Gamepad(@NonNull RCController deviceController) {
        super(deviceController, new NoOpTranslator());
        mGamepad = new SkyController1GamepadCore(mComponentStore, mSc1Backend);
        mButtonMappings = new HashMap<>();
        mAxisMappings = new HashMap<>();
        mPendingInterpolators = new EnumMap<>(SkyController1Gamepad.Axis.class);

        mGamepad.updateSupportedDroneModels(EnumSet.of(Drone.Model.UNKNOWN));
        mGamepad.updateActiveDroneModel(Drone.Model.UNKNOWN);
        mGamepad.notifyUpdated();
    }

    // ---- Lifecycle ----

    @Override
    protected void onConnected() {
        super.onConnected();
        mGamepad.publish();
    }

    @Override
    protected void onDisconnected() {
        super.onDisconnected();
        mGamepad.unpublish();
        mButtonMappings.clear();
        mAxisMappings.clear();
        mPendingInterpolators.clear();
    }

    /**
     * Routes incoming commands to the SC1 skyctrl callbacks only.
     * <p>
     * SC1 firmware speaks the legacy {@code skyctrl} protocol exclusively and never sends
     * {@code ArsdkFeatureMapper} commands. To avoid the dead mapper path in the base class, this override
     * handles only the four skyctrl sub-feature UIDs relevant to SC1 and does NOT call
     * {@code super.onCommandReceived()}.
     * <p>
     * <strong>Verdict #10:</strong> adding this guard removes theoretical confusion from the unused
     * mapper-callback path and makes the protocol boundary explicit.
     */
    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureSkyctrl.AxisMappingsState.UID) {
            ArsdkFeatureSkyctrl.AxisMappingsState.decode(command, mSc1AxisMappingsStateCallbacks);
        } else if (featureId == ArsdkFeatureSkyctrl.ButtonMappingsState.UID) {
            ArsdkFeatureSkyctrl.ButtonMappingsState.decode(command, mSc1ButtonMappingsStateCallbacks);
        } else if (featureId == ArsdkFeatureSkyctrl.GamepadInfosState.UID) {
            ArsdkFeatureSkyctrl.GamepadInfosState.decode(command, mSc1GamepadInfoStateCallbacks);
        } else if (featureId == ArsdkFeatureSkyctrl.AxisFiltersState.UID) {
            ArsdkFeatureSkyctrl.AxisFiltersState.decode(command, mSc1AxisFiltersStateCallbacks);
        }
        // ArsdkFeatureMapper.UID is intentionally NOT handled — SC1 firmware never sends mapper commands.
    }

    // ---- Abstract method implementations (mapper-path — all no-ops for SC1) ----
    //
    // The base class mapper callbacks invoke these; since onCommandReceived does not route mapper UIDs
    // to the base, these will never be called by SC1 firmware. They are implemented as required no-ops
    // to satisfy the abstract contract.

    @Override
    void clearAllButtonsMappings() {
        mButtonMappings.clear();
    }

    @Override
    void clearAllAxisMappings() {
        mAxisMappings.clear();
    }

    @Override
    void removeButtonsMappingEntry(long uid) {
        // mapper path — never called for SC1
    }

    @Override
    void removeAxisMappingEntry(long uid) {
        // mapper path — never called for SC1
    }

    @Override
    void addButtonsMappingEntry(long uid, @NonNull Drone.Model droneModel,
                                @NonNull ButtonsMappableAction action, @ButtonMask long buttons) {
        // mapper path — never called for SC1
    }

    @Override
    void addAxisMappingEntry(long uid, @NonNull Drone.Model droneModel,
                             @NonNull AxisMappableAction action,
                             @AxisMask long axis, @ButtonMask long buttons) {
        // mapper path — never called for SC1
    }

    @Override
    void updateButtonsMappings() {
        mGamepad.updateButtonsMappings(mButtonMappings.values()).notifyUpdated();
    }

    @Override
    void updateAxisMappings() {
        mGamepad.updateAxisMappings(mAxisMappings.values()).notifyUpdated();
    }

    @Override
    void clearAllAxisInterpolators() {
        // mapper path — never called for SC1; interpolators are updated via the AxisFilters state batch
    }

    @Override
    void removeAxisInterpolatorEntry(long uid) {
        // mapper path — never called for SC1
    }

    @Override
    void addAxisInterpolatorEntry(long uid, @NonNull Drone.Model droneModel,
                                  @AxisMask long axisMask, @NonNull AxisInterpolator interpolator) {
        // mapper path — never called for SC1
    }

    @Override
    void updateAxisInterpolators() {
        // mapper path — never called for SC1
    }

    @Override
    void clearAllReversedAxes() {
        // mapper path — never called for SC1; axis reversal is not in the skyctrl protocol
    }

    @Override
    void removeReversedAxisEntry(long uid) {
        // mapper path — never called for SC1
    }

    @Override
    void addReversedAxisEntry(long uid, @NonNull Drone.Model droneModel,
                              @AxisMask long axisMask, boolean reversed) {
        // mapper path — never called for SC1
    }

    @Override
    void updateReversedAxes() {
        // mapper path — never called for SC1
    }

    /**
     * No-op override for SC1.
     * <p>
     * <strong>Verdict #5:</strong> SC1 firmware never sends {@code ArsdkFeatureMapper.GrabState} because
     * it does not speak the mapper feature. This override ensures the base class grab-state machinery
     * does not run for SC1.
     */
    @Override
    void onGrabState(@ButtonMask long buttonsMask, @AxisMask long axesMask, @ButtonMask long buttonStates) {
        // SC1 has no grab support — intentional no-op (verdict #5)
    }

    @Override
    void onButtonEvent(@ButtonMask long button, boolean pressed) {
        // SC1 grab path is never active — no-op
    }

    @Override
    void onAxisEvent(@AxisMask long axis, @IntRange(from = -100, to = 100) int value) {
        // SC1 grab path is never active — no-op
    }

    /**
     * No-op override for SC1.
     * <p>
     * SC1 does not have volatile mapping in the skyctrl protocol. The {@link SkyController1GamepadCore}
     * does not expose a {@code volatileMapping()} setting, so there is nothing to update.
     */
    @Override
    void onVolatileMapping(boolean enabled) {
        // volatile mapping absent from skyctrl protocol — intentional no-op
    }

    @Override
    void processActiveDroneModelChange(@NonNull Drone.Model droneModel) {
        // mapper path (onActiveProduct) — never called for SC1; active model is always UNKNOWN
    }

    // ---- SC1 legacy skyctrl inbound callbacks ----

    /** Helper value object for a single skyctrl mapping record (key/axis id + mapping uid string). */
    private static final class Sc1Mapping {

        /** Key or axis id from the skyctrl protocol. */
        final int id;

        /** Mapping uid string from the skyctrl protocol (e.g. {@code "Yaw"}, {@code "No Action"}). */
        @NonNull
        final String mappingUid;

        Sc1Mapping(int id, @NonNull String mappingUid) {
            this.id = id;
            this.mappingUid = mappingUid;
        }
    }

    /**
     * Callbacks for {@code skyctrl.AxisMappingsState}.
     * <p>
     * Accumulates the full current mapping list between {@code onCurrentAxisMappings} and
     * {@code onAllCurrentAxisMappingsSent}, then pushes the result to {@link SkyController1GamepadCore}.
     * <p>
     * <strong>Verdict #15 (Yaw/Gaz fix):</strong> {@code "Yaw"} maps to
     * {@link AxisMappableAction#CONTROL_YAW_ROTATION_SPEED} and {@code "Gaz"} maps to
     * {@link AxisMappableAction#CONTROL_THROTTLE}. The previous code in
     * {@code GamepadControllerBase} had these inverted.
     * <p>
     * <strong>Verdict #14 (trailing-space fix):</strong> {@code mappingUid} is trimmed before the switch
     * because firmware sends {@code "Camera Tilt "} (with trailing space) on some versions. Using
     * {@link String#trim()} makes the mapping robust against both variants.
     */
    private final ArsdkFeatureSkyctrl.AxisMappingsState.Callback mSc1AxisMappingsStateCallbacks =
            new ArsdkFeatureSkyctrl.AxisMappingsState.Callback() {

        final Collection<Sc1Mapping> pending = new ArrayList<>();

        @Override
        public void onCurrentAxisMappings(int axisId, String mappingUid) {
            pending.add(new Sc1Mapping(axisId, mappingUid));
        }

        @Override
        public void onAllCurrentAxisMappingsSent() {
            Log.i("SC1", "all current axis mappings sent");
            mAxisMappings.clear();

            for (Sc1Mapping mapping : pending) {
                // Trim trailing spaces before switch — firmware may send "Camera Tilt " with a trailing
                // space; .trim() makes the match robust without changing current behaviour (verdict #14).
                final String uid = mapping.mappingUid.trim();
                final AxisMappableAction action;

                switch (uid) {
                    case "Pitch":
                        action = AxisMappableAction.CONTROL_PITCH;
                        break;
                    case "Roll":
                        action = AxisMappableAction.CONTROL_ROLL;
                        break;
                    case "Yaw":
                        // verdict #15: outbound sends "Yaw" for YAW_ROTATION_SPEED — decode consistently
                        action = AxisMappableAction.CONTROL_YAW_ROTATION_SPEED;
                        break;
                    case "Gaz":
                        // verdict #15: outbound sends "Gaz" for THROTTLE — decode consistently
                        action = AxisMappableAction.CONTROL_THROTTLE;
                        break;
                    case "Camera Pan":
                        action = AxisMappableAction.PAN_CAMERA;
                        break;
                    case "Camera Tilt":
                        action = AxisMappableAction.TILT_CAMERA;
                        break;
                    case "No Action":
                        action = AxisMappableAction.NO_ACTION;
                        break;
                    default:
                        action = null;
                        break;
                }

                if (action != null) {
                    AxisEvent axisEvent = AxisEvents.eventFrom(mapping.id);
                    if (axisEvent != null) {
                        mAxisMappings.put(mapping.id,
                                new AxisMappingEntry(Drone.Model.UNKNOWN, action, axisEvent));
                    } else {
                        ULog.w(TAG_GAMEPAD, "SC1: unknown axis id " + mapping.id + ", dropping mapping");
                    }
                }
            }

            updateAxisMappings();
            pending.clear();
        }

        @Override
        public void onAvailableAxisMappings(String mappingUid, String name) {
            // informational — not used
        }

        @Override
        public void onAllAvailableAxisMappingsSent() {
            // informational — not used
        }
    };

    /**
     * Callbacks for {@code skyctrl.ButtonMappingsState}.
     * <p>
     * Accumulates the full current mapping list between {@code onCurrentButtonMappings} and
     * {@code onAllCurrentButtonMappingsSent}, then pushes the result to {@link SkyController1GamepadCore}.
     * <p>
     * <strong>Verdict #14 (trailing-space fix):</strong> {@code mappingUid} is trimmed before the switch
     * because firmware sends {@code "Takeoff/Landing "} (with trailing space) on some versions.
     */
    private final ArsdkFeatureSkyctrl.ButtonMappingsState.Callback mSc1ButtonMappingsStateCallbacks =
            new ArsdkFeatureSkyctrl.ButtonMappingsState.Callback() {

        final Collection<Sc1Mapping> pending = new ArrayList<>();

        @Override
        public void onCurrentButtonMappings(int keyId, String mappingUid) {
            pending.add(new Sc1Mapping(keyId, mappingUid));
        }

        @Override
        public void onAllCurrentButtonMappingsSent() {
            Log.i("SC1", "all current button mappings sent");
            mButtonMappings.clear();

            for (Sc1Mapping mapping : pending) {
                // Trim trailing spaces — firmware may send "Takeoff/Landing " with a trailing space
                // on some versions (verdict #14).
                final String uid = mapping.mappingUid.trim();
                final ButtonsMappableAction action;

                switch (uid) {
                    case "Emergency":
                        action = ButtonsMappableAction.EMERGENCY_CUTOFF;
                        break;
                    case "Return Home":
                        action = ButtonsMappableAction.RETURN_HOME;
                        break;
                    case "Record":
                        action = ButtonsMappableAction.RECORD_VIDEO;
                        break;
                    case "Settings":
                        action = ButtonsMappableAction.APP_ACTION_SETTINGS;
                        break;
                    case "Reset Camera":
                        action = ButtonsMappableAction.CENTER_CAMERA;
                        break;
                    case "Takeoff/Landing":
                        action = ButtonsMappableAction.TAKEOFF_OR_LAND;
                        break;
                    case "Photo":
                        action = ButtonsMappableAction.TAKE_PICTURE;
                        break;
                    case "No Action":
                        action = ButtonsMappableAction.NO_ACTION;
                        break;
                    case "Back":
                        action = ButtonsMappableAction.BACK;
                        break;
                    default:
                        action = null;
                        break;
                }

                if (action != null) {
                    ButtonEvent buttonEvent = ButtonEvents.eventFrom(mapping.id);
                    if (buttonEvent != null) {
                        Set<ButtonEvent> events = EnumSet.of(buttonEvent);
                        mButtonMappings.put(mapping.id,
                                new ButtonsMappingEntry(Drone.Model.UNKNOWN, action, events));
                    } else {
                        ULog.w(TAG_GAMEPAD, "SC1: unknown key id " + mapping.id + ", dropping mapping");
                    }
                }
            }

            updateButtonsMappings();
            pending.clear();
        }

        @Override
        public void onAvailableButtonMappings(String mappingUid, String name) {
            // informational — not used
        }

        @Override
        public void onAllAvailableButtonsMappingsSent() {
            // informational — not used
        }
    };

    /**
     * Callbacks for {@code skyctrl.GamepadInfosState}.
     * <p>
     * The {@code gamepadControl} event provides the human-readable name of each key/axis reported by
     * firmware, but the names are dynamic and not consumed by the mapping decode logic. Retained for
     * completeness and debug logging.
     */
    private final ArsdkFeatureSkyctrl.GamepadInfosState.Callback mSc1GamepadInfoStateCallbacks =
            new ArsdkFeatureSkyctrl.GamepadInfosState.Callback() {

        @Override
        public void onGamepadControl(
                @Nullable ArsdkFeatureSkyctrl.GamepadinfosstateGamepadcontrolType type,
                int id, String name) {
            // informational only — firmware-reported name is not used in mapping decode
        }
    };

    /**
     * Callbacks for {@code skyctrl.AxisFiltersState}.
     * <p>
     * Each {@code onCurrentAxisFilters} delivers one axis id plus its current filter builder string.
     * The batch ends with {@code onAllCurrentFiltersSent}, at which point the accumulated map is
     * pushed atomically to {@link SkyController1GamepadCore}.
     * <p>
     * <strong>Verdict #8/#9:</strong> The firmware sends the full builder string as-is:
     * <ul>
     *   <li>{@code "ARMF;"} or prefix {@code "ARMF"} → {@link AxisInterpolator#LINEAR}</li>
     *   <li>{@code "ARXF;CPx;CPy;"} prefix {@code "ARXF"} → nearest preset tier by CPx value</li>
     * </ul>
     * CPx thresholds used to classify exponential tiers (midpoints between provisional values):
     * <ul>
     *   <li>CPx &lt; 0.70 → {@link AxisInterpolator#LIGHT_EXPONENTIAL} (nominal 0.65)</li>
     *   <li>CPx &lt; 0.80 → {@link AxisInterpolator#MEDIUM_EXPONENTIAL} (nominal 0.75)</li>
     *   <li>CPx &lt; 0.875 → {@link AxisInterpolator#STRONG_EXPONENTIAL} (nominal 0.85)</li>
     *   <li>CPx &ge; 0.875 → {@link AxisInterpolator#STRONGEST_EXPONENTIAL} (nominal 0.90)</li>
     * </ul>
     */
    private final ArsdkFeatureSkyctrl.AxisFiltersState.Callback mSc1AxisFiltersStateCallbacks =
            new ArsdkFeatureSkyctrl.AxisFiltersState.Callback() {

        @Override
        public void onCurrentAxisFilters(int axisId, String filterUidOrBuilder) {
            SkyController1Gamepad.Axis axis = AxisEvents.axisFrom(axisId);
            if (axis == null) {
                ULog.w(TAG_GAMEPAD, "SC1: unknown axis id " + axisId + " in AxisFiltersState, ignoring");
                return;
            }
            AxisInterpolator interpolator = parseFilterString(filterUidOrBuilder);
            if (interpolator != null) {
                mPendingInterpolators.put(axis, interpolator);
            } else {
                ULog.w(TAG_GAMEPAD, "SC1: unrecognised filter string '" + filterUidOrBuilder
                        + "' for axis " + axisId + ", ignoring");
            }
        }

        @Override
        public void onAllCurrentFiltersSent() {
            mGamepad.updateAxisInterpolators(mPendingInterpolators).notifyUpdated();
            mPendingInterpolators.clear();
        }
    };

    // ---- Backend implementation ----

    /**
     * Backend of {@link SkyController1GamepadCore}.
     * <p>
     * All operations translate to skyctrl protocol commands; the mapper feature is never used for SC1.
     */
    private final SkyController1GamepadCore.Backend mSc1Backend = new SkyController1GamepadCore.Backend() {

        @Override
        public void setupMappingEntry(@NonNull MappingEntry mappingEntry, boolean register) {
            switch (mappingEntry.getType()) {
                case BUTTONS_MAPPING: {
                    ButtonsMappingEntry buttonsEntry = mappingEntry.as(ButtonsMappingEntry.class);
                    ButtonsMappableAction buttonsAction = buttonsEntry.getAction();
                    int keyId = ButtonEvents.keycodeFrom(buttonsEntry.getButtonEvents());
                    if (keyId < 0) {
                        ULog.w(TAG_GAMEPAD, "SC1: cannot determine key id for button mapping, dropping");
                        return;
                    }
                    final String mappingUid;
                    if (!register) {
                        mappingUid = "No Action";
                    } else {
                        mappingUid = buttonActionToUid(buttonsAction);
                    }
                    sendCommand(ArsdkFeatureSkyctrl.ButtonMappings.encodeSetButtonMapping(keyId, mappingUid));
                    break;
                }
                case AXIS_MAPPING: {
                    AxisMappingEntry axisEntry = mappingEntry.as(AxisMappingEntry.class);
                    AxisMappableAction axisAction = axisEntry.getAction();
                    int axisId = AxisEvents.idFrom(axisEntry.getAxisEvent());
                    if (axisId < 0) {
                        ULog.w(TAG_GAMEPAD, "SC1: cannot determine axis id for axis mapping, dropping");
                        return;
                    }
                    final String mappingUid;
                    if (!register) {
                        mappingUid = "No Action";
                    } else {
                        mappingUid = axisActionToUid(axisAction);
                    }
                    sendCommand(ArsdkFeatureSkyctrl.AxisMappings.encodeSetAxisMapping(axisId, mappingUid));
                    break;
                }
            }
        }

        @Override
        public void setAxisInterpolator(@NonNull SkyController1Gamepad.Axis axis,
                                        @NonNull AxisInterpolator interpolator) {
            int axisId = AxisEvents.idFromAxis(axis);
            if (axisId < 0) {
                ULog.w(TAG_GAMEPAD, "SC1: unknown Axis " + axis + " for setAxisInterpolator, dropping");
                return;
            }
            String filterString = interpolatorToFilterString(interpolator);
            sendCommand(ArsdkFeatureSkyctrl.AxisFilters.encodeSetAxisFilter(axisId, filterString));
        }

        @Override
        public void resetMappings() {
            sendCommand(ArsdkFeatureSkyctrl.ButtonMappings.encodeDefaultButtonMapping());
            sendCommand(ArsdkFeatureSkyctrl.AxisMappings.encodeDefaultAxisMapping());
        }
    };

    // ---- Helpers ----

    /**
     * Translates a {@link ButtonsMappableAction} to the skyctrl button mapping uid string.
     *
     * @param action action to translate
     *
     * @return the mapping uid string, or {@code "No Action"} for unmapped or unknown actions
     */
    @NonNull
    private static String buttonActionToUid(@NonNull ButtonsMappableAction action) {
        switch (action) {
            case APP_ACTION_SETTINGS:
                return "Settings";
            case RETURN_HOME:
                return "Return Home";
            case TAKEOFF_OR_LAND:
                return "Takeoff/Landing";
            case RECORD_VIDEO:
                return "Record";
            case TAKE_PICTURE:
                return "Photo";
            case CENTER_CAMERA:
                return "Reset Camera";
            case EMERGENCY_CUTOFF:
                return "Emergency";
            case BACK:
                return "Back";
            default:
                return "No Action";
        }
    }

    /**
     * Translates an {@link AxisMappableAction} to the skyctrl axis mapping uid string.
     *
     * @param action action to translate
     *
     * @return the mapping uid string, or {@code "No Action"} for unmapped or unknown actions
     */
    @NonNull
    private static String axisActionToUid(@NonNull AxisMappableAction action) {
        switch (action) {
            case CONTROL_ROLL:
                return "Roll";
            case CONTROL_PITCH:
                return "Pitch";
            case CONTROL_YAW_ROTATION_SPEED:
                // outbound: "Yaw" = yaw rotation speed (consistent with inbound fix, verdict #15)
                return "Yaw";
            case CONTROL_THROTTLE:
                // outbound: "Gaz" = throttle (consistent with inbound fix, verdict #15)
                return "Gaz";
            case PAN_CAMERA:
                return "Camera Pan";
            case TILT_CAMERA:
                return "Camera Tilt";
            default:
                return "No Action";
        }
    }

    /**
     * Translates an {@link AxisInterpolator} to a skyctrl AxisFilters builder string.
     * <p>
     * Mapping (provisional CPx/CPy values — bench confirmation required before final commit,
     * per verdict #8):
     * <ul>
     *   <li>{@link AxisInterpolator#LINEAR} → {@code "ARMF;"} (linear, firmware default)</li>
     *   <li>{@link AxisInterpolator#LIGHT_EXPONENTIAL} → {@code "ARXF;0.65;0.35;"}</li>
     *   <li>{@link AxisInterpolator#MEDIUM_EXPONENTIAL} → {@code "ARXF;0.75;0.25;"}</li>
     *   <li>{@link AxisInterpolator#STRONG_EXPONENTIAL} → {@code "ARXF;0.85;0.15;"}</li>
     *   <li>{@link AxisInterpolator#STRONGEST_EXPONENTIAL} → {@code "ARXF;0.90;0.10;"}</li>
     * </ul>
     *
     * @param interpolator interpolator to translate
     *
     * @return the corresponding builder string
     */
    @NonNull
    static String interpolatorToFilterString(@NonNull AxisInterpolator interpolator) {
        switch (interpolator) {
            case LINEAR:
                return "ARMF;";
            case LIGHT_EXPONENTIAL:
                return "ARXF;0.65;0.35;";
            case MEDIUM_EXPONENTIAL:
                return "ARXF;0.75;0.25;";
            case STRONG_EXPONENTIAL:
                return "ARXF;0.85;0.15;";
            case STRONGEST_EXPONENTIAL:
                return "ARXF;0.90;0.10;";
            default:
                return "ARMF;";
        }
    }

    /**
     * Parses a skyctrl AxisFilters builder string into the nearest {@link AxisInterpolator} preset.
     * <p>
     * <ul>
     *   <li>Strings starting with {@code "ARMF"} → {@link AxisInterpolator#LINEAR}</li>
     *   <li>Strings starting with {@code "ARXF"}: the CPx field is parsed and matched to the nearest
     *       preset tier using midpoint thresholds between the provisional CPx values (verdict #8/#9):
     *       CPx &lt; 0.70 → LIGHT, CPx &lt; 0.80 → MEDIUM, CPx &lt; 0.875 → STRONG,
     *       CPx &ge; 0.875 → STRONGEST.</li>
     * </ul>
     *
     * @param filterUidOrBuilder builder string from firmware
     *
     * @return corresponding {@link AxisInterpolator}, or {@code null} if the string is unrecognised
     */
    @Nullable
    static AxisInterpolator parseFilterString(@NonNull String filterUidOrBuilder) {
        if (filterUidOrBuilder.startsWith("ARMF")) {
            return AxisInterpolator.LINEAR;
        }
        if (filterUidOrBuilder.startsWith("ARXF")) {
            // Format: "ARXF;CPx;CPy;"
            String[] parts = filterUidOrBuilder.split(";");
            if (parts.length >= 2) {
                try {
                    float cpx = Float.parseFloat(parts[1]);
                    if (cpx < 0.70f) {
                        return AxisInterpolator.LIGHT_EXPONENTIAL;
                    } else if (cpx < 0.80f) {
                        return AxisInterpolator.MEDIUM_EXPONENTIAL;
                    } else if (cpx < 0.875f) {
                        return AxisInterpolator.STRONG_EXPONENTIAL;
                    } else {
                        return AxisInterpolator.STRONGEST_EXPONENTIAL;
                    }
                } catch (NumberFormatException e) {
                    ULog.w(TAG_GAMEPAD, "SC1: malformed ARXF CPx in '" + filterUidOrBuilder + "'");
                }
            }
        }
        return null;
    }

    // ---- ButtonEvents inner class ----

    /**
     * Bidirectional table mapping SC1 Android keycode integers to {@link ButtonEvent} enum constants.
     * <p>
     * SC1 does not use the mapper feature for buttons; keycodes are raw Android integers reported by
     * the {@code skyctrl.GamepadInfosState.gamepadControl} event and used as the {@code key_id} in
     * {@code skyctrl.ButtonMappings}.
     */
    @VisibleForTesting
    static final class ButtonEvents {

        /**
         * Returns the {@link ButtonEvent} for the given SC1 keycode, or {@code null} if unrecognised.
         *
         * @param keycode Android keycode integer
         *
         * @return corresponding {@link ButtonEvent}, or {@code null}
         */
        @Nullable
        static ButtonEvent eventFrom(int keycode) {
            ButtonEvent event = KEYCODE_TO_EVENT.get(keycode);
            if (event == null) {
                ULog.w(TAG_GAMEPAD, "SC1: unsupported keycode " + keycode);
            }
            return event;
        }

        /**
         * Returns the SC1 keycode for the first {@link ButtonEvent} in the given set.
         * <p>
         * SC1 maps one button per action, so the set is expected to be a singleton. Returns {@code -1}
         * if the set is empty or contains an unrecognised event.
         *
         * @param events set of button events
         *
         * @return keycode integer, or {@code -1} if the event cannot be resolved
         */
        static int keycodeFrom(@NonNull Set<ButtonEvent> events) {
            if (events.isEmpty()) {
                return -1;
            }
            ButtonEvent event = events.iterator().next();
            int keycode = EVENT_TO_KEYCODE.get(event.ordinal(), -1);
            if (keycode < 0) {
                ULog.w(TAG_GAMEPAD, "SC1: no keycode for ButtonEvent " + event);
            }
            return keycode;
        }

        private ButtonEvents() {}

        /** GSDK ButtonEvent, by SC1 Android keycode integer. */
        private static final SparseArray<ButtonEvent> KEYCODE_TO_EVENT = new SparseArray<>();

        /** SC1 Android keycode integer, by GSDK ButtonEvent ordinal. */
        private static final SparseArray<Integer> EVENT_TO_KEYCODE = new SparseArray<>();

        private static void map(int keycode, @NonNull ButtonEvent event) {
            KEYCODE_TO_EVENT.put(keycode, event);
            EVENT_TO_KEYCODE.put(event.ordinal(), keycode);
        }

        static {
            map(96, ButtonEvent.LEFT_MINI_JS);
            map(97, ButtonEvent.RETURN_HOME);
            map(98, ButtonEvent.RECORD);
            map(99, ButtonEvent.EMERGENCY);
            map(100, ButtonEvent.RIGHT_MINI_JS);
            map(101, ButtonEvent.TAKEOFF_LAND);
            map(103, ButtonEvent.HOME);
            map(104, ButtonEvent.BACK);
        }
    }

    // ---- AxisEvents inner class ----

    /**
     * Bidirectional table mapping SC1 axis id integers (0–7) to {@link AxisEvent} enum constants and
     * to {@link SkyController1Gamepad.Axis} inputs.
     * <p>
     * SC1 axis ids are simple sequential integers used as the {@code axis_id} field in the
     * {@code skyctrl.AxisMappings} and {@code skyctrl.AxisFilters} protocols.
     */
    @VisibleForTesting
    static final class AxisEvents {

        /**
         * Returns the {@link AxisEvent} for the given SC1 axis id, or {@code null} if unrecognised.
         *
         * @param axisId SC1 axis id (0–7)
         *
         * @return corresponding {@link AxisEvent}, or {@code null}
         */
        @Nullable
        static AxisEvent eventFrom(int axisId) {
            AxisEvent event = AXIS_ID_TO_EVENT.get(axisId);
            if (event == null) {
                ULog.w(TAG_GAMEPAD, "SC1: unsupported axis id " + axisId);
            }
            return event;
        }

        /**
         * Returns the SC1 axis id for the given {@link AxisEvent}, or {@code -1} if unrecognised.
         *
         * @param event axis event
         *
         * @return SC1 axis id, or {@code -1}
         */
        static int idFrom(@NonNull AxisEvent event) {
            return EVENT_TO_AXIS_ID.get(event.ordinal(), -1);
        }

        /**
         * Returns the {@link SkyController1Gamepad.Axis} input for the given SC1 axis id, or
         * {@code null} if unrecognised.
         * <p>
         * Used by the AxisFiltersState decode path to look up the public Axis enum from a raw id.
         *
         * @param axisId SC1 axis id (0–7)
         *
         * @return corresponding {@link SkyController1Gamepad.Axis}, or {@code null}
         */
        @Nullable
        static SkyController1Gamepad.Axis axisFrom(int axisId) {
            return AXIS_ID_TO_AXIS.get(axisId);
        }

        /**
         * Returns the SC1 axis id for the given {@link SkyController1Gamepad.Axis} input, or
         * {@code -1} if unrecognised.
         * <p>
         * Used by the backend's {@code setAxisInterpolator} to determine which axis id to send to
         * firmware.
         *
         * @param axis public axis input
         *
         * @return SC1 axis id, or {@code -1}
         */
        static int idFromAxis(@NonNull SkyController1Gamepad.Axis axis) {
            return AXIS_TO_AXIS_ID.get(axis.ordinal(), -1);
        }

        private AxisEvents() {}

        /** GSDK AxisEvent, by SC1 axis id. */
        private static final SparseArray<AxisEvent> AXIS_ID_TO_EVENT = new SparseArray<>();

        /** SC1 axis id, by GSDK AxisEvent ordinal. */
        private static final SparseArray<Integer> EVENT_TO_AXIS_ID = new SparseArray<>();

        /** GSDK Axis input, by SC1 axis id. */
        private static final SparseArray<SkyController1Gamepad.Axis> AXIS_ID_TO_AXIS = new SparseArray<>();

        /** SC1 axis id, by GSDK Axis input ordinal. */
        private static final SparseArray<Integer> AXIS_TO_AXIS_ID = new SparseArray<>();

        private static void map(int axisId, @NonNull AxisEvent event,
                                @NonNull SkyController1Gamepad.Axis axis) {
            AXIS_ID_TO_EVENT.put(axisId, event);
            EVENT_TO_AXIS_ID.put(event.ordinal(), axisId);
            AXIS_ID_TO_AXIS.put(axisId, axis);
            AXIS_TO_AXIS_ID.put(axis.ordinal(), axisId);
        }

        static {
            map(0, AxisEvent.TOP_LEFT_HORIZONTAL,  SkyController1Gamepad.Axis.TOP_LEFT_HORIZONTAL);
            map(1, AxisEvent.TOP_LEFT_VERTICAL,    SkyController1Gamepad.Axis.TOP_LEFT_VERTICAL);
            map(2, AxisEvent.RIGHT_STICK_HORIZONTAL, SkyController1Gamepad.Axis.RIGHT_STICK_HORIZONTAL);
            map(3, AxisEvent.RIGHT_STICK_VERTICAL,  SkyController1Gamepad.Axis.RIGHT_STICK_VERTICAL);
            map(4, AxisEvent.LEFT_STICK_HORIZONTAL,  SkyController1Gamepad.Axis.LEFT_STICK_HORIZONTAL);
            map(5, AxisEvent.LEFT_STICK_VERTICAL,   SkyController1Gamepad.Axis.LEFT_STICK_VERTICAL);
            map(6, AxisEvent.TOP_RIGHT_HORIZONTAL,  SkyController1Gamepad.Axis.TOP_RIGHT_HORIZONTAL);
            map(7, AxisEvent.TOP_RIGHT_VERTICAL,    SkyController1Gamepad.Axis.TOP_RIGHT_VERTICAL);
        }
    }

    // ---- No-op Translator ----

    /**
     * No-op {@link NavigationEventTranslator} for SC1.
     * <p>
     * <strong>Verdict #7:</strong> The old {@code Sc1Gamepad.Translator} used mapper bitmask positions
     * (bits 4–7, 2–3) that are never populated for SC1 (SC1 uses raw Android keycodes, not mapper grab
     * bits). VirtualGamepad navigation via the mapper grab path is therefore non-functional on SC1 today.
     * Replacing the translator with this no-op makes the unsupported state explicit: the base class
     * VirtualGamepad backend will still call {@code grabNavigation()}, but the resulting grab command
     * will carry zero masks and have no effect.
     */
    private static final class NoOpTranslator implements NavigationEventTranslator {

        @ButtonMask
        @Override
        public long getNavigationGrabButtonsMask() {
            return 0;
        }

        @AxisMask
        @Override
        public long getNavigationGrabAxesMask() {
            return 0;
        }

        @Nullable
        @Override
        public VirtualGamepad.Event eventFrom(@ButtonMask long buttonMask) {
            return null;
        }
    }
}
