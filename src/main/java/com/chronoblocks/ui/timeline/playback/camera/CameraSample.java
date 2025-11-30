package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.util.math.Vec3d;

/**
 * Immutable snapshot of a cinematic camera pose.
 * 
 * <p>v2.0: 扩展了 DOF/光圈等参数，方便后期渲染或导出。
 */
public record CameraSample(
    Vec3d position,
    float yaw,
    float pitch,
    float roll,
    float fov,
    float nearPlane,
    float farPlane,
    boolean depthOfFieldEnabled,
    float focusDistance,
    float aperture
) {
    public static final CameraSample EMPTY = new CameraSample(
        Vec3d.ZERO,
        0.0f,
        0.0f,
        0.0f,
        70.0f,
        0.1f,
        1_000.0f,
        false,
        10.0f,
        2.8f
    );

    public CameraSample withPosition(Vec3d pos) {
        return new CameraSample(
            pos, yaw, pitch, roll, fov,
            nearPlane, farPlane,
            depthOfFieldEnabled, focusDistance, aperture
        );
    }

    public CameraSample withFov(float newFov) {
        return new CameraSample(
            position, yaw, pitch, roll, newFov,
            nearPlane, farPlane,
            depthOfFieldEnabled, focusDistance, aperture
        );
    }
}
