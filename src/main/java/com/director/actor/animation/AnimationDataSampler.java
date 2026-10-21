package com.director.actor.animation;

import com.director.actor.rig.AnimationPoseResult;
import com.director.actor.rig.PoseMask;

/** Absolute-time sampling for built-in and registered Pose animations. */
public final class AnimationDataSampler {
    private AnimationDataSampler() {}

    /** Adapt fixed-field actions to the shared compositor without inventing TRS channels. */
    public static com.director.actor.rig.skeleton.RigPose sampleRig(String id,
            com.director.actor.rig.skeleton.RigDefinition rig, float time, boolean loop, float speed) {
        if (!com.director.actor.rig.skeleton.HumanoidTopology.matches(rig)) return null;
        var sampled = sample(id, time, loop, speed);
        if (sampled == null) return null;
        var converted = com.director.actor.rig.skeleton.LegacyHumanoidPoseAdapter.convert(sampled.pose, rig);
        var result = new com.director.actor.rig.skeleton.RigPose(rig);
        for (int i = 0; i < rig.boneCount(); i++) {
            if (converted.isAuthored(i)) result.setLocalTransform(i, converted.localTransform(i),
                    com.director.actor.rig.skeleton.RigPose.ROTATION);
        }
        return result;
    }

    public static AnimationPoseResult sample(String id, float localTime, boolean loop, float speed) {
        if (id == null || id.isBlank()) return null;
        var data = AnimationLibrary.getInstance().get(id).orElse(null);
        if (data == null) return null;
        double time = Float.isFinite(localTime) ? Math.max(0d, localTime) : 0d;
        double rate = Float.isFinite(speed) ? Math.max(0.01d, speed) : 1d;
        double duration = data.getDuration();
        time *= rate;
        if (!Double.isFinite(duration) || duration <= 0d) time = 0d;
        else time = loop ? time % duration : Math.min(time, duration);
        // Boundary samples may alias an asset keyframe; runtime overrides must never mutate it.
        return new AnimationPoseResult(data.sampleAt((float) time).copy(), PoseMask.full());
    }
}
