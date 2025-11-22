package com.chronoblocks.core.animation.sequence.impl;

import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.SequenceStrategy;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 顺序执行策略
 * 
 * <p>按照列表顺序一个接一个执行方块动画。
 * 这是最简单的策略，直接返回原始列表的顺序。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>与 GROW_UP 组合：从下到上，一个接一个</li>
 *   <li>与 EXPAND 组合：从中心到边缘，一个接一个</li>
 * </ul>
 */
public class OneByOneStrategy implements SequenceStrategy {
    
    @Override
    public List<BlockPos> order(List<BlockPos> blocks, Container container, SequenceParameters params) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 顺序执行：直接返回列表顺序（保持原顺序）
        // 注意：如果输入列表本身是无序的（如 HashSet），则顺序是随机的
        // 如果需要特定顺序，应该先对列表进行排序
        return new ArrayList<>(blocks);
    }
    
    @Override
    public String getStrategyName() {
        return "OneByOne";
    }
    
    @Override
    public String getDisplayName() {
        return "顺序执行";
    }
    
    @Override
    public String getDescription() {
        return "按照列表顺序一个接一个执行方块动画";
    }
}

