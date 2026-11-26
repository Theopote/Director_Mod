package com.director.actor.path;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 演员路径库：管理路径定义，保证名称唯一（不区分大小写）。
 */
public final class ActorPathLibrary {

    private static final AtomicLong GLOBAL_GEOMETRY_REVISION = new AtomicLong();
    private final Map<UUID, ActorPathDefinition> byId = new LinkedHashMap<>();
    private long geometryRevision = GLOBAL_GEOMETRY_REVISION.incrementAndGet();

    public long geometryRevision() { return geometryRevision; }

    void noteGeometry() { geometryRevision = GLOBAL_GEOMETRY_REVISION.incrementAndGet(); }

    public List<ActorPathDefinition> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public int size() {
        return byId.size();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public void clear() {
        if (byId.isEmpty()) return;
        for (ActorPathDefinition path : byId.values()) path.attachLibrary(null);
        byId.clear();
        noteGeometry();
    }

    @Nullable
    public ActorPathDefinition get(UUID id) {
        if (id == null) {
            return null;
        }
        return byId.get(id);
    }

    @Nullable
    public ActorPathDefinition findByName(String name) {
        String key = normalizeNameKey(name);
        if (key.isEmpty()) {
            return null;
        }
        for (ActorPathDefinition path : byId.values()) {
            if (normalizeNameKey(path.getName()).equals(key)) {
                return path;
            }
        }
        return null;
    }

    public boolean isNameAvailable(String name, @Nullable UUID excludeId) {
        String key = normalizeNameKey(name);
        if (key.isEmpty()) {
            return false;
        }
        for (ActorPathDefinition path : byId.values()) {
            if (excludeId != null && excludeId.equals(path.getId())) {
                continue;
            }
            if (normalizeNameKey(path.getName()).equals(key)) {
                return false;
            }
        }
        return true;
    }

    public String generateUniqueName(String baseName) {
        String base = baseName == null || baseName.isBlank() ? "Path" : baseName.trim();
        if (isNameAvailable(base, null)) {
            return base;
        }
        int index = 1;
        while (!isNameAvailable(base + " " + index, null)) {
            index++;
        }
        return base + " " + index;
    }

    public ActorPathDefinition add(ActorPathDefinition path) {
        Objects.requireNonNull(path, "path");
        if (!isNameAvailable(path.getName(), path.getId())) {
            path.setName(generateUniqueName(path.getName()));
        }
        ActorPathDefinition previous = byId.put(path.getId(), path);
        if (previous != null && previous != path) previous.attachLibrary(null);
        path.attachLibrary(this);
        noteGeometry();
        return path;
    }

    public boolean rename(UUID id, String newName) {
        ActorPathDefinition path = get(id);
        if (path == null) {
            return false;
        }
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty() || !isNameAvailable(trimmed, id)) {
            return false;
        }
        path.setName(trimmed);
        return true;
    }

    public boolean remove(UUID id) {
        if (id == null) return false;
        ActorPathDefinition removed = byId.remove(id);
        if (removed == null) return false;
        removed.attachLibrary(null);
        noteGeometry();
        return true;
    }

    public void replaceAll(List<ActorPathDefinition> paths) {
        clear();
        if (paths == null) {
            return;
        }
        for (ActorPathDefinition path : paths) {
            if (path != null) {
                add(path);
            }
        }
    }

    private static String normalizeNameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
