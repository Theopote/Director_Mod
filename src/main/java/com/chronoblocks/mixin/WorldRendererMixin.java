package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.state.WorldRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldRendererMixin v2.0
 * ============================================================
 * 在ChronoBlocks模式下：
 * - 完全取消Minecraft默认的方块高亮边框渲染
 * - 原版通过renderTargetBlockOutline()绘制的蓝色边框被拦截
 * - 配合ChronoBlocksInputSystem的自定义边框渲染
 * <p>
 * 注意：
 * - 方块渲染拦截已移至 BlockRenderManagerMixin
 * - 云颜色控制：Minecraft 1.21.4 中 WorldRenderer 没有 getCloudColor() 方法，
 *   云颜色控制暂时不可用。云量（cloudCoverage）可以通过天空颜色暗度（stormDarkness）
 *   和雾效果间接体现。
 * <p>
 * 基于Minecraft 1.21.4源码中的WorldRenderer类方法签名
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    /**
     * 🚫 拦截原版方块边框渲染
     * 在ChronoBlocks模式下完全取消原版蓝色边框
     * 基于Minecraft 1.21.4源码：使用renderTargetBlockOutline方法拦截
     */
    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onRenderTargetBlockOutline(VertexConsumerProvider.Immediate vertexConsumers,
                                           MatrixStack matrices,
                                           boolean translucent,
                                           WorldRenderState renderState,
                                           CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            // 🚫 在ChronoBlocks模式下完全取消原版方块边框渲染
            ci.cancel();
        }
    }

    // ⭐ v2.0：Weather - 云颜色控制
    // 
    // 注意：Minecraft 1.21.4 中 WorldRenderer 没有 getCloudColor() 方法。
    // 云的颜色可能通过其他方式控制（例如 CloudRenderer 或渲染管线中的其他方法）。
    // 如果需要实现云颜色控制，需要：
    // 1. 查找正确的云渲染方法（可能是 renderClouds 或类似方法）
    // 2. 或者通过 Shader 或其他渲染管线方式控制
    // 
    // 目前云颜色控制暂时不可用，但云量（cloudCoverage）可以通过其他视觉效果间接体现。
    // 例如：通过天空颜色暗度（stormDarkness）和雾效果来模拟云的影响。

}
