package com.director.actor.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny LRU for float-bit keyed historical samples. */
final class BoundedTimeCache<V> extends LinkedHashMap<Integer, V> {
    private final int capacity;
    BoundedTimeCache(int capacity) {
        super(Math.max(8, capacity), 0.75f, true);
        this.capacity = Math.max(1, capacity);
    }
    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, V> eldest) {
        return size() > capacity;
    }
}
