package com.director.mixin;

import com.director.core.container.visibility.BlockVisualStateManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 🔥 BlockRenderManagerMixin - 拦截世界方块渲染，隐藏被标记为 suppressWorldRendering 的方块
 * <p>
 * 适配 Minecraft 1.21.10:
 *  - 目标方法签名为 void renderBlock(..., List<BlockModelPart> parts)
 *  - 因此这里必须使用 CallbackInfo 而不是 CallbackInfoReturnable
 *  - 参数列表必须完全匹配目标方法（除了最后的 CallbackInfo）
 */
@Mixin(BlockRenderManager.class)
public class BlockRenderManagerMixin {

    /**
     * 拦截区块构建阶段的方块渲染调用。
     * <p>
     * 方法签名（1.21.10 Yarn）：
     * <p>
     *   void renderBlock(
     *       BlockState state,
     *       BlockPos pos,
     *       BlockRenderView world,
     *       MatrixStack matrices,
     *       VertexConsumer vertexConsumer,
     *       boolean cull,
     *       List<BlockModelPart> parts
     *   )
     */
    @Inject(
        method = "renderBlock(Lnet/minecraft/block/BlockState;" +
                 "Lnet/minecraft/util/math/BlockPos;" +
                 "Lnet/minecraft/world/BlockRenderView;" +
                 "Lnet/minecraft/client/util/math/MatrixStack;" +
                 "Lnet/minecraft/client/render/VertexConsumer;" +
                 "Z" +
                 "Ljava/util/List;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void director$onRenderBlock(
            BlockState state,
            BlockPos pos,
            BlockRenderView world,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            List<?> parts,
            CallbackInfo ci
    ) {
        BlockVisualStateManager visualStateManager = BlockVisualStateManager.getInstance();
        if (visualStateManager == null || pos == null) {
            return;
        }

        BlockVisualStateManager.BlockVisualState visualState = visualStateManager.getBlockState(pos);
        if (visualState != null && visualState.shouldSuppressWorldRendering()) {
            // 阻止该方块被烘焙进区块网格，由我们的覆盖层渲染接管
            ci.cancel();
        }
    }
}
