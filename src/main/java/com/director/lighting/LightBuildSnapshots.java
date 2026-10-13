package com.director.lighting;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Pins terrain lighting through CPU construction and GPU upload, including failures. */
public final class LightBuildSnapshots {
    private static final class Epoch {
        final List<PointLight> lights;
        final AtomicInteger jobs = new AtomicInteger();
        Epoch(List<PointLight> lights) { this.lights = List.copyOf(lights); }
    }
    private volatile Epoch epoch = new Epoch(List.of());
    private final ThreadLocal<List<PointLight>> pinned = new ThreadLocal<>();

    public List<PointLight> lights() { return epoch.lights; }
    public int activeJobs() { return epoch.jobs.get(); }
    public List<PointLight> sampling(List<PointLight> live) {
        var value = pinned.get();
        return value == null ? live : value;
    }
    public synchronized boolean publish(List<PointLight> lights) {
        if (epoch.jobs.get() != 0) return false;
        epoch = new Epoch(lights);
        return true;
    }
    /** Old-world uploads must not hold up the new world. */
    public synchronized void reset() { epoch = new Epoch(List.of()); }
    public Scope openScope() {
        var previous = pinned.get();
        return new Scope(previous == null ? epoch.lights : previous);
    }
    public final class Scope implements AutoCloseable {
        private final List<PointLight> previous = pinned.get();
        private Scope(List<PointLight> lights) { pinned.set(lights); }
        @Override public void close() {
            if (previous == null) pinned.remove(); else pinned.set(previous);
        }
    }
    private synchronized Lease acquire() {
        var captured = epoch;
        captured.jobs.incrementAndGet();
        return new Lease(captured);
    }
    private final class Lease implements AutoCloseable {
        private final Epoch captured;
        private final AtomicBoolean closed = new AtomicBoolean();
        Lease(Epoch captured) { this.captured = captured; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) captured.jobs.decrementAndGet();
        }
    }
    public <T> CompletableFuture<CompletableFuture<T>> submit(
            Supplier<CompletableFuture<T>> task, Executor executor) {
        Lease lease = acquire();
        try {
            return CompletableFuture.supplyAsync(() -> {
                try (Scope scope = new Scope(lease.captured.lights)) {
                    return task.get().whenComplete((value, error) -> lease.close());
                } catch (Throwable error) {
                    lease.close();
                    throw error;
                }
            }, executor);
        } catch (Throwable error) {
            lease.close();
            throw error;
        }
    }
}
