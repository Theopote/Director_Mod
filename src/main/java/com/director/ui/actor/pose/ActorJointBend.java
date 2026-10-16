package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.ArticulatedPlayerRig;

/** Positive bend means the natural elbow/knee direction in local model space. */
final class ActorJointBend {
    private ActorJointBend() {}
    static boolean isJoint(String id) {
        return ArticulatedPlayerRig.LEFT_FOREARM.equals(id) || ArticulatedPlayerRig.RIGHT_FOREARM.equals(id)
                || ArticulatedPlayerRig.LEFT_SHIN.equals(id) || ArticulatedPlayerRig.RIGHT_SHIN.equals(id);
    }
    static float direction(String id) {
        if (!isJoint(id)) throw new IllegalArgumentException("Not an elbow or knee");
        return ArticulatedPlayerRig.LEFT_FOREARM.equals(id) || ArticulatedPlayerRig.RIGHT_FOREARM.equals(id) ? -1f : 1f;
    }
    static float degrees(String id, float pitchDegrees) { return direction(id)*pitchDegrees; }
    static void set(String id, float bendDegrees, float[] rotation) {
        if (!Float.isFinite(bendDegrees)) throw new IllegalArgumentException("Invalid bend angle");
        float bounded = Math.max(0f, Math.min(160f,bendDegrees));
        rotation[0] = bounded == 0f ? 0f : direction(id)*bounded;
        rotation[2]=0; // The lower limb has bend X and turn Y, not a third roll channel.
    }
}
