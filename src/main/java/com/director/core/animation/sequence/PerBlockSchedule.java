package com.director.core.animation.sequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sequence scheduling is independent of the transform performed inside each action. */
public final class PerBlockSchedule {
    private PerBlockSchedule() {}
    public record Schedule(List<BlockSequenceEntry> entries, float duration, float actionDuration) {}

    public static Schedule create(List<BlockSequenceEntry> ordered, SequenceParameters params,
                                  float duration, boolean instant) {
        if (ordered.isEmpty()) return new Schedule(List.of(), 0, 0);
        if (params == null) params = new SequenceParameters();
        float configured = params.getPerBlockDuration() > 0 ? params.getPerBlockDuration() : duration;
        float slot = Float.isFinite(configured) ? Math.max(0.0001f, configured) : 0.5f;
        float gap = params.getBatchInterval();
        Map<Integer, Integer> sizes = new java.util.LinkedHashMap<>();
        for (var entry : ordered) sizes.merge(entry.getGroupId(), 1, Integer::sum);
        List<Integer> groups = new ArrayList<>(sizes.keySet());
        Map<Integer, Float> groupStarts = new HashMap<>();
        Map<Integer, Integer> ranks = new HashMap<>();
        float groupStart = 0;
        for (int i = 0; i < groups.size(); i++) {
            int group = groups.get(i);
            groupStarts.put(group, groupStart);
            ranks.put(group, i);
            groupStart += sizes.get(group) * slot + gap;
        }
        Map<Integer, Integer> counters = new HashMap<>();
        float[] starts = new float[ordered.size()];
        float lastStart = 0;
        var concurrency = params.getConcurrencyMode();
        var mapping = params.getTimeMappingStrategy();
        for (int i = 0; i < ordered.size(); i++) {
            int group = ordered.get(i).getGroupId();
            int local = counters.merge(group, 1, Integer::sum) - 1;
            float start;
            if (concurrency == ConcurrencyMode.ALL_AT_ONCE) start = 0;
            else if (concurrency == ConcurrencyMode.PARALLEL_LAYERS) start = local * slot;
            else if (concurrency == ConcurrencyMode.IN_BATCHES || mapping == TimeMappingStrategy.BATCH_MAPPING)
                start = (i / params.getBatchSize()) * (slot + gap);
            else if (mapping == TimeMappingStrategy.GROUP_FIRST || mapping == TimeMappingStrategy.STAGGERED) {
                if (mapping == TimeMappingStrategy.STAGGERED && ranks.get(group) % 2 == 1)
                    local = sizes.get(group) - 1 - local;
                start = groupStarts.get(group) + local * slot;
            } else start = i * slot;
            starts[i] = start;
            lastStart = Math.max(lastStart, start);
        }
        float action = instant ? 0 : slot;
        float total = instant ? Math.max(slot, lastStart) : lastStart + action;
        float span = total - action;
        List<BlockSequenceEntry> entries = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            var entry = ordered.get(i);
            entries.add(new BlockSequenceEntry(entry.getPos(), span > 0 ? starts[i] / span : 0,
                    entry.getGroupId(), entry.getGroupNormalizedTime(), action / total));
        }
        entries.sort(java.util.Comparator.comparingDouble(BlockSequenceEntry::getNormalizedTime));
        return new Schedule(entries, total, action);
    }
}
