package com.director.actor;

import com.director.actor.persistence.ActorRegistryPersistence;
import com.director.persistence.PersistenceSaveStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global {@link ActorRegistry} holder with dirty-flag debounce saves.
 */
public final class ActorRegistryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorRegistryManager.class);
    private static final AtomicReference<ActorRegistry> GLOBAL = new AtomicReference<>();
    private static final long MIN_SAVE_INTERVAL_MS = 1000L;
    private static final ScheduledExecutorService SAVE_DEBOUNCE = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Director-ActorRegistrySave");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicBoolean dirty = new AtomicBoolean(false);
    private static volatile ScheduledFuture<?> pendingFlush;
    /** Last successfully loaded/saved registry file; used so world switches still flush the previous save. */
    private static volatile Path boundRegistryFile;

    private ActorRegistryManager() {}

    public static ActorRegistry getInstance() {
        ActorRegistry existing = GLOBAL.get();
        if (existing != null) return existing;
        ActorRegistry created = new ActorRegistry();
        if (ActorRegistryPersistence.hasSavedData()) {
            ActorRegistryPersistence.loadFromFile(created);
        }
        if (GLOBAL.compareAndSet(null, created)) {
            bindCurrentRegistryFile();
            LOGGER.info("[ActorRegistryManager] 自动创建新的 ActorRegistry 实例");
            return created;
        }
        return GLOBAL.get();
    }

    public static ActorRegistry createNewRegistry() {
        ActorRegistry previous = GLOBAL.get();
        if (previous != null) flushNow(previous);
        ActorRegistry registry = new ActorRegistry();
        GLOBAL.set(registry);
        LOGGER.info("[ActorRegistryManager] 创建新的 ActorRegistry 实例");
        if (ActorRegistryPersistence.hasSavedData()) {
            ActorRegistryPersistence.loadFromFile(registry);
        }
        bindCurrentRegistryFile();
        return registry;
    }

    public static void setRegistry(ActorRegistry registry) {
        if (registry == null) {
            LOGGER.warn("[ActorRegistryManager] 尝试设置 null ActorRegistry，将清空当前实例");
            ActorRegistry old = GLOBAL.getAndSet(null);
            if (old != null) flushNow(old);
            boundRegistryFile = null;
            return;
        }
        ActorRegistry oldRegistry = GLOBAL.get();
        if (oldRegistry != null && oldRegistry != registry) {
            flushNow(oldRegistry);
        }
        GLOBAL.set(registry);
        LOGGER.info("[ActorRegistryManager] 设置新的 ActorRegistry 实例，包含 {} 个 Actor", registry.size());
        if (ActorRegistryPersistence.hasSavedData()) {
            ActorRegistryPersistence.loadFromFile(registry);
        }
        bindCurrentRegistryFile();
    }

    public static void clear() {
        ActorRegistry registry = GLOBAL.getAndSet(null);
        if (registry != null) {
            flushNow(registry);
            registry.clear();
            LOGGER.info("[ActorRegistryManager] 清空 ActorRegistry");
        }
        boundRegistryFile = null;
    }

    /**
     * Marks the registry dirty and schedules a trailing debounce flush.
     * Always returns {@code true} when the request is accepted; use {@link #saveImmediately()} for a sync result.
     */
    public static boolean save() {
        dirty.set(true);
        scheduleFlush();
        return true;
    }

    /** Closing an editor must flush the last rename/change even inside the throttle interval. */
    public static boolean saveImmediately() {
        dirty.set(false);
        cancelPendingFlush();
        return flushNow(GLOBAL.get());
    }

    public static void noteRegistryFile(Path file) {
        boundRegistryFile = file;
    }

    private static void bindCurrentRegistryFile() {
        Path file = ActorRegistryPersistence.currentRegistryFile();
        if (file != null) boundRegistryFile = file;
    }

    private static synchronized void scheduleFlush() {
        if (pendingFlush != null && !pendingFlush.isDone()) {
            pendingFlush.cancel(false);
        }
        pendingFlush = SAVE_DEBOUNCE.schedule(() -> {
            if (!dirty.getAndSet(false)) return;
            flushNow(GLOBAL.get());
            if (dirty.get()) scheduleFlush();
        }, MIN_SAVE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private static synchronized void cancelPendingFlush() {
        if (pendingFlush != null) {
            pendingFlush.cancel(false);
            pendingFlush = null;
        }
    }

    private static synchronized boolean flushNow(ActorRegistry registry) {
        if (registry == null) return true;
        Path target = boundRegistryFile;
        boolean saved = target != null
                ? ActorRegistryPersistence.saveToFile(registry, target)
                : ActorRegistryPersistence.saveToFile(registry);
        PersistenceSaveStatus.reportActorResult(saved);
        if (saved) {
            Path current = ActorRegistryPersistence.currentRegistryFile();
            if (current != null) boundRegistryFile = current;
            else if (target != null) boundRegistryFile = target;
        }
        return saved;
    }

    public static boolean hasActiveRegistry() {
        return GLOBAL.get() != null;
    }
}
