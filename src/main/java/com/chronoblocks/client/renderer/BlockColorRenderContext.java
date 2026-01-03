package com.chronoblocks.client.renderer;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

/**
 * BlockColorRenderContext
 *
 * <p>用于在“非世界网格渲染路径”（例如 renderBlockAsEntity 的 overlay 渲染）中，
 * 仍然能够给 BlockColors 提供 world + pos，从而正确计算草/树叶等 biome tint 颜色。</p>
 *
 * <p>实现方式：ThreadLocal 存一个当前渲染上下文，在渲染调用前 push，结束后 pop。</p>
 */
public final class BlockColorRenderContext {

    private static final ThreadLocal<BlockRenderView> TL_WORLD = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos> TL_POS = new ThreadLocal<>();

    private BlockColorRenderContext() {
    }

    public static void push(BlockRenderView world, BlockPos pos) {
        TL_WORLD.set(world);
        TL_POS.set(pos);
    }

    public static void pop() {
        TL_WORLD.remove();
        TL_POS.remove();
    }

    public static BlockRenderView getWorld() {
        return TL_WORLD.get();
    }

    public static BlockPos getPos() {
        return TL_POS.get();
    }
}


