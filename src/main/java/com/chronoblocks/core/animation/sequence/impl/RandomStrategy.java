package com.chronoblocks.core.animation.sequence.impl;

import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.SequenceStrategy;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 随机执行策略
 * 
 * <p>随机打乱方块顺序后执行。
 * 可以使用随机种子来确保结果可重复。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>与任何动画模式组合：随机顺序显示</li>
 *   <li>用于创造混乱、随机的视觉效果</li>
 * </ul>
 */
public class RandomStrategy implements SequenceStrategy {
    
    @Override
    public List<BlockPos> order(List<BlockPos> blocks, Container container, SequenceParameters params) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 创建新的列表（避免修改原列表）
        List<BlockPos> result = new ArrayList<>(blocks);
        
        // 使用随机种子（如果提供）或系统时间
        Random random;
        if (params != null && params.getRandomSeed() != null) {
            random = new Random(params.getRandomSeed());
        } else {
            random = new Random();
        }
        
        // 随机打乱顺序
        Collections.shuffle(result, random);
        
        return result;
    }
    
    @Override
    public String getStrategyName() {
        return "Random";
    }
    
    @Override
    public String getDisplayName() {
        return "随机执行";
    }
    
    @Override
    public String getDescription() {
        return "随机打乱顺序执行方块动画";
    }
}

