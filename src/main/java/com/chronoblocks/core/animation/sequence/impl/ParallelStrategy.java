package com.chronoblocks.core.animation.sequence.impl;

import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.SequenceStrategy;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 并行执行策略
 * 
 * <p>所有方块同时执行动画（不进行排序）。
 * 这个策略主要用于瞬间出现等场景，所有方块应该在同一时刻显示。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>与 INSTANT 组合：所有方块瞬间同时出现</li>
 *   <li>用于不需要顺序效果的动画</li>
 * </ul>
 * 
 * <p>注意：此策略不进行排序，直接返回原始列表。
 * 实际的"同时执行"效果由动画引擎控制（所有方块的出现时间相同）。
 */
public class ParallelStrategy implements SequenceStrategy {
    
    @Override
    public List<BlockPos> order(List<BlockPos> blocks, Container container, SequenceParameters params) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 并行执行：不进行排序，直接返回原始列表
        // 所有方块应该在同一时刻执行，顺序无关紧要
        return new ArrayList<>(blocks);
    }
    
    @Override
    public String getStrategyName() {
        return "Parallel";
    }
    
    @Override
    public String getDisplayName() {
        return "并行执行";
    }
    
    @Override
    public String getDescription() {
        return "所有方块同时执行动画，不进行排序，用于瞬间出现等场景";
    }
}

