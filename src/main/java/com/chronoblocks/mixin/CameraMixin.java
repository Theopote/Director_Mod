package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.camera.CameraSample;
import com.chronoblocks.ui.timeline.playback.camera.CameraRuntime;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CameraMixin v4.0 - 摄像机位置和旋转覆盖
 * <p>
 * ⭐ 优化：
 * <ul>
 *   <li>使用 @Invoker 替代反射，提高版本兼容性和安全性</li>
 *   <li>在 Camera.update 之后覆盖位置和角度，不破坏原版逻辑</li>
 *   <li>注意：Roll（侧倾角）在 GameRendererMixin 中通过 MatrixStack 应用</li>
 * </ul>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    /**
     * ★★ 更新摄像机后，再覆盖位置和角度，而不是取消 update。
     * 这样就不会破坏 FOV 计算、平滑等逻辑。
     * <p>
     * ⚠️ 注意：Roll（侧倾角）不支持在 Camera 中直接设置，
     * 需要在 GameRendererMixin 中通过 MatrixStack 应用。
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void chronoblocks$applyCinematicCamera(
            BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
            float tickDelta, CallbackInfo ci
    ) {
        CameraRuntime runtime = CameraRuntime.getInstance();
        // ✅ 只有在电影模式下才覆盖 Camera（使用统一的条件判断）
        if (!runtime.isCinematicEnabled()) {
            return;
        }

        CameraSample sample = runtime.getActiveSample();
        if (sample == null) return;

        // overwrite camera pos
        chronoblocks$invokeSetPos(sample.position().x, sample.position().y, sample.position().z);

        // overwrite yaw/pitch (Roll 在 GameRendererMixin 中处理)
        chronoblocks$invokeSetRotation(sample.yaw(), sample.pitch());
    }

    /**
     * ⭐ 使用 @Invoker 安全地调用 Camera.setPos
     * 这比反射更安全，且能被 Mixin 工具链检查
     */
    @org.spongepowered.asm.mixin.gen.Invoker("setPos")
    public abstract void chronoblocks$invokeSetPos(double x, double y, double z);

    /**
     * ⭐ 使用 @Invoker 安全地调用 Camera.setRotation
     * 这比反射更安全，且能被 Mixin 工具链检查
     */
    @org.spongepowered.asm.mixin.gen.Invoker("setRotation")
    public abstract void chronoblocks$invokeSetRotation(float yaw, float pitch);
}
