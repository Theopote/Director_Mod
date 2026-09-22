package com.director.actor.model;

import com.director.actor.model.texture.CuboidFaceUvs;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * One box-shaped visual piece attached to a bone's local space.
 *
 * <p>{@code offset}/{@code size} describe an axis-aligned box in the owning bone's local
 * (unrotated, unscaled) space: the box spans {@code [offset, offset + size]}.
 * When {@code itemModelId} is set, the preview renderer uses a generated item model with custom UVs;
 * otherwise {@code blockStateId} selects a placeholder block (e.g. {@code "minecraft:white_concrete"}).
 */
public record BoneCuboid(
        String boneId,
        Vector3f offset,
        Vector3f size,
        String blockStateId,
        String itemModelId,
        String textureId,
        CuboidFaceUvs faceUvs
) {
    public BoneCuboid(String boneId, Vector3f offset, Vector3f size, String blockStateId) {
        this(boneId, offset, size, blockStateId, null, null, null);
    }

    public BoneCuboid {
        if (boneId == null || boneId.isBlank()) throw new IllegalArgumentException("boneId must not be blank");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(size, "size");
        if (blockStateId == null || blockStateId.isBlank()) throw new IllegalArgumentException("blockStateId must not be blank");
        if (!offset.isFinite() || !size.isFinite() || size.x <= 0 || size.y <= 0 || size.z <= 0)
            throw new IllegalArgumentException("Invalid cuboid geometry for bone " + boneId);
    }

    public boolean usesCustomTexture() {
        return itemModelId != null && !itemModelId.isBlank();
    }
}
