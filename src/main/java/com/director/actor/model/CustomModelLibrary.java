package com.director.actor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Project-facing registry for user-authored custom block-cuboid models. */
public final class CustomModelLibrary {
    private static final CustomModelLibrary INSTANCE = new CustomModelLibrary();
    private final Map<String, CustomModelDefinition> models = new LinkedHashMap<>();

    private CustomModelLibrary() {}

    public static CustomModelLibrary getInstance() { return INSTANCE; }

    public Optional<CustomModelDefinition> register(CustomModelDefinition model) {
        Objects.requireNonNull(model, "model");
        return Optional.ofNullable(models.put(model.id(), model));
    }

    public Optional<CustomModelDefinition> find(String id) { return Optional.ofNullable(models.get(id)); }
    public boolean contains(String id) { return models.containsKey(id); }
    public boolean remove(String id) { return models.remove(id) != null; }
    public void clear() { models.clear(); }
    public List<CustomModelDefinition> all() { return Collections.unmodifiableList(new ArrayList<>(models.values())); }
    public List<CustomModelDefinition> forRig(String rigId) {
        return models.values().stream().filter(m -> m.rigId().equals(rigId)).toList();
    }
}
