package com.director.actor;

import com.director.actor.persistence.ActorRegistryPersistence;
import com.director.persistence.ActorLibrarySaveQueue;
import com.director.persistence.PersistenceSaveStatus;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ActorRegistryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorRegistryManager.class);
    private static final ActorLibrarySaveQueue SAVES = new ActorLibrarySaveQueue(
            "Director-ActorRegistrySave", ActorRegistryPersistence::writeSnapshot);
    private static ActorRegistry registry;
    private static Path boundRegistryFile;

    private ActorRegistryManager() {}

    public static synchronized ActorRegistry getInstance() {
        if (registry == null) load(new ActorRegistry());
        return registry;
    }

    public static synchronized ActorRegistry createNewRegistry() {
        flushCurrent();
        load(new ActorRegistry());
        return registry;
    }

    public static synchronized void setRegistry(ActorRegistry next) {
        if (registry == next) return;
        flushCurrent();
        if (next == null) {
            registry = null;
            boundRegistryFile = null;
        } else {
            load(next);
        }
    }

    public static synchronized void clear() {
        var previous = registry;
        setRegistry(null);
        if (previous != null) previous.clear();
    }

    public static synchronized boolean save() {
        if (registry == null || boundRegistryFile == null) return true;
        try {
            SAVES.submit(boundRegistryFile, ActorRegistryPersistence.snapshot(registry));
            return true;
        } catch (RuntimeException failure) {
            LOGGER.error("Unable to capture actor registry", failure);
            PersistenceSaveStatus.reportActorResult(false);
            return false;
        }
    }

    public static synchronized boolean saveImmediately() {
        return flushCurrent();
    }

    private static void load(ActorRegistry next) {
        boundRegistryFile = ActorRegistryPersistence.currentRegistryFile();
        ActorRegistryPersistence.loadFromFile(next, boundRegistryFile);
        registry = next;
    }

    private static boolean flushCurrent() {
        if (registry == null) return true;
        if (boundRegistryFile == null) return false;
        try {
            return SAVES.flush(boundRegistryFile, ActorRegistryPersistence.snapshot(registry));
        } catch (RuntimeException failure) {
            LOGGER.error("Unable to capture actor registry", failure);
            PersistenceSaveStatus.reportActorResult(false);
            return false;
        }
    }

    public static synchronized boolean hasActiveRegistry() {
        return registry != null;
    }
}
