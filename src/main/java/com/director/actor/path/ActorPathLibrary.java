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

/**
 * 演员路径库：管理路径定义，保证名称唯一（不区分大小写）。
 */
public final class ActorPathLibrary {

    private final Map<UUID, ActorPathDefinition> byId = new LinkedHashMap<>();

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
        byId.clear();
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
        byId.put(path.getId(), path);
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
        return id != null && byId.remove(id) != null;
    }

    public void replaceAll(List<ActorPathDefinition> paths) {
        byId.clear();
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
