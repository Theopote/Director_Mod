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

