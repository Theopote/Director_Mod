package com.director.core.animation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.LongPredicate;

/** Render-thread handoff requests; only suppression transitions rebuild, never animation frames. */
public final class WholeContainerRenderSections {
    private static final WholeContainerRenderSections INSTANCE = new WholeContainerRenderSections();
    private Object world;
    private final Set<Long> pending = new LinkedHashSet<>();
    public static WholeContainerRenderSections getInstance() { return INSTANCE; }

    public void request(Object currentWorld, BlockPos pos) {
        switchWorld(currentWorld);
        if (currentWorld == null) return;
        int x = pos.getX() >> 4, y = pos.getY() >> 4, z = pos.getZ() >> 4;
        for (int sx = x - ((pos.getX() & 15) == 0 ? 1 : 0);
                sx <= x + ((pos.getX() & 15) == 15 ? 1 : 0); sx++)
            for (int sy = y - ((pos.getY() & 15) == 0 ? 1 : 0);
                    sy <= y + ((pos.getY() & 15) == 15 ? 1 : 0); sy++)
                for (int sz = z - ((pos.getZ() & 15) == 0 ? 1 : 0);
                        sz <= z + ((pos.getZ() & 15) == 15 ? 1 : 0); sz++)
                    pending.add(ChunkSectionPos.asLong(sx, sy, sz));
    }

    public Set<Long> take(Object currentWorld) {
        switchWorld(currentWorld);
        var result = Set.copyOf(pending);
        pending.clear();
        return result;
    }

    /** A failed/unavailable rebuild stays queued. Budgets never discard unprocessed sections. */
    public int drain(Object currentWorld, int maxSections, long deadlineNanos, LongPredicate rebuild) {
        switchWorld(currentWorld);
        int attempted = 0;
        var iterator = pending.iterator();
        while (iterator.hasNext() && attempted < maxSections && System.nanoTime() < deadlineNanos) {
            long section = iterator.next();
            attempted++;
            if (rebuild.test(section)) iterator.remove();
        }
        return attempted;
    }

    public int pendingCount(Object currentWorld) {
        switchWorld(currentWorld);
        return pending.size();
    }
    private void switchWorld(Object currentWorld) {
        if (world != currentWorld) { pending.clear(); world = currentWorld; }
    }
}
