package com.chronoblocks.core.animation.impl;

import com.chronoblocks.core.animation.BlockAnimationEngine;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * 从下到上逐层出现动画引擎
 * 
 * <p>方块按照Y坐标从低到高的顺序逐层出现
 */
public class GrowUpAnimationEngine implements BlockAnimationEngine {
    
    @Override
    public float calculateBlockAppearTime(
        Container container,
        BlockPos blockPos,
        float clipStartTime,
        float clipDuration,
        float animationDuration,
        Map<String, Object> params
    ) {
        if (container == null || blockPos == null) {
            return clipStartTime;
        }
        
        // 获取容器的边界框
        Container.BoundingBox bbox = container.getBoundingBox();
        if (bbox == null || bbox.getHeight() <= 0) {
            return clipStartTime;
        }
        
        // 计算方块在容器中的相对Y位置（0.0 = 底部，1.0 = 顶部）
        int minY = bbox.getMin().getY();
        int maxY = bbox.getMax().getY();
        int blockY = blockPos.getY();
        
        if (maxY == minY) {
            // 所有方块在同一层，立即显示
            return clipStartTime;
        }
        
        // 计算该方块所在层相对于容器底部的比例
        float layerRatio = (float)(blockY - minY) / (float)(maxY - minY);
        
        // 根据层比例计算出现时间
        // 底部（layerRatio = 0）在 clipStartTime 显示
        // 顶部（layerRatio = 1）在 clipStartTime + animationDuration 显示
        float appearTime = clipStartTime + (layerRatio * animationDuration);
        
        return appearTime;
    }
    
    @Override
    public AnimationMode[] getSupportedModes() {
        return new AnimationMode[] { AnimationMode.GROW_UP };
    }
    
    @Override
    public String getEngineName() {
        return "GrowUpAnimation";
    }
}

