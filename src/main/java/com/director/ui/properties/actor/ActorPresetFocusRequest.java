package com.director.ui.properties.actor;

import java.lang.ref.WeakReference;
import java.util.function.LongSupplier;

/** Short-lived, single-consumption UI navigation request, without retaining a deleted clip. */
final class ActorPresetFocusRequest<T> {
    private final LongSupplier clock;
    private WeakReference<T> target=new WeakReference<>(null);
    private long requested;
    ActorPresetFocusRequest() { this(System::nanoTime); }
    ActorPresetFocusRequest(LongSupplier clock) { this.clock=clock; }
    void request(T value) { target=new WeakReference<>(value); requested=clock.getAsLong(); }
    boolean consume(T value) {
        T pending=target.get();
        boolean match=pending!=null && pending==value && clock.getAsLong()-requested<=500_000_000L;
        // A different inspector invalidates the request as well.
        target.clear();
        return match;
    }
}
