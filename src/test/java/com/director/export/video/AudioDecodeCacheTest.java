package com.director.export.video;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioDecodeCacheTest {
    @Test void evictsLeastRecentlyUsedDecodedAudioToStayWithinByteBudget() {
        var cache = new AudioDecodeCache<String, float[]>(12, samples -> samples.length * 4L);
        float[] a = new float[2], b = new float[1], c = new float[1];
        cache.put("a", a); cache.put("b", b);
        assertSame(a, cache.get("a"));
        cache.put("c", c);
        assertSame(a, cache.get("a"));
        assertNull(cache.get("b"));
        assertSame(c, cache.get("c"));
    }

    @Test void oversizedSoundCanBeUsedWithoutRetainingItInTheCache() {
        var cache = new AudioDecodeCache<String, float[]>(8, samples -> samples.length * 4L);
        float[] small = new float[1];
        cache.put("small", small); cache.put("large", new float[3]);
        assertNull(cache.get("large"));
        assertSame(small, cache.get("small"));
    }

    @Test void replacingCachedSoundUpdatesItsAccountedSize() {
        var cache = new AudioDecodeCache<String, float[]>(8, samples -> samples.length * 4L);
        cache.put("same", new float[2]);
        float[] smaller = new float[1], other = new float[1];
        cache.put("same", smaller); cache.put("other", other);
        assertSame(smaller, cache.get("same"));
        assertSame(other, cache.get("other"));
    }
}
