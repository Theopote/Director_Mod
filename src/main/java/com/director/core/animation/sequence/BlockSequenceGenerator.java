package com.director.core.animation.sequence;

import com.director.core.container.Container;
import net.minecraft.util.math.BlockPos;

import com.director.core.animation.sequence.evaluator.FillType;
import com.director.core.animation.sequence.evaluator.FloodSeedMode;
import com.director.core.animation.sequence.evaluator.OriginType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Flow + Fill 评分器封装，输出归一化时间顺序。 */
public final class BlockSequenceGenerator {

    private static final SequenceEvaluator EVALUATOR = new SequenceEvaluator();

    private BlockSequenceGenerator() {
    }

    public static List<BlockSequenceEntry> generate(List<BlockPos> sourceBlocks, Container container, SequenceParameters params) {
        // 旧入口：没有时间上下文（clipDuration/animDuration），保持原有“纯归一化顺序”输出
        if (container == null) {
            return List.of();
        }
        if (params == null) {
            return generateDefault(sourceBlocks, container);
        }

        List<BlockPos> blocks = sourceBlocks != null && !sourceBlocks.isEmpty()
            ? sourceBlocks
            : new ArrayList<>(container.getBlocksView());

        Container.BoundingBox bbox = container.getBoundingBox();

        List<BlockSequenceItem> items = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            BlockSequenceItem item = new BlockSequenceItem(blocks.get(i), i);
            SequenceEvaluator.Evaluation evaluation = EVALUATOR.evaluate(blocks.get(i), bbox, params);
            item.setGroupId(evaluation.layerIndex());
            item.setScore(evaluation.finalScore());
            items.add(item);
        }

        // FLOOD 后处理：用 BFS 波前距离替换 fillScore
        if (params.getFillType() == FillType.FLOOD) {
            applyFloodBFS(items, params, bbox);
        }

        Comparator<BlockSequenceItem> scoreOrder = Comparator.comparingDouble(BlockSequenceItem::getScore)
                .thenComparingLong(item -> item.getPos().asLong());
        boolean grouped = params.getTimeMappingStrategy() == TimeMappingStrategy.GROUP_FIRST
                || params.getTimeMappingStrategy() == TimeMappingStrategy.STAGGERED;
        if (grouped && params.getConcurrencyMode() == ConcurrencyMode.ONE_BY_ONE) {
            Comparator<BlockSequenceItem> groupOrder = Comparator.comparingInt(BlockSequenceItem::getGroupId);
            if (params.isReverse()) groupOrder = groupOrder.reversed();
            items.sort(groupOrder.thenComparing(scoreOrder));
        } else {
            items.sort(scoreOrder);
        }

        int n = items.size();
        List<BlockSequenceEntry> result = new ArrayList<>(n);
        if (n == 0) {
            return result;
        }

        Map<Integer, Integer> groupSizes = new HashMap<>();
        for (BlockSequenceItem item : items) {
            groupSizes.merge(item.getGroupId(), 1, Integer::sum);
        }
        Map<Integer, Integer> groupCounters = new HashMap<>();

        for (int i = 0; i < n; i++) {
            BlockSequenceItem item = items.get(i);
            float normalized = n == 1 ? 0.0f : (float) i / (float) (n - 1);
            int groupId = item.getGroupId();
            int orderInGroup = groupCounters.merge(groupId, 1, Integer::sum);
            int groupSize = Math.max(1, groupSizes.getOrDefault(groupId, 1));
            float groupNormalized = groupSize == 1 ? 0.0f : (float) (orderInGroup - 1) / (float) (groupSize - 1);
            result.add(new BlockSequenceEntry(item.getPos(), normalized, groupId, groupNormalized));
        }
        return result;
    }

    /** Schedules starts inside the clip, independently of individual action duration. */
    public static List<BlockSequenceEntry> generate(List<BlockPos> sourceBlocks, Container container,
            SequenceParameters params, float clipDuration, float animDuration, float totalDelaySeconds) {
        return generate(sourceBlocks, container, params, clipDuration, animDuration, totalDelaySeconds, false);
    }

    public static List<BlockSequenceEntry> generate(List<BlockPos> sourceBlocks, Container container,
            SequenceParameters params, float clipDuration, float animDuration, float totalDelaySeconds,
            boolean instant) {
        return PerBlockSchedule.create(generate(sourceBlocks, container, params), params, clipDuration).entries();
    }

    private static List<BlockSequenceEntry> generateDefault(List<BlockPos> sourceBlocks, Container container) {
        List<BlockPos> blocks;
        if (sourceBlocks != null && !sourceBlocks.isEmpty()) {
            blocks = sourceBlocks;
        } else if (container != null) {
            blocks = new ArrayList<>(container.getBlocksView());
        } else {
            blocks = List.of();
        }

        List<BlockSequenceEntry> entries = new ArrayList<>(blocks.size());
        int n = blocks.size();
        if (n == 0) {
            return entries;
        }
        for (int i = 0; i < n; i++) {
            float normalized = n == 1 ? 0.0f : (float) i / (float) (n - 1);
            entries.add(new BlockSequenceEntry(blocks.get(i), normalized, 0, normalized));
        }
        return entries;
    }

    // ============================================================================================
    // BFS 泛洪蔓延算法
    // ============================================================================================

    /** 6-连通邻居偏移（上/下/东/西/南/北） */
    private static final BlockPos[] NEIGHBORS = {
        new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
        new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
        new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
    };

    /**
     * 对 items 按层分组后执行 BFS 泛洪，用 BFS 距离替换每个 item 的 score。
     *
     * <p>算法：
     * <ol>
     *   <li>按 groupId（layerIndex）将方块分组</li>
     *   <li>对每组，根据 {@link FloodSeedMode} 选择 BFS 起点</li>
     *   <li>从起点做 BFS（6-连通），BFS 波前距离即为新 score</li>
     *   <li>未能到达的孤立方块附加在末尾</li>
     * </ol>
     */
    private static void applyFloodBFS(List<BlockSequenceItem> items, SequenceParameters params,
                                       Container.BoundingBox bbox) {
        if (items == null || items.isEmpty()) return;

        FloodSeedMode seedMode = params.getFloodSeedMode();
        boolean reverse = params.isReverse();
        float weight = Math.max(0f, params.getPriorityWeight());

        // 1. 按 groupId 分组
        Map<Integer, List<BlockSequenceItem>> groups = new HashMap<>();
        for (BlockSequenceItem item : items) {
            groups.computeIfAbsent(item.getGroupId(), k -> new ArrayList<>()).add(item);
        }

        // 全局随机种子（用于 GLOBAL_RANDOM / LAYER_RANDOM 的确定性伪随机）
        long globalSeed = 0;
        if (bbox != null) {
            BlockPos min = bbox.getMin();
            BlockPos max = bbox.getMax();
            globalSeed = min.asLong() ^ (max.asLong() * 31L) ^ items.size();
        }

        for (Map.Entry<Integer, List<BlockSequenceItem>> entry : groups.entrySet()) {
            int groupId = entry.getKey();
            List<BlockSequenceItem> groupItems = entry.getValue();
            if (groupItems.size() <= 1) {
                // 只有一个方块的组不需要排序
                continue;
            }

            // 2. 构建方块位置索引
            Set<BlockPos> posSet = new HashSet<>();
            Map<BlockPos, BlockSequenceItem> posMap = new HashMap<>();
            for (BlockSequenceItem item : groupItems) {
                posSet.add(item.getPos());
                posMap.put(item.getPos(), item);
            }

            // 3. 多连通分量 BFS：循环直到所有方块都被访问
            Map<BlockPos, Integer> bfsDistance = new HashMap<>();
            Set<BlockPos> unvisited = new HashSet<>(posSet);
            int componentOffset = 0; // 每个连通分量的距离偏移

            while (!unvisited.isEmpty()) {
                // 3.1 选择当前连通分量的 BFS 起点
                BlockPos seed;
                if (componentOffset == 0) {
                    // 第一个连通分量：使用用户指定的种子模式
                    seed = selectFloodSeed(groupItems, seedMode, bbox, groupId, globalSeed);
                    if (!unvisited.contains(seed)) {
                        // 如果种子不在未访问集合中，找最近的未访问方块
                        seed = findClosest(seed, unvisited);
                    }
                } else {
                    // 后续连通分量：从未访问方块中任选一个（确定性选择）
                    seed = unvisited.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElse(null);
                }

                if (seed == null) {
                    break; // 不应该发生，但保险起见
                }

                // 3.2 对当前连通分量执行 BFS
                Queue<BlockPos> queue = new ArrayDeque<>();
                queue.add(seed);
                bfsDistance.put(seed, componentOffset);
                unvisited.remove(seed);

                int maxDistInComponent = componentOffset;

                while (!queue.isEmpty()) {
                    BlockPos current = queue.poll();
                    int dist = bfsDistance.get(current);
                    for (BlockPos offset : NEIGHBORS) {
                        BlockPos neighbor = current.add(offset);
                        if (unvisited.contains(neighbor)) {
                            int neighborDist = dist + 1;
                            bfsDistance.put(neighbor, neighborDist);
                            queue.add(neighbor);
                            unvisited.remove(neighbor);
                            maxDistInComponent = Math.max(maxDistInComponent, neighborDist);
                        }
                    }
                }

                // 3.3 下一个连通分量的距离从当前最大距离+间隔开始
                // 间隔设为1，使不同连通分量的方块在序列中保持分离
                componentOffset = maxDistInComponent + 1;
            }

            // 4. 用 BFS 距离设置新 score
            // 所有方块都应该被访问到了，不再需要 unreachableDist
            for (BlockSequenceItem item : groupItems) {
                int dist = bfsDistance.getOrDefault(item.getPos(), 0); // 不应该发生
                float bfsScore = (float) dist;
                // 重新计算 finalScore = flowScore(groupId) * weight + bfsScore
                float finalScore = (float) groupId * weight + bfsScore;
                if (reverse) {
                    finalScore = -finalScore;
                }
                item.setScore(finalScore);
            }
        }
    }

    /**
     * 根据 FloodSeedMode 选择 BFS 起点。
     */
    private static BlockPos selectFloodSeed(List<BlockSequenceItem> groupItems, FloodSeedMode mode,
                                             Container.BoundingBox bbox, int groupId, long globalSeed) {
        switch (mode) {
            case LAYER_CENTER -> {
                // 计算该组所有方块的几何中心
                double cx = 0, cy = 0, cz = 0;
                for (BlockSequenceItem item : groupItems) {
                    cx += item.getPos().getX();
                    cy += item.getPos().getY();
                    cz += item.getPos().getZ();
                }
                int n = groupItems.size();
                BlockPos center = new BlockPos((int) Math.round(cx / n), (int) Math.round(cy / n), (int) Math.round(cz / n));
                return center;
            }
            case LAYER_MIN_CORNER -> {
                // 该组中坐标最小的方块
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                for (BlockSequenceItem item : groupItems) {
                    BlockPos p = item.getPos();
                    if (p.getX() < minX) minX = p.getX();
                    if (p.getY() < minY) minY = p.getY();
                    if (p.getZ() < minZ) minZ = p.getZ();
                }
                return new BlockPos(minX, minY, minZ);
            }
            case LAYER_RANDOM -> {
                // 基于 groupId 的确定性伪随机索引
                long layerSeed = globalSeed ^ (groupId * 7919L);
                int idx = (int) ((layerSeed & 0x7FFFFFFFL) % groupItems.size());
                return groupItems.get(idx).getPos();
            }
            case GLOBAL_RANDOM -> {
                // 基于全局种子的确定性伪随机：在所有组间共享同一个逻辑起点
                // 以 bbox 中心做偏移后选取
                int idx = (int) ((globalSeed & 0x7FFFFFFFL) % groupItems.size());
                return groupItems.get(idx).getPos();
            }
        }
        // fallback: 第一个方块
        return groupItems.get(0).getPos();
    }

    /**
     * 在方块集合中找到离目标最近的方块（曼哈顿距离）。
     */
    private static BlockPos findClosest(BlockPos target, Set<BlockPos> candidates) {
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (BlockPos p : candidates) {
            int dist = Math.abs(p.getX() - target.getX())
                     + Math.abs(p.getY() - target.getY())
                     + Math.abs(p.getZ() - target.getZ());
            if (dist < bestDist || (dist == bestDist && (best == null || p.asLong() < best.asLong()))) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }
}

