package com.chronoblocks.client.gizmo.util;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

/**
 * 🧭 TransformUtil - 变换工具类
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TransformUtil {
    
    /**
     * 平移 Box
     * 
     * @param box 原始 Box
     * @param delta 平移向量
     * @return 平移后的 Box
     */
    public static Box translateBox(Box box, Vec3d delta) {
        return new Box(
            box.minX + delta.x, box.minY + delta.y, box.minZ + delta.z,
            box.maxX + delta.x, box.maxY + delta.y, box.maxZ + delta.z
        );
    }
    
    /**
     * 从角点缩放 Box（保持对角点固定，按 delta 推另一角）
     * 
     * @param box 原始 Box
     * @param delta 缩放增量
     * @param cornerSign 角点符号（X,Y,Z 为 1 表示 max 角，-1 表示 min 角）
     * @return 缩放后的 Box
     */
    public static Box resizeBoxFromCorner(Box box, Vec3d delta, Vec3i cornerSign) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;
        
        if (cornerSign.getX() > 0) {
            maxX += delta.x;
        } else {
            minX += delta.x;
        }
        
        if (cornerSign.getY() > 0) {
            maxY += delta.y;
        } else {
            minY += delta.y;
        }
        
        if (cornerSign.getZ() > 0) {
            maxZ += delta.z;
        } else {
            minZ += delta.z;
        }
        
        // 归一化，避免 min > max
        double nminX = Math.min(minX, maxX);
        double nmaxX = Math.max(minX, maxX);
        double nminY = Math.min(minY, maxY);
        double nmaxY = Math.max(minY, maxY);
        double nminZ = Math.min(minZ, maxZ);
        double nmaxZ = Math.max(minZ, maxZ);
        
        return new Box(nminX, nminY, nminZ, nmaxX, nmaxY, nmaxZ);
    }
    
    /**
     * 从面缩放 Box（保持对面固定）
     * 
     * @param box 原始 Box
     * @param delta 缩放增量
     * @param axis 缩放轴
     * @param isMaxFace true 表示缩放 max 面，false 表示缩放 min 面
     * @return 缩放后的 Box
     */
    public static Box resizeBoxFromFace(Box box, Vec3d delta, com.chronoblocks.client.gizmo.Axis axis, boolean isMaxFace) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;
        
        switch (axis) {
            case X -> {
                if (isMaxFace) maxX += delta.x;
                else minX += delta.x;
            }
            case Y -> {
                if (isMaxFace) maxY += delta.y;
                else minY += delta.y;
            }
            case Z -> {
                if (isMaxFace) maxZ += delta.z;
                else minZ += delta.z;
            }
        }
        
        // 归一化
        double nminX = Math.min(minX, maxX);
        double nmaxX = Math.max(minX, maxX);
        double nminY = Math.min(minY, maxY);
        double nmaxY = Math.max(minY, maxY);
        double nminZ = Math.min(minZ, maxZ);
        double nmaxZ = Math.max(minZ, maxZ);
        
        return new Box(nminX, nminY, nminZ, nmaxX, nmaxY, nmaxZ);
    }
}

