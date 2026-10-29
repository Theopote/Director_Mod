package com.director.export.video;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Requests may arrive from workers; preparation and capture run on the render thread. */
final class FrameCaptureQueue<T> {
    private final ConcurrentLinkedQueue<T> pending = new ConcurrentLinkedQueue<>();
    private T prepared;

    void add(T request) { pending.add(request); }
    void remove(T request) { pending.remove(request); }

    T beginPreparation() {
        prepared = null;
        return pending.peek();
    }

    void markPrepared(T request) { prepared = request; }

    T takePrepared() {
        T request = prepared;
        prepared = null;
        return request != null && pending.peek() == request ? request : null;
    }
}
