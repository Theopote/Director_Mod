package com.director.core.container.visibility;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side proofs of unchanged section data/visual state. Does not retain worlds per entry. */
public final class ClientSectionInvalidation {
    private static final ClientSectionInvalidation INSTANCE = new ClientSectionInvalidation();
    private static final int MAX_SECTIONS = 8192;
    private final Map<Long, Object> tokens = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Object> eldest) {
            return size() > MAX_SECTIONS;
        }
    };
    private Object world;
    private long revision;

    public static ClientSectionInvalidation getInstance() { return INSTANCE; }

    public synchronized long revision() { return revision; }

    public synchronized void reset(Object currentWorld) {
        world = currentWorld;
        tokens.clear();
        revision++;
    }

    public synchronized Object token(Object currentWorld, long section) {
        if (currentWorld == null) return null;
        if (world != currentWorld) reset(currentWorld);
        return tokens.computeIfAbsent(section, key -> new Object());
    }

    public synchronized boolean unchanged(Object currentWorld, long section, Object token) {
        return token != null && world == currentWorld && tokens.get(section) == token;
    }

    public synchronized void invalidate(Object currentWorld, long section) {
        if (currentWorld == null || world != currentWorld) return;
        tokens.remove(section);
        // Wake stationary previews too, including proofs conservatively evicted from the LRU.
        revision++;
    }

    public void invalidateBlock(Object currentWorld, BlockPos pos) {
        invalidate(currentWorld, section(pos));
    }

    public synchronized void invalidateVisual(BlockPos pos) {
        if (world == null) return;
        tokens.remove(section(pos));
        revision++;
    }

    public synchronized void invalidateAllVisual() {
        if (world == null) return;
        tokens.clear();
        revision++;
    }

    public synchronized void invalidateChunk(Object currentWorld, int x, int z, int bottom, int top) {
        if (currentWorld == null || world != currentWorld) return;
        for (int y = bottom; y < top; y++) tokens.remove(ChunkSectionPos.asLong(x, y, z));
        revision++;
    }

    public static long section(BlockPos pos) {
        return ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }
}
