package com.director.core.animation.container;

import net.minecraft.util.math.Vec3d;

/**
 * RotationTrack - 控制容器欧拉角旋转的轨道（单位：度）。
 */
public class RotationTrack extends Track<Vec3d> {

    public RotationTrack(String propertyPath) {
        super(propertyPath);
    }

    @Override
    public Vec3d evaluate(double timeSec) {
        if (keyframes.isEmpty()) {
            return null;
        }
        if (keyframes.size() == 1) {
            return keyframes.getFirst().value();
        }

        Keyframe<Vec3d> previous = keyframes.getFirst();
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
        return keyframes.getLast().value();
    }

    public EvaluatedPose evaluatePose(double timeSec) {
        Vec3d value = evaluate(timeSec);
        if (value == null) {
            return EvaluatedPose.empty();
        }
        return new EvaluatedPose(null, value, null, 1.0);
    }

    private Vec3d lerp(Vec3d a, Vec3d b, double alpha) {
        return new Vec3d(
            a.x + (b.x - a.x) * alpha,
            a.y + (b.y - a.y) * alpha,
            a.z + (b.z - a.z) * alpha
        );
    }
}

