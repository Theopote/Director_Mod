package com.chronoblocks.core.selection.algorithms;

import com.chronoblocks.core.selection.block_filters.BlockFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 区域与几何体选择工具：
 * - AABB 内筛选（按过滤器）
 * - 球、圆柱（实用形状）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class RegionSelectors {
    private RegionSelectors(){}

    public static Set<BlockPos> selectInBox(World w, Box box, BlockFilter filter) {
        Set<BlockPos> out = new LinkedHashSet<>();
        int minX = (int)Math.floor(box.minX), maxX = (int)Math.floor(box.maxX) - 1;
        int minY = (int)Math.floor(box.minY), maxY = (int)Math.floor(box.maxY) - 1;
        int minZ = (int)Math.floor(box.minZ), maxZ = (int)Math.floor(box.maxZ) - 1;

        for (int x=minX; x<=maxX; x++)
            for (int y=minY; y<=maxY; y++)
                for (int z=minZ; z<=maxZ; z++) {
                    BlockPos p = new BlockPos(x,y,z);
                    if (filter.test(w,p,w.getBlockState(p))) out.add(p);
                }
        return out;
        // 若体积巨大，可分层分批交给批处理执行器
    }

}
