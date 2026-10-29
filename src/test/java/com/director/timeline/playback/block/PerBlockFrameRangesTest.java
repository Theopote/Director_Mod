package com.director.timeline.playback.block;

import com.director.core.animation.PerBlockAnimationTiming;
import com.director.core.animation.sequence.BlockSequenceEntry;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PerBlockFrameRangesTest {
    private List<BlockSequenceEntry> entries(float... normalized) {
        var result = new ArrayList<BlockSequenceEntry>();
        for (int i = 0; i < normalized.length; i++)
            result.add(new BlockSequenceEntry(new BlockPos(i, 0, 0), normalized[i], 0, 0));
        return result;
    }

    private List<Integer> selected(List<PerBlockFrameRanges.Range> ranges) {
        var result = new ArrayList<Integer>();
        for (var range : ranges) for (int i = range.from(); i < range.to(); i++) result.add(i);
        return result;
    }

    @Test void matchesFullScanAtExactAdjacentAndRepeatedBoundariesAcrossSeeks() {
        var entries = entries(0, 0, 0.1f, 0.5f, 0.5f, 1);
        for (var timing : List.of(new PerBlockAnimationTiming.Layout(2, 4, 0.3f, 0.4f, 2),
                new PerBlockAnimationTiming.Layout(1_000_000, 1_000_001, 0.13f, 0.04f, 0.7f),
                new PerBlockAnimationTiming.Layout(2, 2, 0, 0, 0))) {
            var times = new TreeSet<Float>();
            for (var entry : entries) {
                var window = timing.window(entry.getNormalizedTime());
                for (float boundary : new float[] {window.start(), window.end()}) {
                    times.add(Math.nextDown(boundary)); times.add(boundary); times.add(Math.nextUp(boundary));
                }
            }
            var previousTimes = new ArrayList<Float>(times); previousTimes.add(null);
            for (boolean exit : new boolean[] {false, true}) for (boolean instant : new boolean[] {false, true})
                for (float time : times) for (Float previous : previousTimes)
                    for (int[] visible : List.of(new int[] {0, entries.size()}, new int[] {0, 2},
                            new int[] {3, entries.size()}, new int[] {0, 0})) {
                        var expected = new ArrayList<Integer>();
                        for (int i = 0; i < entries.size(); i++) {
                            var window = timing.window(entries.get(i).getNormalizedTime());
                            boolean render = i >= visible[0] && i < visible[1]
                                    && (exit ? instant || time < window.end() : time >= window.start());
                            boolean event = previous != null && (previous < window.start() && time >= window.start()
                                    || previous < window.end() && time >= window.end());
                            if (render || event) expected.add(i);
                        }
                        var actual = selected(PerBlockFrameRanges.select(entries, timing, time, previous,
                                exit, instant, visible[0], visible[1]));
                        assertEquals(expected, actual, "exit=" + exit + " instant=" + instant + " time=" + time);
                    }
        }
    }

    private static final class CountingEntries extends ArrayList<BlockSequenceEntry> {
        int reads;
        @Override public BlockSequenceEntry get(int index) { reads++; return super.get(index); }
    }

    private CountingEntries largeSequence() {
        var entries = new CountingEntries();
        for (int i = 0; i < 100_000; i++)
            entries.add(new BlockSequenceEntry(new BlockPos(i, 0, 0), i / 99999f, 0, 0));
        return entries;
    }

    @Test void largeSequenceSelectsSmallPrefixAndSuffixWithoutScanningAllEntries() {
        var entries = largeSequence();
        var timing = new PerBlockAnimationTiming.Layout(0, 100_000, 0, 1, 100_000);
        var prefix = selected(PerBlockFrameRanges.select(entries, timing, 2.5f, null, false, false, 0, entries.size()));
        assertEquals(List.of(0, 1, 2), prefix);
        assertTrue(entries.reads < 50, "Selection must use binary search");
        entries.reads = 0;
        var suffix = selected(PerBlockFrameRanges.select(entries, timing, 100_000.5f, null, true, false, 0, entries.size()));
        assertEquals(List.of(99_999), suffix);
        assertTrue(entries.reads < 50);
    }

    @Test void crossedStartAndEndEventsRemainSeparateSmallRangesEvenWithNoVisibleBlocks() {
        var entries = largeSequence();
        var timing = new PerBlockAnimationTiming.Layout(0, 100_000, 0, 100, 100_000);
        var ranges = PerBlockFrameRanges.select(entries, timing, 50_001, 50_000f, true, false, 0, 0);
        assertEquals(2, ranges.size());
        assertEquals(2, selected(ranges).size());
        assertTrue(entries.reads < 120, "Do not scan the prefix to find crossed events");
    }

    @Test void instantDemolitionUsesTheVisibleSuffixAndOverlappingEventsNeverDuplicateEntries() {
        var entries = entries(0, 0.25f, 0.5f, 0.75f, 1);
        var timing = new PerBlockAnimationTiming.Layout(0, 4, 0, 0, 4);
        assertEquals(List.of(3, 4), selected(PerBlockFrameRanges.select(entries, timing, 2, null, true, true, 3, 5)));
        assertEquals(List.of(0, 1, 2, 3, 4), selected(PerBlockFrameRanges.select(entries, timing, 4, -1f, false, true, 0, 5)));
    }
}
