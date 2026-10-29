package com.director.timeline.playback.block;

import com.director.core.animation.PerBlockAnimationTiming;
import com.director.core.animation.sequence.BlockSequenceEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Sorted, non-null schedule entries are visited once for rendering or crossed effect boundaries. */
final class PerBlockFrameRanges {
    record Range(int from, int to) {}

    private PerBlockFrameRanges() {}

    static List<Range> select(List<BlockSequenceEntry> entries, PerBlockAnimationTiming.Layout timing,
            float time, Float previousEffectsTime, boolean exitPhase, boolean instant,
            int visibleFrom, int visibleTo) {
        int size = entries.size();
        if (size == 0) return List.of();
        // Invalid legacy times are not ordered by their effective window. Preserve the old scan.
        if (!Float.isFinite(time) || !Float.isFinite(timing.start()) || !Float.isFinite(timing.delay())
                || !Float.isFinite(timing.sequenceSpan()) || !Float.isFinite(timing.duration())
                || !Float.isFinite(entries.getFirst().getNormalizedTime())
                || !Float.isFinite(entries.getLast().getNormalizedTime())) return List.of(new Range(0, size));

        var ranges = new ArrayList<Range>(3);
        int renderFrom = exitPhase && !instant ? upperBound(entries, timing, time, true) : 0;
        int renderTo = exitPhase ? size : upperBound(entries, timing, time, false);
        add(ranges, Math.max(renderFrom, visibleFrom), Math.min(renderTo, visibleTo));
        if (previousEffectsTime != null && time > previousEffectsTime) {
            add(ranges, upperBound(entries, timing, previousEffectsTime, false),
                    upperBound(entries, timing, time, false));
            add(ranges, upperBound(entries, timing, previousEffectsTime, true),
                    upperBound(entries, timing, time, true));
        }
        ranges.sort(Comparator.comparingInt(Range::from));
        var merged = new ArrayList<Range>(3);
        for (var range : ranges) {
            if (merged.isEmpty() || merged.getLast().to() < range.from()) merged.add(range);
            else {
                var previous = merged.removeLast();
                merged.add(new Range(previous.from(), Math.max(previous.to(), range.to())));
            }
        }
        return merged;
    }

    private static void add(List<Range> ranges, int from, int to) {
        if (from < to) ranges.add(new Range(from, to));
    }

    private static int upperBound(List<BlockSequenceEntry> entries, PerBlockAnimationTiming.Layout timing,
            float time, boolean finished) {
        int low = 0, high = entries.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            var window = timing.window(entries.get(mid).getNormalizedTime());
            float boundary = finished ? window.end() : window.start();
            if (boundary <= time) low = mid + 1;
            else high = mid;
        }
        return low;
    }
}
