package com.chronoblocks.core.animation.sequence;

import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            ? new ArrayList<>(sourceBlocks)
            : new ArrayList<>(container.getBlocks());

        Container.BoundingBox bbox = container.getBoundingBox();

        List<BlockSequenceItem> items = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            BlockSequenceItem item = new BlockSequenceItem(blocks.get(i), i);
            SequenceEvaluator.Evaluation evaluation = EVALUATOR.evaluate(blocks.get(i), bbox, params);
            item.setGroupId(evaluation.layerIndex());
            item.setScore(evaluation.finalScore());
            items.add(item);
        }

        items.sort(Comparator
            .comparingInt(BlockSequenceItem::getGroupId)
            .thenComparingDouble(BlockSequenceItem::getScore)
        );

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

    /**
     * 带时间上下文的序列生成：
     *
     * <p>用于让 SequenceParameters 中的并发/批次/间隔/全局延迟在运行时真正生效。
     * 输出的 {@link BlockSequenceEntry#getNormalizedTime()} 表示“该方块动画窗口相对可用时间轴”的归一化位置：
     * <pre>
     * availableSpan = max(ε, clipDuration - animDuration - totalDelay)
     * blockStartOffsetSeconds = normalizedTime * availableSpan
     * </pre>
     */
    public static List<BlockSequenceEntry> generate(List<BlockPos> sourceBlocks,
                                                   Container container,
                                                   SequenceParameters params,
                                                   float clipDuration,
                                                   float animDuration,
                                                   float totalDelaySeconds) {
        if (container == null) {
            return List.of();
        }
        if (params == null) {
            return generateDefault(sourceBlocks, container);
        }

        // 无时间上下文时退化为旧逻辑（避免除零/负时长）
        if (clipDuration <= 0.0f) {
            return generate(sourceBlocks, container, params);
        }

        List<BlockPos> blocks = sourceBlocks != null && !sourceBlocks.isEmpty()
            ? new ArrayList<>(sourceBlocks)
            : new ArrayList<>(container.getBlocks());

        Container.BoundingBox bbox = container.getBoundingBox();

        List<BlockSequenceItem> items = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            BlockSequenceItem item = new BlockSequenceItem(blocks.get(i), i);
            SequenceEvaluator.Evaluation evaluation = EVALUATOR.evaluate(blocks.get(i), bbox, params);
            item.setGroupId(evaluation.layerIndex());
            item.setScore(evaluation.finalScore());
            items.add(item);
        }

        items.sort(Comparator
            .comparingInt(BlockSequenceItem::getGroupId)
            .thenComparingDouble(BlockSequenceItem::getScore)
        );

        int n = items.size();
        List<BlockSequenceEntry> result = new ArrayList<>(n);
        if (n == 0) {
            return result;
        }

        // group sizes + stable group rank (for GROUP_FIRST)
        Map<Integer, Integer> groupSizes = new HashMap<>();
        for (BlockSequenceItem item : items) {
            groupSizes.merge(item.getGroupId(), 1, Integer::sum);
        }
        List<Integer> groupIdsSorted = new ArrayList<>(groupSizes.keySet());
        groupIdsSorted.sort(Integer::compareTo);
        Map<Integer, Integer> groupRank = new HashMap<>();
        for (int i = 0; i < groupIdsSorted.size(); i++) {
            groupRank.put(groupIdsSorted.get(i), i);
        }

        Map<Integer, Integer> groupCounters = new HashMap<>();

        float delay = Math.max(0.0f, totalDelaySeconds);
        float ad = Math.max(0.0001f, Math.min(animDuration, clipDuration));
        float baseSpan = clipDuration - ad - delay;
        // Preserve sequence ordering when the animation window consumes the clip.
        float availableSpan = baseSpan > 0.0001f
                ? baseSpan
                : Math.max(0.0001f, clipDuration - delay);

        ConcurrencyMode concurrency = params.getConcurrencyMode();
        TimeMappingStrategy mapping = params.getTimeMappingStrategy();

        int batchSize = Math.max(1, params.getBatchSize());
        float batchInterval = Math.max(0.0f, params.getBatchInterval());

        int groupCount = Math.max(1, groupIdsSorted.size());
        int batchCount = Math.max(1, (int) Math.ceil((double) n / (double) batchSize));

        // ---- 批次间隔 (IN_BATCHES / BATCH_MAPPING) ----
        // 直接使用用户设置的间隔值，仅在总量超出可用时间时才等比压缩。
        // 批次内所有方块同时出发，不再分配无用的 batchDuration 空窗。
        float batchGap = batchCount > 1
                ? Math.min(batchInterval, availableSpan / (float) (batchCount - 1))
                : 0.0f;

        // ---- 组间隔 (GROUP_FIRST / STAGGERED) ----
        // 每组需要最小窗口让组内方块有铺展空间，其余时间分配给组间隔。
        float minGroupDuration = 0.001f;
        float maxTotalGroupGaps = Math.max(0.0f, availableSpan - groupCount * minGroupDuration);
        float groupGap = groupCount > 1
                ? Math.min(batchInterval, maxTotalGroupGaps / (float) (groupCount - 1))
                : 0.0f;

        float groupWindow = Math.max(0.0f, availableSpan - (groupCount - 1) * groupGap);
        float groupDuration = Math.max(minGroupDuration, groupWindow / (float) groupCount);

        for (int i = 0; i < n; i++) {
            BlockSequenceItem item = items.get(i);
            int groupId = item.getGroupId();
            int orderInGroup = groupCounters.merge(groupId, 1, Integer::sum);
            int groupSize = Math.max(1, groupSizes.getOrDefault(groupId, 1));
            float groupNormalized = groupSize == 1 ? 0.0f : (float) (orderInGroup - 1) / (float) (groupSize - 1);

            float startOffsetSeconds;

            if (concurrency == ConcurrencyMode.ALL_AT_ONCE) {
                startOffsetSeconds = 0.0f;
            } else if (concurrency == ConcurrencyMode.IN_BATCHES || mapping == TimeMappingStrategy.BATCH_MAPPING) {
                int b = i / batchSize;
                startOffsetSeconds = b * batchGap;
            } else if (concurrency == ConcurrencyMode.PARALLEL_LAYERS) {
                // 层并行：每层都在 [0..availableSpan] 内独立推进（由 groupNormalized 决定）
                startOffsetSeconds = groupNormalized * availableSpan;
            } else if (mapping == TimeMappingStrategy.GROUP_FIRST) {
                int g = groupRank.getOrDefault(groupId, 0);
                startOffsetSeconds = g * (groupDuration + groupGap) + groupNormalized * groupDuration;
            } else if (mapping == TimeMappingStrategy.STAGGERED) {
                // 交错模式：奇数组正序、偶数组反序，在同一时间窗口内形成"锯齿"推进
                int g = groupRank.getOrDefault(groupId, 0);
                float groupNorm = (g % 2 == 0) ? groupNormalized : (1.0f - groupNormalized);
                startOffsetSeconds = g * (groupDuration + groupGap) + groupNorm * groupDuration;
            } else {
                // NORMALIZED_LINEAR：将排序结果均匀铺满 availableSpan
                float normalized = n == 1 ? 0.0f : (float) i / (float) (n - 1);
                startOffsetSeconds = normalized * availableSpan;
            }

            float normalizedTime = availableSpan <= 0.0001f ? 0.0f : (startOffsetSeconds / availableSpan);
            normalizedTime = Math.max(0.0f, Math.min(1.0f, normalizedTime));

            result.add(new BlockSequenceEntry(item.getPos(), normalizedTime, groupId, groupNormalized));
        }

        return result;
    }

    private static List<BlockSequenceEntry> generateDefault(List<BlockPos> sourceBlocks, Container container) {
        List<BlockPos> blocks;
        if (sourceBlocks != null && !sourceBlocks.isEmpty()) {
            blocks = new ArrayList<>(sourceBlocks);
        } else if (container != null) {
            blocks = new ArrayList<>(container.getBlocks());
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
}

