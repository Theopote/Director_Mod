package com.chronoblocks.client.gizmo.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Set;

/**
 * 🧭 GizmoVoxelizer - Gizmo 体素化工具
 * 
 * 将 Box 映射为方块集合（用于把 targetBox 写入 SelectionManager）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class GizmoVoxelizer {
    
    /**
     * 将 Box 近似体素化为 BlockPos 集合
     * 
     * 用于把 targetBox 写入 SelectionManager
     * 
     * @param box 包围盒
     * @return 方块位置集合
     */
    public static Set<BlockPos> voxelize(Box box) {
        Set<BlockPos> out = new HashSet<>();
        
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.ceil(box.maxX) - 1;
        int maxY = (int) Math.ceil(box.maxY) - 1;
        int maxZ = (int) Math.ceil(box.maxZ) - 1;
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    out.add(new BlockPos(x, y, z));
                }
            }
        }
        
        return out;
    }
}

