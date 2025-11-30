package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.ui.timeline.playback.camera.CameraRuntime;
import com.chronoblocks.ui.timeline.playback.camera.CinematicCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 🎬 GameRendererMixin - 阻止原版方块选取与玩家渲染
 * ==============================================
 * 在ChronoBlocks模式下：
 * - 隐藏第一人称手臂与物品渲染
 * - 控制玩家模型可见性
 * - 阻止原版方块高亮更新
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    /**
     * 🚫 拦截第一人称物品（手臂、武器、工具等）渲染
     * 在ChronoBlocks模式下完全隐藏玩家手臂
     * 基于Minecraft 1.21.4源码：renderHand(Camera camera, float tickDelta, Matrix4f matrix4f)
     */
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(Camera camera, float tickDelta, org.joml.Matrix4f matrix4f, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染第一人称手
        }
    }

    /**
     * 🎭 控制玩家模型可见性
     * 在ChronoBlocks模式下可选择隐藏玩家模型实现"自由摄影"模式
     * 基于Minecraft 1.21.4源码：renderWorld(RenderTickCounter renderTickCounter)
     */
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void onRenderWorldHead(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            // 可以通过隐藏玩家实体实现"自由摄影"模式
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.setInvisible(true);
            }
        } else {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.setInvisible(false);
            }
        }
    }

    /**
     * Override FOV while the cinematic camera is driving the view.
     * 修复：移除 method 中的硬编码描述符，解决 "Cannot resolve method" 警告
     */
    @Inject(
        method = "getFov", // 只保留方法名，让 Mixin 自动推断描述符
        at = @At("HEAD"),
        cancellable = true
    )
    private void chronoblocks$overrideFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (!CameraRuntime.getInstance().isCinematicActive()) {
            return;
        }

        CinematicCamera cineCam = CameraRuntime.getInstance().getCinematicCamera();
        double fov = (double) cineCam.getFov();
        cir.setReturnValue(fov);
    }
}
