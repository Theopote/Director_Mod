package com.chronoblocks.mixin;

import com.chronoblocks.client.renderer.BlockColorRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.IdList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockColorsMixin
 *
 * <p>修复：动画/遮挡 overlay 渲染走 renderBlockAsEntity 时，BlockColors#getColor 可能拿不到 world/pos，
 * 导致草/树叶等 biome tint 方块颜色与真实落地后的世界渲染不一致（通常更深一点）。</p>
 *
 * <p>策略：当 world/pos 为空时，回退到 {@link BlockColorRenderContext} 提供的 ThreadLocal 上下文。</p>
 */
@Mixin(BlockColors.class)
public class BlockColorsMixin {

    @Final
    @Shadow
    private IdList<BlockColorProvider> providers;

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void chronoblocks$getColor(BlockState state,
                                       @Nullable BlockRenderView world,
                                       @Nullable BlockPos pos,
                                       int tintIndex,
                                       CallbackInfoReturnable<Integer> cir) {
        if (state == null) {
            return;
        }
        if (world != null && pos != null) {
            return;
        }

        BlockRenderView ctxWorld = BlockColorRenderContext.getWorld();
        BlockPos ctxPos = BlockColorRenderContext.getPos();
        if (ctxWorld == null || ctxPos == null) {
            return;
        }

        BlockColorProvider provider = this.providers.get(Registries.BLOCK.getRawId(state.getBlock()));
        if (provider == null) {
            cir.setReturnValue(-1);
            return;
        }
        cir.setReturnValue(provider.getColor(state, ctxWorld, ctxPos, tintIndex));
    }
}


