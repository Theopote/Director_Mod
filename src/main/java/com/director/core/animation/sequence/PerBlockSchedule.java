package com.director.core.animation.sequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Trigger cadence uses the clip length. Individual action duration never enters scheduling. */
public final class PerBlockSchedule {
    private PerBlockSchedule() {}
    public record Schedule(List<BlockSequenceEntry> entries, float lastStartOffset) {}

    public static Schedule create(List<BlockSequenceEntry> ordered, SequenceParameters params, float clipDuration) {
        if (ordered.isEmpty()) return new Schedule(List.of(), 0);
        if (params == null) params = new SequenceParameters();
        float length = Math.max(0, clipDuration);
        Map<Integer, Integer> sizes = new LinkedHashMap<>();
        for (var entry : ordered) sizes.merge(entry.getGroupId(), 1, Integer::sum);
        List<Integer> groups = new ArrayList<>(sizes.keySet());
        var concurrency = params.getConcurrencyMode();
        var mapping = params.getTimeMappingStrategy();
        int batchCount = (ordered.size() + params.getBatchSize() - 1) / params.getBatchSize();
        float batchGap = Math.min(params.getBatchInterval(), length / batchCount);
        float groupGap = Math.min(params.getBatchInterval(), length / groups.size());
        float groupStep = Math.max(0, length - (groups.size() - 1) * groupGap) / ordered.size();
        Map<Integer, Float> groupStarts = new HashMap<>();
        Map<Integer, Integer> ranks = new HashMap<>();
        float groupStart = 0;
        for (int i = 0; i < groups.size(); i++) {
            int group = groups.get(i);
            groupStarts.put(group, groupStart); ranks.put(group, i);
            groupStart += sizes.get(group) * groupStep + groupGap;
        }
        Map<Integer, Integer> counters = new HashMap<>();
        List<BlockSequenceEntry> entries = new ArrayList<>(ordered.size());
        float lastStart = 0;
        for (int i = 0; i < ordered.size(); i++) {
            var entry = ordered.get(i);
            int group = entry.getGroupId();
            int local = counters.merge(group, 1, Integer::sum) - 1;
            float start;
            if (concurrency == ConcurrencyMode.ALL_AT_ONCE) start = 0;
            else if (concurrency == ConcurrencyMode.PARALLEL_LAYERS) start = local * length / sizes.get(group);
            else if (concurrency == ConcurrencyMode.IN_BATCHES || mapping == TimeMappingStrategy.BATCH_MAPPING)
                start = (i / params.getBatchSize()) * batchGap;
            else if (mapping == TimeMappingStrategy.GROUP_FIRST || mapping == TimeMappingStrategy.STAGGERED) {
                if (mapping == TimeMappingStrategy.STAGGERED && ranks.get(group) % 2 == 1)
                    local = sizes.get(group) - 1 - local;
                start = groupStarts.get(group) + local * groupStep;
            } else start = i * length / ordered.size();
            lastStart = Math.max(lastStart, start);
            entries.add(new BlockSequenceEntry(entry.getPos(), length > 0 ? start / length : 0,
                    group, entry.getGroupNormalizedTime()));
        }
        entries.sort(java.util.Comparator.comparingDouble(BlockSequenceEntry::getNormalizedTime));
        return new Schedule(entries, lastStart);
    }
}
