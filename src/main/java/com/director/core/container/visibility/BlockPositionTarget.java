package com.director.core.container.visibility;

import it.unimi.dsi.fastutil.longs.AbstractLongSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.util.math.BlockPos;
import java.util.Set;

/** Read-only encoded view of an immutable frame target; no full-container conversion/copy. */
final class BlockPositionTarget extends AbstractLongSet {
    private final Set<BlockPos> positions;
    BlockPositionTarget(Set<BlockPos> positions) { this.positions = positions; }
    @Override public int size() { return positions.size(); }
    @Override public boolean contains(long value) { return positions.contains(BlockPos.fromLong(value)); }
    @Override public LongIterator iterator() {
        var source = positions.iterator();
        return new LongIterator() {
            @Override public boolean hasNext() { return source.hasNext(); }
            @Override public long nextLong() { return source.next().asLong(); }
        };
    }
}
