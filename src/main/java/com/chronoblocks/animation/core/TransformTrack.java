package com.chronoblocks.animation.core;

import net.minecraft.util.math.Vec3d;

/**
 * TransformTrack - 控制容器位置或缩放的轨道。
 */
public class TransformTrack extends Track<Vec3d> {

    private final boolean scaleTrack;

    public TransformTrack(String propertyPath, boolean scaleTrack) {
        super(propertyPath);
        this.scaleTrack = scaleTrack;
    }

    @Override
    public Vec3d evaluate(double timeSec) {
        if (keyframes.isEmpty()) {
            return null;
        }
        if (keyframes.size() == 1) {
            return keyframes.get(0).value();
        }

        Keyframe<Vec3d> previous = keyframes.get(0);
        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe<Vec3d> next = keyframes.get(i);
            if (timeSec <= next.time()) {
                double duration = next.time() - previous.time();
                double alpha = duration <= 0 ? 1.0 : (timeSec - previous.time()) / duration;
                alpha = Math.max(0.0, Math.min(1.0, alpha));
                return lerp(previous.value(), next.value(), alpha);
            }
            previous = next;
        }
        return keyframes.get(keyframes.size() - 1).value();
    }

    public EvaluatedPose evaluatePose(double timeSec) {
        Vec3d value = evaluate(timeSec);
        if (value == null) {
            return EvaluatedPose.empty();
        }
        return scaleTrack
            ? new EvaluatedPose(null, null, value, 1.0)
            : new EvaluatedPose(value, null, null, 1.0);
    }

    public boolean isScaleTrack() {
        return scaleTrack;
    }

    private Vec3d lerp(Vec3d a, Vec3d b, double alpha) {
        return new Vec3d(
            a.x + (b.x - a.x) * alpha,
            a.y + (b.y - a.y) * alpha,
            a.z + (b.z - a.z) * alpha
        );
    }
}


