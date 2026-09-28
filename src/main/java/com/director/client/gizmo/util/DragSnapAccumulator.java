package com.director.client.gizmo.util;

import net.minecraft.util.math.Vec3d;

/** Snap cumulative displacement so slow, high-frame-rate drags never lose sub-step input. */
public final class DragSnapAccumulator {
    private Vec3d raw = Vec3d.ZERO;
    private Vec3d applied = Vec3d.ZERO;

    public Vec3d update(Vec3d delta, double step) {
        raw = raw.add(delta);
        Vec3d target = step > 0 ? SnapUtil.snapVec(raw, step, null) : raw;
        Vec3d change = target.subtract(applied);
        applied = target;
        return change;
    }

    public void reset() {
        raw = Vec3d.ZERO;
        applied = Vec3d.ZERO;
    }
}
