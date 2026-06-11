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

package com.parrot.drone.groundsdk.arsdkengine.pilotingitf.miniature;

import androidx.annotation.NonNull;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.pilotingitf.PilotingItfController;
import com.parrot.drone.groundsdk.device.pilotingitf.animation.Animation;
import com.parrot.drone.groundsdk.device.pilotingitf.animation.Flip;
import com.parrot.drone.groundsdk.internal.device.pilotingitf.animation.AnimationItfCore;
import com.parrot.drone.groundsdk.internal.device.pilotingitf.animation.FlipCore;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.util.EnumSet;

/**
 * Animation piloting interface controller for Mambo / miniature-family drones.
 *
 * <p>Bridges the {@code Minidrone.Animations.Flip} ARSDK command to the groundSdk
 * {@link com.parrot.drone.groundsdk.device.pilotingitf.AnimationItf} public API.
 *
 * <p><b>Animation-state limitation:</b> Mambo firmware does not send any animation-state
 * event after a flip command — unlike Anafi which uses the {@code animation} feature for
 * bidirectional state.  To give callers a consistent observable, this controller
 * synthesises state on send: immediately after the command is enqueued the current
 * animation is set to {@link Animation.Status#ANIMATING}, and a second
 * {@link Animation.Status#IDLE} transition is posted so the interface returns to idle
 * as soon as the framework drains the command queue.  Callers should therefore treat the
 * ANIMATING state as "flip command sent" rather than "flip in progress".
 */
public class MiniatureAnimationPilotingItf extends PilotingItfController {

    /** Animation interface for which this object is the backend. */
    @NonNull
    private final AnimationItfCore mAnimationItf;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this animation interface controller.
     */
    public MiniatureAnimationPilotingItf(@NonNull DroneController droneController) {
        super(droneController);
        mAnimationItf = new AnimationItfCore(mComponentStore, mBackend);
    }

    @Override
    protected void onConnected() {
        // Mambo supports all four flip directions unconditionally — advertise them statically.
        mAnimationItf.updateAvailableAnimations(EnumSet.of(Animation.Type.FLIP));
        mAnimationItf.publish();
    }

    @Override
    protected void onDisconnected() {
        mAnimationItf.unpublish();
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        // Mambo firmware has no animation-state events; nothing to decode here.
    }

    /**
     * Encodes and sends a {@code Minidrone.Animations.Flip} command for the given direction,
     * then synthesises animation state because Mambo firmware sends no state feedback.
     *
     * @param config {@code Flip} animation configuration
     *
     * @return {@code true} if the command was sent, {@code false} otherwise
     */
    private boolean startFlip(@NonNull Flip.Config config) {
        ArsdkFeatureMinidrone.AnimationsFlipDirection arsdkDirection;
        switch (config.getDirection()) {
            case FRONT:
                arsdkDirection = ArsdkFeatureMinidrone.AnimationsFlipDirection.FRONT;
                break;
            case BACK:
                arsdkDirection = ArsdkFeatureMinidrone.AnimationsFlipDirection.BACK;
                break;
            case RIGHT:
                arsdkDirection = ArsdkFeatureMinidrone.AnimationsFlipDirection.RIGHT;
                break;
            case LEFT:
                arsdkDirection = ArsdkFeatureMinidrone.AnimationsFlipDirection.LEFT;
                break;
            default:
                return false;
        }
        boolean sent = sendCommand(ArsdkFeatureMinidrone.Animations.encodeFlip(arsdkDirection));
        if (sent) {
            // Synthesise ANIMATING then immediately IDLE: firmware provides no state events.
            mAnimationItf.updateAnimation(new FlipCore(config.getDirection()),
                    Animation.Status.ANIMATING).notifyUpdated();
            mAnimationItf.clearAnimation().notifyUpdated();
        }
        return sent;
    }

    /** Backend of AnimationItfCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final AnimationItfCore.Backend mBackend = new AnimationItfCore.Backend() {

        @Override
        public boolean startAnimation(@NonNull Animation.Config animationConfig) {
            if (animationConfig.getAnimationType() == Animation.Type.FLIP) {
                return startFlip((Flip.Config) animationConfig);
            }
            return false;
        }

        @Override
        public boolean abortCurrentAnimation() {
            // Mambo has no cancel command for animations; nothing to send.
            return false;
        }
    };
}
