package com.chronoblocks.core.animation.sequence.impl;

import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.SequenceStrategy;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量执行策略
 * 
 * <p>将方块分成若干批，每批N个方块同时执行。
 * 批次之间按顺序执行。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>与 GROW_UP 组合：从下到上，每批5个方块</li>
 *   <li>用于加快动画速度，同时保持一定的顺序感</li>
 * </ul>
 * 
 * <p>参数：
 * <ul>
 *   <li>batchSize：每批方块数（默认1，即一个接一个）</li>
 * </ul>
 */
public class BatchStrategy implements SequenceStrategy {
    
    @Override
    public List<BlockPos> order(List<BlockPos> blocks, Container container, SequenceParameters params) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 获取批量大小（默认1）
        int batchSize = params != null ? params.getBatchSize() : 1;
        if (batchSize < 1) {
            batchSize = 1;
        }
        
        // 创建新的列表（保持原顺序，但按批次组织）
        // 注意：这里只是保持原顺序，实际的批次逻辑在动画引擎中处理
        // 或者可以在这里就按批次重新组织列表
        List<BlockPos> result = new ArrayList<>(blocks);
        
        // 如果批量大小为1，等同于顺序执行
        if (batchSize == 1) {
            return result;
        }
        
        // 按批次重新组织列表（保持批次内的顺序，批次之间按顺序）
        // 例如：batchSize=3, [1,2,3,4,5,6,7] -> [1,2,3,4,5,6,7]（顺序不变，但批次信息在参数中）
        // 注意：批次逻辑主要在动画引擎中处理，这里只负责排序
        return result;
    }
    
    @Override
    public String getStrategyName() {
        return "Batch";
    }
    
    @Override
    public String getDisplayName() {
        return "批量执行";
    }
    
    @Override
    public String getDescription() {
        return "将方块分成若干批，每批N个方块同时执行";
    }
}

