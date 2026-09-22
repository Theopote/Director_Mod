package com.director.actor.model.texture;

import java.util.Arrays;
import java.util.Objects;

/** Per-face UV rectangles copied from Blockbench .bbmodel faces (pixel coordinates). */
public record CuboidFaceUvs(
        float[] north,
        float[] south,
        float[] east,
        float[] west,
        float[] up,
        float[] down
) {
    public CuboidFaceUvs {
        north = copy(north);
        south = copy(south);
        east = copy(east);
        west = copy(west);
        up = copy(up);
        down = copy(down);
    }

    public boolean hasAnyFace() {
        return north != null || south != null || east != null || west != null || up != null || down != null;
    }

    private static float[] copy(float[] values) {
        return values == null ? null : Arrays.copyOf(values, values.length);
    }

    public static CuboidFaceUvs uniform(float[] uv) {
        Objects.requireNonNull(uv, "uv");
        return new CuboidFaceUvs(uv, uv, uv, uv, uv, uv);
    }
}
