package com.chronoblocks.core.selection.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Set;

/**
 * 🧭 边界框计算工具
 * 
 * 计算方块集合的最小包围盒（AABB）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class BoundingBoxCalculator {
    
    /**
     * 计算方块集合的边界框
     * 
     * @param blocks 方块位置集合
     * @return 最小包围盒，如果集合为空则返回 null
     */
    public static Box calculate(Set<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        // 扩展到方块边界（每个方块占据1x1x1的空间）
        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
    
    /**
     * 计算两个边界框的并集
     */
    public static Box union(Box box1, Box box2) {
        if (box1 == null) return box2;
        if (box2 == null) return box1;
        
        return new Box(
            Math.min(box1.minX, box2.minX),
            Math.min(box1.minY, box2.minY),
            Math.min(box1.minZ, box2.minZ),
            Math.max(box1.maxX, box2.maxX),
            Math.max(box1.maxY, box2.maxY),
            Math.max(box1.maxZ, box2.maxZ)
        );
    }
    
    /**
     * 计算边界框的体积
     */
    public static double volume(Box box) {
        if (box == null) return 0.0;
        
        double dx = box.maxX - box.minX;
        double dy = box.maxY - box.minY;
        double dz = box.maxZ - box.minZ;
        
        return dx * dy * dz;
    }
    
    /**
     * 计算边界框的中心点
     */
    public static BlockPos center(Box box) {
        if (box == null) return BlockPos.ORIGIN;
        
        int cx = (int) ((box.minX + box.maxX) / 2);
        int cy = (int) ((box.minY + box.maxY) / 2);
        int cz = (int) ((box.minZ + box.maxZ) / 2);
        
        return new BlockPos(cx, cy, cz);
    }
    
    /**
     * 扩展边界框
     */
    public static Box expand(Box box, double amount) {
        if (box == null) return null;
        
        return box.expand(amount);
    }
    
    /**
     * 获取边界框的尺寸信息
     */
    public static BoxDimensions getDimensions(Box box) {
        if (box == null) {
            return new BoxDimensions(0, 0, 0, 0.0);
        }
        
        int width = (int) (box.maxX - box.minX);
        int height = (int) (box.maxY - box.minY);
        int depth = (int) (box.maxZ - box.minZ);
        double volume = volume(box);
        
        return new BoxDimensions(width, height, depth, volume);
    }
    
    /**
     * 边界框尺寸信息
     */
    public record BoxDimensions(int width, int height, int depth, double volume) {
        @Override
        public String toString() {
            return String.format("%d×%d×%d (体积: %.0f)", width, height, depth, volume);
        }
    }
}

