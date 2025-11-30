package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.util.math.Vec3d;

/**
 * Immutable snapshot of a cinematic camera pose.
 */
public record CameraSample(
    Vec3d position,
    float yaw,
    float pitch,
    float roll,
    float fov,
    float nearPlane,
    float farPlane
) {
    public static final CameraSample EMPTY = new CameraSample(
        Vec3d.ZERO,
        0.0f,
        0.0f,
        0.0f,
        70.0f,
        0.1f,
        1_000.0f
    );
}
