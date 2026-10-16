package com.director.timeline.playback.block;

import com.director.core.animation.sequence.BlockSequenceEntry;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SequenceVisibilityTest {
    @Test void prefixAndDemolitionViewsMatchExplicitSetsAcrossForwardAndBackwardSeeks() {
        var positions = new ArrayList<BlockPos>();
        var entries = new ArrayList<BlockSequenceEntry>();
        for (int i = 0; i < 100; i++) {
            var pos = new BlockPos(i, 10, -i); positions.add(pos);
            entries.add(new BlockSequenceEntry(pos, i / 99f, 0, 0));
        }
        var all = Set.copyOf(positions);
        var sequence = new SequenceVisibility(entries, all);
        for (boolean exit : new boolean[] {false, true}) {
            for (int render : new int[] {0, 30, 80, 100, 50, 0}) {
                for (int world : new int[] {0, 10, 50, 100}) {
                    var frame = sequence.sample(render, world, exit);
                    var expectedRender = new HashSet<>(positions.subList(0, render));
                    var expectedWorld = new HashSet<>(positions.subList(0, world));
                    if (exit) {
                        var r = new HashSet<>(all); r.removeAll(expectedRender); expectedRender = r;
                        var w = new HashSet<>(all); w.removeAll(expectedWorld); expectedWorld = w;
                    }
                    assertEquals(expectedRender, frame.render());
                    assertEquals(expectedWorld, frame.world());
                    assertEquals(expectedRender, new HashSet<>(frame.render()));
                    assertFalse(frame.render().contains(new BlockPos(500, 0, 0)));
                }
            }
        }
    }

    @Test void repeatedBoundariesReuseSetsAndEarlierFramesRemainImmutable() {
        var entries = new ArrayList<BlockSequenceEntry>();
        var all = new HashSet<BlockPos>();
        for (int i = 0; i < 100_000; i++) {
            var pos = new BlockPos(i, 5, 0); all.add(pos);
            entries.add(new BlockSequenceEntry(pos, i / 99999f, 0, 0));
        }
        var sequence = new SequenceVisibility(entries, Set.copyOf(all));
        var old = sequence.sample(50000, 40000, false);
        for (int i = 0; i < 1000; i++) assertSame(old, sequence.sample(50000, 40000, false));
        var next = sequence.sample(60000, 40000, false);
        assertSame(old.world(), next.world());
        assertEquals(50000, old.render().size());
        assertThrows(UnsupportedOperationException.class, () -> old.render().clear());
        assertEquals(50000, new HashSet<>(old.render()).size());
        assertEquals(60000, next.render().size());
    }

    @Test void duplicatesMissingAndNullEntriesDoNotBreakSetMembershipOrSize() {
        var a = BlockPos.ORIGIN; var b = new BlockPos(1, 0, 0); var c = new BlockPos(2, 0, 0);
        var entries = Arrays.asList(new BlockSequenceEntry(a, 0, 0, 0), null,
                new BlockSequenceEntry(a, 0.5f, 0, 0), new BlockSequenceEntry(b, 1, 0, 0));
        var sequence = new SequenceVisibility(entries, Set.of(a, b, c));
        assertEquals(Set.of(a), sequence.sample(3, 3, false).render());
        assertEquals(Set.of(c), sequence.sample(4, 4, true).render());
        assertEquals(Set.of(a, b), sequence.sample(4, 4, false).render());
    }
}
