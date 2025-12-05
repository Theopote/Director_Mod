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
            // 直接写入玩家实体会叠加眼高，导致相机抬高；这里仅用于特殊场景，尽量避免。
            client.player.setPos(sample.position().x, sample.position().y, sample.position().z);
            client.player.setYaw(sample.yaw());
            client.player.setPitch(sample.pitch());
        }
    }

    /**
     * 将 CameraSample 同步到玩家实体（补偿眼高），用于从时间线返回玩家控制时避免视角抬升。
     */
    public static void applySampleToPlayerEntity(MinecraftClient client, CameraSample sample) {
        if (client == null || client.player == null || sample == null) {
            return;
        }
        double eye = client.player.getStandingEyeHeight();
        // 只补偿竖直方向的眼高（MC 相机在头部中心，默认无横向偏移）
        double px = sample.position().x;
        double py = sample.position().y - eye;
        double pz = sample.position().z;
        client.player.setPos(px, py, pz);
        client.player.setYaw(sample.yaw());
        client.player.setPitch(sample.pitch());
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

    /**
     * ⭐ 从游戏摄像机直接采样当前状态（用于设置关键帧时立即获取玩家视角）
     * 
     * <p>用途：
     * <ul>
     *   <li>设置关键帧时，如果当前 owner 是 TIMELINE，切回 PLAYER 后需要立即获取玩家视角</li>
     *   <li>不等待 CameraMixin 在下一帧更新 CameraRuntime.currentSample</li>
     * </ul>
     * 
     * <p>⭐ 关键修复：从 Camera 对象读取，而不是从 player 读取
     * - Camera 的 yaw/pitch 是实际渲染时使用的值
     * - Player 的 yaw/pitch 可能和 Camera 有差异（特别是在第三人称视角时）
     * - 确保设置关键帧时捕获的朝向和播放时一致
     * 
     * @param client MinecraftClient 实例
     * @return 当前游戏摄像机的状态快照，如果无法获取则返回 null
     */
    public static CameraSample sampleFromGameCamera(MinecraftClient client) {
        if (client == null || client.player == null || client.gameRenderer == null) {
            return null;
        }

        // ⭐ 关键修复：从 Camera 对象读取，而不是从 player 读取
        // Camera 的 yaw/pitch 是实际渲染时使用的值，确保和播放时一致
        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }

        // 从 Camera 对象获取位置和旋转（这是实际渲染时使用的值）
        net.minecraft.util.math.Vec3d pos = camera.getPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        // 从 GameRenderer 获取当前渲染 FOV（包含动态 FOV），保持时间线与实时画面一致
        float tickDelta = 0f;
        if (client.getRenderTickCounter() != null) {
            tickDelta = client.getRenderTickCounter().getTickDelta(false);
        }
        float baseFov;
        if (client.gameRenderer instanceof com.chronoblocks.mixin.accessor.GameRendererAccessor accessor) {
            baseFov = accessor.chronoblocks$invokeGetFov(camera, tickDelta, true);
        } else {
            baseFov = (float) client.options.getFov().getValue();
        }

        // 创建 CameraSample（使用默认值填充其他参数）
        return new CameraSample(
                pos,
                yaw,
                pitch,
                0.0f,      // roll 对玩家视角无意义
                baseFov,
                0.05f,     // near plane
                1024.0f,   // far plane
                false,      // depth of field disabled
                10.0f,     // focus distance
                2.8f       // aperture
        );
    }
}
