package com.director.timeline.playback.camera;

import net.minecraft.util.math.Vec3d;

/**
 * 摄像机状态快照（位置/旋转/FOV/DOF等）。
 * 
 * <p>⭐ 单一摄像机系统：用于存储游戏摄像机的状态快照。
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
}
