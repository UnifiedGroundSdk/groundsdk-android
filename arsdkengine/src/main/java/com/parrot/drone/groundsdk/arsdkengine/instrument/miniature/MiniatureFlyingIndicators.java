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

package com.parrot.drone.groundsdk.arsdkengine.instrument.miniature;

import android.os.Handler;
import android.os.Looper;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.instrument.DroneInstrumentController;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.PilotingCommand;
import com.parrot.drone.groundsdk.device.instrument.FlyingIndicators;
import com.parrot.drone.groundsdk.internal.device.instrument.FlyingIndicatorsCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Flying indicator instrument controller for the Miniature family drones.
 *
 * <h3>PCMD-activity inference (Mambo firmware workaround)</h3>
 * <p>
 * Mambo firmware reports {@code HOVERING} when the drone is airborne but never transitions to {@code FLYING}
 * when the pilot applies stick input — this is a known firmware bug.  To work around it, while the firmware
 * state is {@code HOVERING} this controller polls the active piloting command at
 * {@value #PCMD_POLL_MS}-millisecond intervals.  When at least one axis (roll, pitch, yaw, or gaz) is
 * non-zero the published flying state is upgraded to {@link FlyingIndicators.FlyingState#FLYING}.
 * <p>
 * When all axes return to neutral a {@value #HOVER_DEBOUNCE_MS}-millisecond debounce fires before
 * reverting to {@link FlyingIndicators.FlyingState#WAITING}, preventing rapid flapping on short stick
 * releases.  Polling stops automatically on any firmware transition out of {@code HOVERING} (LANDING,
 * EMERGENCY, etc.) and on disconnection.
 * <p>
 * MOTOR_RAMPING semantics are preserved: {@code onAutoTakeOffModeChanged(state=1)} still sets
 * {@link FlyingIndicators.LandedState#MOTOR_RAMPING} regardless of stick input.
 */
public class MiniatureFlyingIndicators extends DroneInstrumentController {

    /** Polling interval (ms) for PCMD-activity checks while in HOVERING firmware state. */
    private static final long PCMD_POLL_MS = 50L;

    /** Debounce delay (ms) before reverting inferred FLYING → WAITING on neutral stick input. */
    private static final long HOVER_DEBOUNCE_MS = 500L;

    /** The flying indicator from which this object is the backend. */
    @NonNull
    private final FlyingIndicatorsCore mFlyingIndicator;

    /** Handler on the main looper, used for both the PCMD poll loop and the hover-debounce timeout. */
    @NonNull
    private final Handler mMainHandler;

    /**
     * {@code true} while the firmware is reporting {@code HOVERING} state, meaning PCMD-activity
     * inference is in effect and the poll loop is running.
     */
    private boolean mInHoverState;

    /** {@code true} while the hover-debounce runnable is posted and has not yet fired or been canceled. */
    private boolean mHoverDebouncePending;

    /**
     * Runnable that reverts the inferred FLYING state back to WAITING (HOVERING) after the debounce
     * period has elapsed without any non-neutral stick input.
     */
    private final Runnable mHoverDebounce = this::onHoverDebounce;

    /**
     * Fires when the hover debounce elapses with no non-neutral stick input: reverts the inferred
     * FLYING state back to WAITING while the firmware still reports HOVERING.
     */
    private void onHoverDebounce() {
        mHoverDebouncePending = false;
        if (mInHoverState) {
            mFlyingIndicator.updateFlyingState(FlyingIndicators.FlyingState.WAITING).notifyUpdated();
        }
    }

    /**
     * Periodic runnable that polls the active piloting command while the firmware is in HOVERING state.
     * On each tick it reads roll/pitch/yaw/gaz from the current {@link PilotingCommand}; if any axis is
     * non-neutral it cancels the debounce and publishes FLYING, otherwise it arms the debounce.
     * The runnable reschedules itself at {@value #PCMD_POLL_MS} ms until {@link #cancelInference()} is
     * called.
     */
    private final Runnable mPcmdPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mInHoverState) {
                return;
            }
            PilotingCommand pcmd = mDeviceController.getPilotingCommand();
            boolean active = pcmd.getRoll() != 0 || pcmd.getPitch() != 0
                    || pcmd.getYaw() != 0 || pcmd.getGaz() != 0;
            if (active) {
                // Non-neutral input: cancel any pending revert and publish FLYING.
                mMainHandler.removeCallbacks(mHoverDebounce);
                mHoverDebouncePending = false;
                mFlyingIndicator.updateFlyingState(FlyingIndicators.FlyingState.FLYING).notifyUpdated();
            } else {
                // All axes neutral: arm debounce to revert to WAITING if not already pending.
                if (!mHoverDebouncePending) {
                    mHoverDebouncePending = true;
                    mMainHandler.postDelayed(mHoverDebounce, HOVER_DEBOUNCE_MS);
                }
            }
            // Reschedule while still hovering.
            mMainHandler.postDelayed(this, PCMD_POLL_MS);
        }
    };

    /**
     * Constructor.
     *
     * @param droneController The drone controller that owns this component controller.
     */
    public MiniatureFlyingIndicators(@NonNull DroneController droneController) {
        super(droneController);
        mFlyingIndicator = new FlyingIndicatorsCore(mComponentStore);
        mMainHandler = new Handler(Looper.getMainLooper());
        mFlyingIndicator.updateLandedState(FlyingIndicators.LandedState.IDLE)
                        .updateFlyingState(FlyingIndicators.FlyingState.NONE)
                        .notifyUpdated();
    }

    @Override
    public void onConnected() {
        mFlyingIndicator.publish();
    }

    @Override
    public void onDisconnected() {
        cancelInference();
        mFlyingIndicator.unpublish();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureMinidrone.PilotingState.UID) {
            ArsdkFeatureMinidrone.PilotingState.decode(command, mPilotingStateCallback);
        }
    }

    /**
     * Cancels any active PCMD-inference state: stops the poll loop and the debounce timer.
     * Called whenever the firmware transitions out of HOVERING (or on disconnect).
     */
    private void cancelInference() {
        mInHoverState = false;
        mMainHandler.removeCallbacks(mPcmdPollRunnable);
        mMainHandler.removeCallbacks(mHoverDebounce);
        mHoverDebouncePending = false;
    }

    /** Callbacks called when a command of the feature ArsdkFeatureMinidrone.PilotingState is decoded. */
    private final ArsdkFeatureMinidrone.PilotingState.Callback mPilotingStateCallback =
            new ArsdkFeatureMinidrone.PilotingState.Callback() {

                @Override
                public void onAutoTakeOffModeChanged(int state) {
                    if (state == 1) {
                        mFlyingIndicator.updateLandedState(FlyingIndicators.LandedState.MOTOR_RAMPING)
                                        .notifyUpdated();
                    } else {
                        mFlyingIndicator.updateLandedState(FlyingIndicators.LandedState.IDLE).notifyUpdated();
                    }
                }

                @Override
                public void onFlyingStateChanged(
                        @Nullable ArsdkFeatureMinidrone.PilotingstateFlyingstatechangedState state) {
                    if (state != null) switch (state) {
                        case LANDED:
                            cancelInference();
                            mFlyingIndicator.updateLandedState(FlyingIndicators.LandedState.IDLE).notifyUpdated();
                            break;
//                        case USERTAKEOFF:
//                            mFlyingIndicator.updateLandedState(FlyingIndicators.LandedState.WAITING_USER_ACTION)
//                                            .notifyUpdated();
//                            break;
//                        case MOTOR_RAMPING:
//                            mFlyingIndicator.updateLandedState(FlyingIndicators.LandedState.MOTOR_RAMPING)
//                                            .notifyUpdated();
//                            break;
                        case TAKINGOFF:
                            cancelInference();
                            mFlyingIndicator.updateFlyingState(FlyingIndicators.FlyingState.TAKING_OFF)
                                            .notifyUpdated();
                            break;
                        case HOVERING:
                            // Mambo firmware never transitions HOVERING→FLYING (known fw bug).
                            // Start PCMD-activity inference: publish WAITING now and launch the
                            // poll loop; the loop will upgrade to FLYING while any axis is active
                            // and revert after HOVER_DEBOUNCE_MS of neutral input.
                            cancelInference(); // clear any prior poll in case of re-entry
                            mInHoverState = true;
                            mFlyingIndicator.updateFlyingState(FlyingIndicators.FlyingState.WAITING)
                                            .notifyUpdated();
                            mMainHandler.post(mPcmdPollRunnable);
                            break;
                        case FLYING:
                            cancelInference();
                            mFlyingIndicator.updateFlyingState(FlyingIndicators.FlyingState.FLYING)
                                            .notifyUpdated();
                            break;
                        case LANDING:
                            cancelInference();
                            mFlyingIndicator.updateFlyingState(FlyingIndicators.FlyingState.LANDING)
                                            .notifyUpdated();
                            break;
                        case EMERGENCY:
                            cancelInference();
                            mFlyingIndicator.updateState(FlyingIndicators.State.EMERGENCY).notifyUpdated();
                            break;
//                        case EMERGENCY_LANDING:
//                            mFlyingIndicator.updateState(FlyingIndicators.State.EMERGENCY_LANDING).notifyUpdated();
//                            break;
                        case ROLLING:
                            break;
                        case INIT:
                            break;
                    }


                }
            };
}
