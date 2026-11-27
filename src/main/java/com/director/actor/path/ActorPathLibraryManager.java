package com.director.actor.path;

import com.director.actor.path.persistence.ActorPathPersistence;
import com.director.persistence.PersistenceSaveStatus;
import com.director.persistence.ActorLibrarySaveQueue;
import com.director.timeline.model.entity.ActorRouteClip;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 演员路径库全局管理器（按存档持久化，脏标记防抖写盘）。
 */
public final class ActorPathLibraryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPathLibraryManager.class);
    private static final ActorLibrarySaveQueue SAVES = new ActorLibrarySaveQueue(
            "Director-ActorPathSave", ActorPathPersistence::writeSnapshot);
    private static ActorPathLibrary library;
    private static Path boundPathsFile;

    private ActorPathLibraryManager() {}

    public static synchronized ActorPathLibrary getInstance() {
        if (library == null) loadCurrentWorld();
        return library;
    }

    public static synchronized void reloadForCurrentWorld() {
        flushCurrent();
        loadCurrentWorld();
    }

    public static synchronized boolean save() {
        if (library == null || boundPathsFile == null) return true;
        try {
            SAVES.submit(boundPathsFile, ActorPathPersistence.snapshot(library));
            return true;
        } catch (RuntimeException failure) {
            LOGGER.error("Unable to capture actor paths", failure);
            PersistenceSaveStatus.reportActorResult(false);
            return false;
        }
    }

    public static synchronized boolean saveImmediately() {
        return flushCurrent();
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

    private static void loadCurrentWorld() {
        boundPathsFile = ActorPathPersistence.currentPathsFile();
        var next = new ActorPathLibrary();
        ActorPathPersistence.loadFromFile(next, boundPathsFile);
        library = next;
    }

    private static boolean flushCurrent() {
        if (library == null) return true;
        if (boundPathsFile == null) return false;
        try {
            return SAVES.flush(boundPathsFile, ActorPathPersistence.snapshot(library));
        } catch (RuntimeException failure) {
            LOGGER.error("Unable to capture actor paths", failure);
            PersistenceSaveStatus.reportActorResult(false);
            return false;
        }
    }
}
