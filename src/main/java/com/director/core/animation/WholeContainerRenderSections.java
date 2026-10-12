package com.director.core.animation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.HashSet;
import java.util.Set;

/** Render-thread handoff requests; only suppression transitions rebuild, never animation frames. */
public final class WholeContainerRenderSections {
    private static final WholeContainerRenderSections INSTANCE = new WholeContainerRenderSections();
    private Object world;
    private final Set<Long> pending = new HashSet<>();
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
    private void switchWorld(Object currentWorld) {
        if (world != currentWorld) { pending.clear(); world = currentWorld; }
    }
}
