package com.director.actor.path;

import com.director.actor.path.persistence.ActorPathPersistence;
import com.director.timeline.model.entity.ActorRouteClip;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 演员路径库全局管理器（按存档持久化）。
 */
public final class ActorPathLibraryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPathLibraryManager.class);
    private static final AtomicReference<ActorPathLibrary> GLOBAL = new AtomicReference<>();

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
            LOGGER.info("[ActorPathLibraryManager] 初始化路径库，共 {} 条路径", library.size());
        }
        return library;
    }

    public static void reloadForCurrentWorld() {
        ActorPathLibrary library = new ActorPathLibrary();
        if (ActorPathPersistence.hasSavedData()) {
            ActorPathPersistence.loadFromFile(library);
        }
        GLOBAL.set(library);
        LOGGER.info("[ActorPathLibraryManager] 重载路径库，共 {} 条路径", library.size());
    }

    public static boolean save() {
        ActorPathLibrary library = GLOBAL.get();
        if (library == null) {
            return true;
        }
        return ActorPathPersistence.saveToFile(library);
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
        if (path == null) {
            return false;
        }
        path.setWaypoints(waypoints);
        path.setInterpolationMode(mode);
        save();
        return true;
    }

    public static boolean renamePath(UUID id, String newName) {
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
}
