package com.chronoblocks.core.util;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/**
 * 🔧 SnapUtil - 方块网格对齐工具类
 * 
 * 功能：
 * - 一方块步进（网格对齐）
 * - 保证厚度 >= 1 方块
 * - 沿轴扩展/收缩盒子
 * 
 * @author ChronoBlocks
 * @version 1.0
 */
public final class SnapUtil {
    
    private SnapUtil() {}
    
    /**
     * 计算轴向步进：把"当前参考值 curr"相对"基准 far/near"映射为 ±1, ±2 ... 的格点步进
     * 
     * @param far 远端边界
     * @param near 近端边界
     * @param curr 当前值
     * @return 步进量（整数，>0 扩展，<0 收缩）
     */
    public static int computeUnitDelta(double far, double near, double curr) {
        // 计算相对"远端面"的偏移，并按整数格取整
        double raw = curr - far;
        
        // 以 1 方块为步进单位
        int steps = (int) Math.round(raw);
        
        // 如果偏移很小，允许 ±1 步进，提升手感
        if (steps == 0 && Math.abs(raw) > 0.1) {
            steps = raw > 0 ? 1 : -1;
        }
        
        return steps;
    }
    
    /**
     * 保证盒子在三个方向的厚度 >= 1 方块
     * 
     * Box 的 max 边界是 exclusive（不包含），所以对于整数坐标：
     * - minX=0, maxX=1 表示厚度为 1 方块
     * - minX=0, maxX=0.5 表示厚度 < 1，需要修正为 maxX=1
     * 
     * @param box 原始盒子
     * @return 修正后的盒子（保证最小厚度）
     */
    public static Box ensureMinThicknessOne(Box box) {
        double minX = box.minX;
        double maxX = box.maxX;
        double minY = box.minY;
        double maxY = box.maxY;
        double minZ = box.minZ;
        double maxZ = box.maxZ;
        
        // 修正每个方向的厚度
        if (maxX - minX < 1.0) {
            maxX = minX + 1.0;
        }
        if (maxY - minY < 1.0) {
            maxY = minY + 1.0;
        }
        if (maxZ - minZ < 1.0) {
            maxZ = minZ + 1.0;
        }
        
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * 沿某轴按 1 方块步进扩展或收缩盒子，保持厚度 >= 1
     * 
     * @param base 基础盒子
     * @param axis 轴向（Direction.EAST/WEST/UP/DOWN/SOUTH/NORTH）
     * @param steps 步进量（>0 扩展，<0 收缩）
     * @return 调整后的盒子
     */
    public static Box expandBoxByAxisOneBlock(Box base, Direction axis, int steps) {
        if (steps == 0 || axis == null) return base;
        
        double minX = base.minX;
        double maxX = base.maxX;
        double minY = base.minY;
        double maxY = base.maxY;
        double minZ = base.minZ;
        double maxZ = base.maxZ;
        
        // 根据轴向调整边界
        switch (axis) {
            case EAST -> maxX += steps;
            case WEST -> minX -= steps;
            case UP   -> maxY += steps;
            case DOWN -> minY -= steps;
            case SOUTH-> maxZ += steps;
            case NORTH-> minZ -= steps;
        }
        
        // 确保 min <= max（如果反了则交换）
        double temp;
        if (minX > maxX) {
            temp = minX; minX = maxX; maxX = temp;
        }
        if (minY > maxY) {
            temp = minY; minY = maxY; maxY = temp;
        }
        if (minZ > maxZ) {
            temp = minZ; minZ = maxZ; maxZ = temp;
        }
        
        Box adjusted = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        return ensureMinThicknessOne(adjusted);
    }
}
