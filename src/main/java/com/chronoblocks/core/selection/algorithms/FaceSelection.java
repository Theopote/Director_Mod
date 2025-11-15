package com.chronoblocks.core.selection.algorithms;

import com.chronoblocks.core.selection.block_filters.BlockFilter;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;

/**
 * 面选择（Face Propagation）：
 * - 从命中的方块 + 命中面开始，在该平面内扩展（二维 BFS）
 * - 仅沿与法线垂直的 2 轴传播（如面=UP/DOWN，则在 XZ 平面传播）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class FaceSelection {

    private FaceSelection() {}

    public static Set<BlockPos> select(World w,
                                       BlockHitResult hit,
                                       BlockFilter filter,
                                       int maxCount) {
        if (w == null || hit == null) return Set.of();

        BlockPos start = hit.getBlockPos();
        Direction face = hit.getSide();

        // 确定二维传播轴
        AxisPair axes = axesFor(face);
        if (axes == null) return Set.of();

        // 种子是否通过过滤
        BlockState seed = w.getBlockState(start);
        if (!filter.test(w, start, seed)) return Set.of();

        Set<BlockPos> result = new LinkedHashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> dq = new ArrayDeque<>();

        dq.add(start);
        visited.add(start);

        while (!dq.isEmpty() && result.size() < maxCount) {
            BlockPos p = dq.poll();
            result.add(p);

            for (var off : axes.offsets()) {
                BlockPos np = p.add(off[0], off[1], off[2]);
                if (visited.add(np)) {
                    // 限制在同一平面：即沿法线方向的坐标与 start 相同
                    if (samePlane(face, start, np)) {
                        BlockState st = w.getBlockState(np);
                        if (filter.test(w, np, st)) {
                            dq.add(np);
                        }
                    }
                }
            }
        }

        return result;
    }

    private static boolean samePlane(Direction face, BlockPos a, BlockPos b) {
        return switch (face.getAxis()) {
            case X -> a.getX() == b.getX();
            case Y -> a.getY() == b.getY();
            case Z -> a.getZ() == b.getZ();
        };
    }

    private static AxisPair axesFor(Direction face) {
        return switch (face.getAxis()) {
            case X -> AxisPair.YZ;
            case Y -> AxisPair.XZ;
            case Z -> AxisPair.XY;
        };
    }

    private enum AxisPair {
        XZ(new int[][]{{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}}),
        XY(new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}}),
        YZ(new int[][]{{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}});

        private final int[][] offsets;
        AxisPair(int[][] o){ this.offsets=o; }
        public int[][] offsets(){ return offsets; }
    }
}
