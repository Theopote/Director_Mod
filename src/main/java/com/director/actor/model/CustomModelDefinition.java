package com.director.actor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A block-cuboid visual body attached to a specific {@link com.director.actor.rig.skeleton.RigDefinition}.
 *
 * <p>Unlike {@link com.director.actor.rig.skeleton.HumanoidTopology}-matching rigs (which reuse the
 * ArmorStand renderer), a {@code CustomModelDefinition} lets an arbitrary-topology rig (any bone count
 * or naming) get a visible body made of per-bone boxes, rendered with block-display entities.
 */
public final class CustomModelDefinition {
    private final String id;
    private final String displayName;
    private final String rigId;
    private final List<BoneCuboid> cuboids;

    public CustomModelDefinition(String id, String displayName, String rigId, List<BoneCuboid> cuboids) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Model id must not be blank");
        if (rigId == null || rigId.isBlank()) throw new IllegalArgumentException("rigId must not be blank");
        this.id = id;
        this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
        this.rigId = rigId;
        this.cuboids = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(cuboids, "cuboids")));
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String rigId() { return rigId; }
    public List<BoneCuboid> cuboids() { return cuboids; }
}
