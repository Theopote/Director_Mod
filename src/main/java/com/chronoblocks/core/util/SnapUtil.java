package com.chronoblocks.core.util;

import net.minecraft.util.math.Box;

/**
 * 🔧 SnapUtil - 方块网格对齐工具类
 * <p>
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
     * 保证盒子在三个方向的厚度 >= 1 方块
     * <p>
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
}