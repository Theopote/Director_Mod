package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 🚫 WorldRendererMixin - 阻止原版方块边框渲染
 * ==============================================
 * 在ChronoBlocks模式下：
 * - 完全取消Minecraft默认的方块高亮边框渲染
 * - 原版通过renderTargetBlockOutline()绘制的蓝色边框被拦截
 * - 配合ChronoBlocksInputSystem的自定义边框渲染
 *
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
    private void onRenderTargetBlockOutline(Camera camera, VertexConsumerProvider.Immediate vertexConsumers, 
                                           MatrixStack matrices, boolean translucent, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            // 🚫 在ChronoBlocks模式下完全取消原版方块边框渲染
            ci.cancel();
        }
    }
}
