package com.chronoblocks.core.util;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 📐 CornerPicker - 角点拾取工具
 * 
 * 用于检测鼠标点击是否命中 Box 的 8 个角点之一
 * 
 * @author ChronoBlocks
 * @version 1.0
 */
public final class CornerPicker {
    
    private CornerPicker() {}
    
    /**
     * 角点拾取结果
     */
    public static class CornerPickResult {
        private final int cornerIndex;  // 0-7，表示 8 个角点中的哪一个
        private final Vec3d cornerPos;  // 角点世界坐标
        private final double distance; // 到点击点的距离
        
        public CornerPickResult(int cornerIndex, Vec3d cornerPos, double distance) {
            this.cornerIndex = cornerIndex;
            this.cornerPos = cornerPos;
            this.distance = distance;
        }
        
        public int getCornerIndex() {
            return cornerIndex;
        }
        
        public Vec3d getCornerPos() {
            return cornerPos;
        }
        
        public double getDistance() {
            return distance;
        }
    }
    
    /**
     * 拾取最近的角点
     * 
     * Box 的 8 个角点：
     * 0: (minX, minY, minZ)
     * 1: (maxX, minY, minZ)
     * 2: (minX, maxY, minZ)
     * 3: (maxX, maxY, minZ)
     * 4: (minX, minY, maxZ)
     * 5: (maxX, minY, maxZ)
     * 6: (minX, maxY, maxZ)
     * 7: (maxX, maxY, maxZ)
     * 
     * @param box 目标盒子
     * @param hitPoint 点击点（世界坐标，通常是方块中心）
     * @param radius 拾取半径（阈值）
     * @return 拾取结果，如果未命中任何角点则返回 null
     */
    public static CornerPickResult pickNearestCorner(Box box, Vec3d hitPoint, double radius) {
        if (box == null || hitPoint == null) return null;
        
        // 计算 8 个角点
        Vec3d[] corners = {
            new Vec3d(box.minX, box.minY, box.minZ), // 0
            new Vec3d(box.maxX, box.minY, box.minZ), // 1
            new Vec3d(box.minX, box.maxY, box.minZ), // 2
            new Vec3d(box.maxX, box.maxY, box.minZ), // 3
            new Vec3d(box.minX, box.minY, box.maxZ), // 4
            new Vec3d(box.maxX, box.minY, box.maxZ), // 5
            new Vec3d(box.minX, box.maxY, box.maxZ), // 6
            new Vec3d(box.maxX, box.maxY, box.maxZ)  // 7
        };
        
        int bestIndex = -1;
        double bestDistance2 = radius * radius + 1e-6;
        
        // 查找最近的角点
        for (int i = 0; i < corners.length; i++) {
            double d2 = corners[i].squaredDistanceTo(hitPoint);
            if (d2 <= bestDistance2) {
                bestDistance2 = d2;
                bestIndex = i;
            }
        }
        
        if (bestIndex < 0) return null;
        
        return new CornerPickResult(bestIndex, corners[bestIndex], Math.sqrt(bestDistance2));
    }
    
    /**
     * 获取角点的符号向量
     * 
     * 用于标识角点在 Box 中的位置：
     * - (-1, -1, -1) 表示 min 角
     * - (1, 1, 1) 表示 max 角
     * 
     * @param cornerIndex 角点索引（0-7）
     * @return 符号向量，如果索引无效则返回 null
     */
    public static net.minecraft.util.math.Vec3i getCornerSign(int cornerIndex) {
        if (cornerIndex < 0 || cornerIndex >= 8) return null;
        
        int x = (cornerIndex & 1) != 0 ? 1 : -1;  // 最低位：X
        int y = (cornerIndex & 2) != 0 ? 1 : -1;  // 次低位：Y
        int z = (cornerIndex & 4) != 0 ? 1 : -1;  // 高位：Z
        
        return new net.minecraft.util.math.Vec3i(x, y, z);
    }
}
