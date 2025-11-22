package com.chronoblocks.core.animation.impl;

import com.chronoblocks.core.animation.BlockAnimationEngine;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * 从中心到周边逐渐扩散动画引擎
 * 
 * <p>方块按照距离容器中心的距离从近到远依次出现
 */
public class ExpandAnimationEngine implements BlockAnimationEngine {
    
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
        
        // 获取容器中心点
        net.minecraft.util.math.BlockPos center = container.getCenter();
        if (center == null) {
            return clipStartTime;
        }
        
        // 计算方块到中心的距离
        double dx = blockPos.getX() - center.getX();
        double dy = blockPos.getY() - center.getY();
        double dz = blockPos.getZ() - center.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        // 获取容器的最大半径（从中心到最远方块的距离）
        Container.BoundingBox bbox = container.getBoundingBox();
        if (bbox == null) {
            return clipStartTime;
        }
        
        double maxRadius = calculateMaxRadius(bbox, center);
        if (maxRadius <= 0) {
            return clipStartTime;
        }
        
        // 计算方块相对位置（0.0 = 中心，1.0 = 边缘）
        float radiusRatio = (float)(distance / maxRadius);
        
        // 根据距离比例计算出现时间
        // 中心（radiusRatio = 0）在 clipStartTime 显示
        // 边缘（radiusRatio = 1）在 clipStartTime + animationDuration 显示

        return clipStartTime + (radiusRatio * animationDuration);
    }
    
    /**
     * 计算容器的最大半径（从中心到最远角的距离）
     */
    private double calculateMaxRadius(Container.BoundingBox bbox, net.minecraft.util.math.BlockPos center) {
        // 计算边界框的8个角到中心的距离，取最大值
        double maxDist = 0.0;
        
        net.minecraft.util.math.BlockPos min = bbox.getMin();
        net.minecraft.util.math.BlockPos max = bbox.getMax();
        
        for (int x : new int[]{min.getX(), max.getX()}) {
            for (int y : new int[]{min.getY(), max.getY()}) {
                for (int z : new int[]{min.getZ(), max.getZ()}) {
                    double dx = x - center.getX();
                    double dy = y - center.getY();
                    double dz = z - center.getZ();
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    maxDist = Math.max(maxDist, dist);
                }
            }
        }
        
        return maxDist;
    }
    
    @Override
    public AnimationMode[] getSupportedModes() {
        return new AnimationMode[] { AnimationMode.EXPAND };
    }
    
    @Override
    public String getEngineName() {
        return "ExpandAnimation";
    }
}

