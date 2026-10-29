package com.director.export.video;

import java.util.LinkedHashMap;
import java.util.function.ToLongFunction;

/** Per-export cache; active sounds can retain an evicted value only until playback ends. */
final class AudioDecodeCache<K, V> {
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final long budget;
    private final ToLongFunction<V> weigh;
    private long bytes;

    AudioDecodeCache(long budget, ToLongFunction<V> weigh) {
        this.budget = budget;
        this.weigh = weigh;
    }

    V get(K key) { return entries.get(key); }

    void put(K key, V value) {
        V previous = entries.remove(key);
        if (previous != null) bytes -= weigh.applyAsLong(previous);
        long size = weigh.applyAsLong(value);
        if (size > budget) return;
        while (bytes + size > budget && !entries.isEmpty()) {
            var oldest = entries.entrySet().iterator();
            bytes -= weigh.applyAsLong(oldest.next().getValue());
            oldest.remove();
        }
        entries.put(key, value);
        bytes += size;
    }
}
