package com.director.client.renderer.mesh;

import com.mojang.blaze3d.buffers.GpuBuffer;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SectionGpuBufferCacheTest {
    private static class Buffer extends GpuBuffer {
        boolean closed, fail;
        int closes;
        Buffer(int size) { super(USAGE_VERTEX, size); }
        @Override public boolean isClosed() { return closed; }
        @Override public void close() { closed = true; closes++; if (fail) throw new IllegalStateException("close"); }
    }
    private SectionGpuBufferCache.Key key(long section, long version) {
        return new SectionGpuBufferCache.Key(section, "solid", version, 0);
    }

    @Test void persistentHitsDoNotReuploadAndLruRespectsByteBudget() {
        var uploaded = new ArrayList<Buffer>();
        var cache = new SectionGpuBufferCache(8, (key, data) -> {
            var buffer = new Buffer(data.remaining()); uploaded.add(buffer); return buffer;
        }, () -> {});
        var a = cache.upload(key(1, 1), ByteBuffer.allocate(4));
        cache.upload(key(2, 1), ByteBuffer.allocate(4));
        assertSame(a, cache.upload(key(1, 1), ByteBuffer.allocate(4)));
        assertEquals(2, uploaded.size());
        cache.upload(key(3, 1), ByteBuffer.allocate(4));
        assertFalse(uploaded.getFirst().closed); assertTrue(uploaded.get(1).closed);
        assertEquals(8, cache.residentBytes());
        cache.close(); assertEquals(0, cache.residentBytes());
        for (var buffer : uploaded) assertEquals(1, buffer.closes);
    }

    @Test void oversizedOrEmptyMeshesUseFallbackAndSourcePositionIsNotConsumed() {
        var cache = new SectionGpuBufferCache(4, (key, data) -> {
            int size = data.remaining(); data.position(data.limit()); return new Buffer(size);
        }, () -> {});
        assertNull(cache.upload(key(0, 0), ByteBuffer.allocate(5)));
        assertNull(cache.upload(key(0, 0), ByteBuffer.allocate(0)));
        var source = ByteBuffer.allocate(4); source.position(1);
        cache.upload(key(1, 1), source);
        assertEquals(1, source.position()); assertEquals(3, cache.residentBytes()); cache.close();
    }

    @Test void sectionInvalidationClosesEveryVersionAndExternallyClosedBuffersAreMisses() {
        var cache = new SectionGpuBufferCache(32, (key, data) -> new Buffer(data.remaining()), () -> {});
        var a = (Buffer) cache.upload(key(1, 1), ByteBuffer.allocate(4));
        var b = (Buffer) cache.upload(key(1, 2), ByteBuffer.allocate(4));
        var c = (Buffer) cache.upload(key(2, 1), ByteBuffer.allocate(4));
        cache.invalidate(1); assertTrue(a.closed); assertTrue(b.closed); assertFalse(c.closed);
        assertEquals(4, cache.residentBytes()); c.close();
        assertNull(cache.get(key(2, 1))); assertEquals(0, cache.residentBytes()); cache.close();
    }

    @Test void removingOneClipPreservesOtherClipsAndVersionsInTheSameSection() {
        var cache = new SectionGpuBufferCache(32, (key, data) -> new Buffer(data.remaining()), () -> {});
        var first = new SectionGpuBufferCache.Key(1, "clip-a", 1, 0);
        var second = new SectionGpuBufferCache.Key(1, "clip-b", 1, 0);
        var newer = new SectionGpuBufferCache.Key(1, "clip-a", 2, 0);
        var a = (Buffer) cache.upload(first, ByteBuffer.allocate(4));
        var b = cache.upload(second, ByteBuffer.allocate(4));
        var c = cache.upload(newer, ByteBuffer.allocate(4));
        cache.invalidate(first);
        cache.invalidate(first);
        assertEquals(1, a.closes);
        assertSame(b, cache.get(second));
        assertSame(c, cache.get(newer));
        assertEquals(8, cache.residentBytes());
        cache.close();
    }

    @Test void closingOneFailedResourceStillClosesRemainingResourcesAndResetsAccounting() {
        var cache = new SectionGpuBufferCache(32, (key, data) -> new Buffer(data.remaining()), () -> {});
        var a = (Buffer) cache.upload(key(1, 1), ByteBuffer.allocate(4));
        var b = (Buffer) cache.upload(key(2, 1), ByteBuffer.allocate(4));
        a.fail = true; assertThrows(IllegalStateException.class, cache::close);
        assertTrue(b.closed); assertEquals(0, cache.residentBytes());
        cache.close(); assertEquals(1, b.closes);
    }
}
