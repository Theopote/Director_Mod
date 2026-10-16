package com.director.core.container.visibility;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockPositionTargetTest {
    @Test void creatingAndQueryingTargetDoesNotScanTheSource() {
        var positions = Set.of(new BlockPos(-500, -64, 400), new BlockPos(100, 319, -100));
        var source = new AbstractSet<BlockPos>() {
            @Override public int size() { return positions.size(); }
            @Override public boolean contains(Object pos) { return positions.contains(pos); }
            @Override public Iterator<BlockPos> iterator() { return fail("no eager target conversion"); }
        };
        var target = new BlockPositionTarget(source);
        assertEquals(2, target.size());
        for (var pos : positions) assertTrue(target.contains(pos.asLong()));
        assertFalse(target.contains(BlockPos.ORIGIN.asLong()));
        assertThrows(UnsupportedOperationException.class, () -> target.add(0));
    }

    @Test void lazyTargetConvergesAndRewindsWithoutAnyEncodingQueue() {
        var a = new BlockPos(-32, 100, -32); var b = new BlockPos(10, 20, 30);
        var target = new BlockPositionTarget(Set.of(a, b));
        var shown = new LongOpenHashSet();
        var work = new TimelineVisibilityWork(shown, target);
        while (!work.isComplete()) work.process(1, Long.MAX_VALUE, (pos, visible) -> true);
        assertEquals(new LongOpenHashSet(new long[] {a.asLong(), b.asLong()}), shown);
        var rewind = new TimelineVisibilityWork(shown, new BlockPositionTarget(Set.of()));
        while (!rewind.isComplete()) rewind.process(1, Long.MAX_VALUE, (pos, visible) -> true);
        assertTrue(shown.isEmpty());
        assertEquals(2, target.size());
    }
}
