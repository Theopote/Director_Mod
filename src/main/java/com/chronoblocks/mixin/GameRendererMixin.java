package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.export.video.VideoRenderPipeline;
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
 * 🎬 GameRendererMixin - 统一相机系统版
 * <p>
 * - 继续隐藏手 / 玩家实体的逻辑（保持你原来的功能）
 * - 在 getFov(...) 时，如果当前 owner=TIMELINE，则使用 CameraRuntime.currentSample.fov
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void chronoblocks$onRenderHand(float tickDelta, boolean renderEntity, org.joml.Matrix4f matrix4f, CallbackInfo ci) {
        if (ChronoBlocksClientController.getInstance().shouldHideCrosshair()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void chronoblocks$onRenderWorldHead(RenderTickCounter tickCounter, CallbackInfo ci) {
        // 🎥 视频导出：在世界渲染开始前驱动时间线/相机（避免额外 renderWorld）
        VideoRenderPipeline.getInstance().onRenderWorldHead(tickCounter);

        var mc = net.minecraft.client.MinecraftClient.getInstance();
        var controller = ChronoBlocksClientController.getInstance();
        if (mc.player != null) {
            mc.player.setInvisible(controller.shouldHideCrosshair());
        }
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void chronoblocks$onRenderWorldTail(RenderTickCounter tickCounter, CallbackInfo ci) {
        // 🎥 视频导出：在世界渲染结束后（HUD 之前）抓取画面到指定分辨率 offscreen framebuffer
        VideoRenderPipeline.getInstance().onRenderWorldTail();
    }

    /**
     * 统一摄像机系统：只在时间线接管时覆盖 FOV。
     * 玩家模式下完全使用原版 FOV（包括动态 FOV 等）。
     */
    @Inject(
            method = "getFov",
            at = @At("HEAD"),
            cancellable = true
    )
    private void chronoblocks$overrideFov(Camera camera,
                                          float tickDelta,
                                          boolean changingFov,
                                          CallbackInfoReturnable<Float> cir) {
        CameraRuntime runtime = CameraRuntime.getInstance();

        // 只有时间线接管摄像机时才覆盖 FOV
        if (!runtime.isTimelineOwner()) {
            return;
        }

        CameraSample sample = runtime.getCurrentSample();
        if (sample != null) {
            cir.setReturnValue(sample.fov());
        }
    }
}
