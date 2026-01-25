package com.chronoblocks.core.selection.block_filters;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 🔍 方块过滤器接口
 * <p>
 * 用于定义各种方块匹配规则，可被魔棒工具、类型过滤工具等使用。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
@FunctionalInterface
public interface BlockFilter {
    
    /**
     * 测试方块状态是否匹配过滤器
     * 
     * @param world 世界实例
     * @param pos 方块位置
     * @param state 方块状态
     * @return true 如果匹配，false 否则
     */
    boolean test(World world, BlockPos pos, BlockState state);
    
    /**
     * 组合多个过滤器（AND 逻辑）
     */
    default BlockFilter and(BlockFilter other) {
        return (world, pos, state) -> this.test(world, pos, state) && other.test(world, pos, state);
    }
    
    /**
     * 组合多个过滤器（OR 逻辑）
     */
    default BlockFilter or(BlockFilter other) {
        return (world, pos, state) -> this.test(world, pos, state) || other.test(world, pos, state);
    }
    
    /**
     * 反转过滤器
     */
    default BlockFilter negate() {
        return (world, pos, state) -> !this.test(world, pos, state);
    }
}

