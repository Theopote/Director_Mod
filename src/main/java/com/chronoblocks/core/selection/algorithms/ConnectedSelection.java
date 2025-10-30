package com.chronoblocks.core.selection.algorithms;

import com.chronoblocks.core.selection.filter.SelectionFilter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;

/**
 * 连通域选择（Flood Fill）。
 * - 支持 6 / 26 连通（faceOnly = true => 6 方向；false => 带边角）
 * - 支持最大步数/数量限制（防爆内存）
 * - 自定义过滤器（类型/状态/Tag）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ConnectedSelection {

    private ConnectedSelection() {}

    public static Set<BlockPos> select(World world,
                                       BlockPos start,
                                       SelectionFilter filter,
                                       boolean faceOnly6,
                                       int maxCount,
                                       int maxDepth) {
        if (world == null || start == null) return Set.of();

        var result = new LinkedHashSet<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<Node>();

        BlockState startState = world.getBlockState(start);
        if (!filter.test(world, start, startState)) return Set.of();

        queue.add(new Node(start, 0));
        visited.add(start);

        Direction[] faceDirs = Direction.values();
        // 26 邻域 = 所有 offsets ∈ {-1,0,1}^3 \ {0,0,0}
        int[] d = {-1, 0, 1};

        while (!queue.isEmpty()) {
            Node n = queue.poll();
            BlockPos pos = n.pos;
            result.add(pos);
            if (result.size() >= maxCount) break;
            if (n.depth >= maxDepth) continue;

            if (faceOnly6) {
                for (Direction dir : faceDirs) {
                    BlockPos np = pos.offset(dir);
                    if (visited.add(np)) {
                        BlockState st = world.getBlockState(np);
                        if (filter.test(world, np, st)) {
                            queue.add(new Node(np, n.depth + 1));
                        }
                    }
                }
            } else {
                for (int dx : d) for (int dy : d) for (int dz : d) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos np = pos.add(dx, dy, dz);
                    if (visited.add(np)) {
                        BlockState st = world.getBlockState(np);
                        if (filter.test(world, np, st)) {
                            queue.add(new Node(np, n.depth + 1));
                        }
                    }
                }
            }
        }

        return result;
    }

    private record Node(BlockPos pos, int depth) {}
}
