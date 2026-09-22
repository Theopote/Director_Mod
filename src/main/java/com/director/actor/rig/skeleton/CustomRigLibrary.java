package com.director.actor.rig.skeleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Project-facing registry for user-authored custom rig skeletons (used by {@code ActorType.CUSTOM}). */
public final class CustomRigLibrary {
    private static final CustomRigLibrary INSTANCE = new CustomRigLibrary();
    private final Map<String, RigDefinition> rigs = new LinkedHashMap<>();

    private CustomRigLibrary() {}

    public static CustomRigLibrary getInstance() { return INSTANCE; }

    public Optional<RigDefinition> register(RigDefinition rig) {
        Objects.requireNonNull(rig, "rig");
        return Optional.ofNullable(rigs.put(rig.id(), rig));
    }

    public Optional<RigDefinition> find(String id) { return Optional.ofNullable(rigs.get(id)); }
    public boolean contains(String id) { return rigs.containsKey(id); }
    public boolean remove(String id) { return rigs.remove(id) != null; }
    public void clear() { rigs.clear(); }
    public List<RigDefinition> all() { return Collections.unmodifiableList(new ArrayList<>(rigs.values())); }
}
