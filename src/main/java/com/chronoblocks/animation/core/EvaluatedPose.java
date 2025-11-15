package com.chronoblocks.animation.core;

import net.minecraft.util.math.Vec3d;

/**
 * EvaluatedPose - 某个时间点的求值结果。
 * position/scale 可为 null，visibility 默认为 1.0。
 */
public record EvaluatedPose(
    Vec3d position,
    Vec3d rotation,
    Vec3d scale,
    double visibility
) {

    public static EvaluatedPose empty() {
        return new EvaluatedPose(null, null, null, 1.0);
    }

    public EvaluatedPose merge(EvaluatedPose other) {
        if (other == null) {
            return this;
        }
        Vec3d mergedPosition = other.position != null ? other.position : this.position;
        Vec3d mergedRotation = other.rotation != null ? other.rotation : this.rotation;
        Vec3d mergedScale = other.scale != null ? other.scale : this.scale;
        double mergedVisibility = other.visibility != 1.0 ? other.visibility : this.visibility;
        return new EvaluatedPose(mergedPosition, mergedRotation, mergedScale, mergedVisibility);
    }
}


