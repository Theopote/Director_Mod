package com.director.actor.animation;

import com.director.actor.rig.AnimationPoseResult;
import com.director.actor.rig.PoseMask;

/** Absolute-time sampling for built-in and registered Pose animations. */
public final class AnimationDataSampler {
    private AnimationDataSampler() {}

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
