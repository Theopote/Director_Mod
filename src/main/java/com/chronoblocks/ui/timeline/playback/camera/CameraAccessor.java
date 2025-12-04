package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.client.MinecraftClient;

/**
 * CameraAccessor - 封装所有对 Minecraft 原生 Camera 的读写。
 *
 * <p>⭐ 单一摄像机系统：
 * <ul>
 *   <li>采样：从游戏摄像机读取当前状态（位置/旋转/FOV等）</li>
 *   <li>应用：将 CameraSample 同步到游戏摄像机（位置/旋转/FOV）</li>
 *   <li>实际渲染时通过 CameraMixin 和 GameRendererMixin 覆盖</li>
 * </ul>
 */
public final class CameraAccessor {

    private CameraAccessor() {}

    /**
     * 将 CameraSample 同步到游戏摄像机（位置和旋转）。
     * 
     * <p>⭐ 单一摄像机系统：直接更新玩家位置和旋转，实现无跳变切换。
     * FOV 由 GameRendererMixin 在渲染时覆盖，不需要修改 client.options.getFov()。
     * 
     * @param client MinecraftClient 实例
     * @param sample 要应用的摄像机样本
     */
    public static void applySampleToGameCamera(MinecraftClient client, CameraSample sample) {
        if (client == null || sample == null) {
            return;
        }

        if (client.player != null) {
            client.player.setPos(sample.position().x, sample.position().y, sample.position().z);
            client.player.setYaw(sample.yaw());
            client.player.setPitch(sample.pitch());
        }
    }

    /**
     * 将 CameraSample 同步到游戏摄像机（包括位置、旋转和 FOV）。
     * 
     * <p>⭐ 统一摄像机系统：用于时间线接管时，将时间线状态同步到游戏摄像机。
     * 
     * @deprecated 统一摄像机系统中，Mixin 会从 CameraRuntime 读取并应用，不再需要直接修改游戏摄像机
     * 
     * @param client MinecraftClient 实例
     * @param sample 要应用的摄像机样本
     */
    @Deprecated
    public static void applySampleToGameCameraWithFov(MinecraftClient client, CameraSample sample) {
        applySampleToGameCamera(client, sample);
        if (client != null && client.options != null && client.options.getFov() != null && sample != null) {
            client.options.getFov().setValue((int) sample.fov());
        }
    }
}
