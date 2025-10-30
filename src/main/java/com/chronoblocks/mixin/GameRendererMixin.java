package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
}
