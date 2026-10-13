package com.director.lighting;

import java.util.Objects;
import java.util.UUID;

/** Immutable scene light; brightness uses Minecraft's 0..15 block-light scale. */
public record PointLight(UUID id, String name, double x, double y, double z,
                         float brightness, float range, float falloff, boolean enabled) {
    public PointLight {
        Objects.requireNonNull(id, "id");
        name = name == null || name.isBlank() ? "Point light" : name;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 30_000_000)
            throw new IllegalArgumentException("Invalid light position");
        if (!Float.isFinite(brightness) || brightness < 0 || brightness > 15
                || !Float.isFinite(range) || range < 1 || range > 32
                || !Float.isFinite(falloff) || falloff < .25f || falloff > 4)
            throw new IllegalArgumentException("Invalid light settings");
    }
    public static PointLight create(double x, double y, double z) {
        return new PointLight(UUID.randomUUID(), "Point light", x, y, z, 15, 8, 1, true);
    }
    public PointLight at(double x, double y, double z) {
        return new PointLight(id, name, x, y, z, brightness, range, falloff, enabled);
    }
    public PointLight settings(float brightness, float range, float falloff, boolean enabled) {
        return new PointLight(id, name, x, y, z, brightness, range, falloff, enabled);
    }
}
