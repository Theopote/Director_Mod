package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * CameraAccessor - 封装所有对 Minecraft 原生 Camera 的读写。
 *
 * <p>注意：大部分时候，我们不直接在这里写回 Camera，
 * 实际渲染时是通过 CameraMixin 在 update 之后覆盖位置/角度。
 * 这里更多是用于采样当前玩家视角。
 */
public final class CameraAccessor {

    private CameraAccessor() {}

    /**
     * 从当前游戏摄像机采样一个 CameraSample。
     * 
     * @param client MinecraftClient 实例
     */
    public static CameraSample sampleGameCamera(MinecraftClient client) {
        if (client == null || client.gameRenderer == null) {
            return CameraSample.EMPTY;
        }
        
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return CameraSample.EMPTY;
        }

        Vec3d pos = camera.getPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        float roll = 0.0f; // 原版没有 roll，后续如果有第三方 roll 就从 matrix/扩展里取

        // FOV 从选项获取（GameRenderer#getFov 是 private 的）
        float fov = 70.0f; // 默认值
        if (client.options != null && client.options.getFov() != null) {
            fov = client.options.getFov().getValue().floatValue();
        }

        // 近/远裁剪面：可以用默认值或配置值（先给一套常用默认）
        float near = 0.05f;
        float far = client.gameRenderer.getViewDistance() * 16.0f;

        // 景深参数：目前原版没有，先给默认值
        boolean dofEnabled = false;
        float focusDistance = 10.0f;
        float aperture = 2.8f;

        return new CameraSample(
            pos,
            yaw,
            pitch,
            roll,
            fov,
            near,
            far,
            dofEnabled,
            focusDistance,
            aperture
        );
    }
}
