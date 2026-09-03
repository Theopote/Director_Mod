package com.director.actor.rig.skeleton;

import java.util.Objects;

/** Stable definition of one bone in a {@link RigDefinition}. */
public record BoneDefinition(
        String id,
        String displayName,
        int parentIndex,
        BoneTransform restLocal,
        BoneCapabilities capabilities
) {
    public BoneDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Bone id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        Objects.requireNonNull(restLocal, "restLocal");
        Objects.requireNonNull(capabilities, "capabilities");
    }
}
