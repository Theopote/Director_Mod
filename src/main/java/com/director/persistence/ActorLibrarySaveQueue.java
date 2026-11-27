package com.director.persistence;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

public final class ActorLibrarySaveQueue {
    private final ScheduledExecutorService executor;
    private final BiPredicate<Path, String> writer;
    private final Map<Path, String> pending = new LinkedHashMap<>();
    private ScheduledFuture<?> scheduled;

    public ActorLibrarySaveQueue(String name, BiPredicate<Path, String> writer) {
        this.writer = writer;
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void submit(Path target, String snapshot) {
        pending.put(target, snapshot);
        schedule(1000);
    }

    public synchronized boolean flush(Path target, String snapshot) {
        pending.put(target, snapshot);
        boolean saved = write(target, snapshot);
        if (saved) pending.remove(target);
        else schedule(3000);
        return saved;
    }

    private void schedule(long delayMs) {
        if (scheduled != null) scheduled.cancel(false);
        scheduled = executor.schedule(this::flushPending, delayMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void flushPending() {
        pending.entrySet().removeIf(entry -> write(entry.getKey(), entry.getValue()));
        if (!pending.isEmpty()) schedule(3000);
    }

    private boolean write(Path path, String snapshot) {
        boolean saved;
        try {
            saved = writer.test(path, snapshot);
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(ActorLibrarySaveQueue.class).error("Unable to save actor library {}", path, failure);
            saved = false;
        }
        PersistenceSaveStatus.reportActorResult(saved);
        return saved;
    }
}
