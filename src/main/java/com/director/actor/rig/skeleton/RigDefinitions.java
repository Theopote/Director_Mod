package com.director.actor.rig.skeleton;

import java.util.Optional;

/** Resolver for rig definitions supported by the current build. */
public final class RigDefinitions {
    private RigDefinitions() {}

    public static Optional<RigDefinition> find(String id) {
        if (HumanoidRig.DEFINITION.id().equals(id)) return Optional.of(HumanoidRig.DEFINITION);
        return Optional.empty();
    }
}
