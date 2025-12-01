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

    /**
     * 将 CameraSample 应用到游戏摄像机（玩家视角）。
     * ⭐ 用于"无缝衔接"场景：从时间线预览退出时，将预览的摄像机状态应用到玩家视角。
     * 
     * ⭐ 关键修复：不修改 FOV，因为 FOV 是通过 GameRendererMixin 覆盖的，不应该修改 client.options.getFov()
     * 
     * @param client MinecraftClient 实例
     * @param sample 要应用的摄像机样本
     */
    public static void applySampleToGameCamera(MinecraftClient client, CameraSample sample) {
        if (client == null || sample == null) {
            return;
        }

        // 应用位置和角度：如果玩家存在，使用 setPos 和 setYaw/setPitch
        if (client.player != null) {
            client.player.setPos(sample.position().x, sample.position().y, sample.position().z);
            client.player.setYaw(sample.yaw());
            client.player.setPitch(sample.pitch());
            // 注意：roll 在原版中不支持，跳过
        }

        // ⭐ 关键修复：不修改 FOV！
        // 原因：
        // 1. 在电影模式下，FOV 是通过 GameRendererMixin.getFov() 覆盖的，不会修改 client.options.getFov()
        // 2. 如果修改 client.options.getFov()，会永久改变玩家的 FOV 设置
        // 3. 退出电影模式时，GameRendererMixin 不再覆盖，应该使用玩家原始的 FOV（client.options.getFov()）
        // 所以这里不应该修改 FOV
    }
}
