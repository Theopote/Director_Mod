package com.director.actor.path;

import com.director.actor.path.persistence.ActorPathPersistence;
import com.director.persistence.PersistenceSaveStatus;
import com.director.timeline.model.entity.ActorRouteClip;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 演员路径库全局管理器（按存档持久化，脏标记防抖写盘）。
 */
public final class ActorPathLibraryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPathLibraryManager.class);
    private static final AtomicReference<ActorPathLibrary> GLOBAL = new AtomicReference<>();
    private static final long MIN_SAVE_INTERVAL_MS = 1000L;
    private static final ScheduledExecutorService SAVE_DEBOUNCE = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Director-ActorPathSave");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean dirty = new AtomicBoolean(false);
    private static volatile ScheduledFuture<?> pendingFlush;
    private static volatile Path boundPathsFile;

    private ActorPathLibraryManager() {
    }

    public static ActorPathLibrary getInstance() {
        ActorPathLibrary library = GLOBAL.get();
        if (library == null) {
            library = new ActorPathLibrary();
            GLOBAL.set(library);
            if (ActorPathPersistence.hasSavedData()) {
                ActorPathPersistence.loadFromFile(library);
            }
            bindCurrentPathsFile();
            LOGGER.info("[ActorPathLibraryManager] 初始化路径库，共 {} 条路径", library.size());
        }
        return library;
    }

    public static void reloadForCurrentWorld() {
        ActorPathLibrary previous = GLOBAL.get();
        if (previous != null) flushNow(previous);
        ActorPathLibrary library = new ActorPathLibrary();
        if (ActorPathPersistence.hasSavedData()) {
            ActorPathPersistence.loadFromFile(library);
        }
        GLOBAL.set(library);
        bindCurrentPathsFile();
        LOGGER.info("[ActorPathLibraryManager] 重载路径库，共 {} 条路径", library.size());
    }

    /** Marks dirty and schedules a trailing debounce flush. */
    public static boolean save() {
        dirty.set(true);
        scheduleFlush();
        return true;
    }

    /** Sync flush for editor close / explicit save barriers. */
    public static boolean saveImmediately() {
        dirty.set(false);
        cancelPendingFlush();
        return flushNow(GLOBAL.get());
    }

    public static List<ActorPathDefinition> getAllPaths() {
        return getInstance().getAll();
    }

    @Nullable
    public static ActorPathDefinition getPath(UUID id) {
        return getInstance().get(id);
    }

    public static ActorPathDefinition createPath(String preferredName,
                                                 List<Vector3f> waypoints,
                                                 ActorRouteClip.InterpolationMode mode) {
        ActorPathLibrary library = getInstance();
        String name = library.generateUniqueName(preferredName);
        ActorPathDefinition path = new ActorPathDefinition(name);
        path.setWaypoints(waypoints);
        path.setInterpolationMode(mode);
        library.add(path);
        save();
        return path;
    }

    public static boolean updatePath(UUID id, List<Vector3f> waypoints, ActorRouteClip.InterpolationMode mode) {
        ActorPathDefinition path = getInstance().get(id);
        if (path == null || path.isLocked()) {
            return false;
        }
        path.setWaypoints(waypoints);
        path.setInterpolationMode(mode);
        save();
        return true;
    }

    public static boolean renamePath(UUID id, String newName) {
        ActorPathDefinition path = getInstance().get(id);
        if (path == null || path.isLocked()) {
            return false;
        }
        boolean renamed = getInstance().rename(id, newName);
        if (renamed) {
            save();
        }
        return renamed;
    }

    public static boolean removePath(UUID id) {
        boolean removed = getInstance().remove(id);
        if (removed) {
            save();
        }
        return removed;
    }

    public static boolean setPathLocked(UUID id, boolean locked) {
        ActorPathDefinition path = getInstance().get(id);
        if (path == null) {
            return false;
        }
        path.setLocked(locked);
        save();
        return true;
    }

    public static boolean setPathVisible(UUID id, boolean visible) {
        ActorPathDefinition path = getInstance().get(id);
        if (path == null) {
            return false;
        }
        path.setVisible(visible);
        save();
        return true;
    }

    private static void bindCurrentPathsFile() {
        Path file = ActorPathPersistence.currentPathsFile();
        if (file != null) boundPathsFile = file;
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

    private static synchronized boolean flushNow(ActorPathLibrary library) {
        if (library == null) return true;
        Path target = boundPathsFile;
        boolean saved = target != null
                ? ActorPathPersistence.saveToFile(library, target)
                : ActorPathPersistence.saveToFile(library);
        PersistenceSaveStatus.reportActorResult(saved);
        if (saved) {
            Path current = ActorPathPersistence.currentPathsFile();
            if (current != null) boundPathsFile = current;
            else if (target != null) boundPathsFile = target;
        }
        return saved;
    }
}
