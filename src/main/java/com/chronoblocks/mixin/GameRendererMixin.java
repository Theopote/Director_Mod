package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.ui.timeline.playback.camera.CameraRuntime;
import com.chronoblocks.ui.timeline.playback.camera.CameraSample;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 🎬 GameRendererMixin - ChronoBlocks 相关渲染控制
 *
 * - 隐藏第一人称手臂与物品渲染（当需要时）
 * - 控制玩家模型可见性
 * - 在电影模式下覆盖 FOV
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(Camera camera, float tickDelta, org.joml.Matrix4f matrix4f, CallbackInfo ci) {
        if (ChronoBlocksClientController.getInstance().shouldHideCrosshair()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void onRenderWorldHead(RenderTickCounter tickCounter, CallbackInfo ci) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        var controller = ChronoBlocksClientController.getInstance();
        if (mc.player != null) {
            mc.player.setInvisible(controller.shouldHideCrosshair());
        }
    }

    /**
     * ⭐ 统一摄像机系统 v2.2：仅在电影模式下覆盖 FOV
     *
     * - 电影模式：使用 sample 的 FOV（时间线定义）
     * - 游戏模式：完全使用原版 FOV，不干预
     */
    @Inject(
            method = "getFov",
            at = @At("HEAD"),
            cancellable = true
    )
    private void chronoblocks$overrideFov(Camera camera, float tickDelta, boolean changingFov,
                                          CallbackInfoReturnable<Float> cir) {
        CameraRuntime runtime = CameraRuntime.getInstance();
        // ✅ 只有电影模式才覆盖 FOV（使用统一的条件判断）
        if (!runtime.isCinematicEnabled()) {
            return; // 非电影模式 → 使用原版 FOV
        }

        CameraSample sample = runtime.getActiveSample();
        if (sample != null) {
            cir.setReturnValue(sample.fov());
        }
    }
}
