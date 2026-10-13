package com.director.lighting;

/** Includes the one-block halo used by vanilla terrain invalidation, without block iteration. */
public final class LightSectionBounds {
    private LightSectionBounds() {}
    public static boolean intersects(PointLightChanges.Bounds b, int x, int y, int z) {
        return axis(b.minX(), b.maxX(), x) && axis(b.minY(), b.maxY(), y)
                && axis(b.minZ(), b.maxZ(), z);
    }
    private static boolean axis(int min, int max, int section) {
        long origin = (long) section * 16;
        return origin <= (long) max + 1 && origin + 15 >= (long) min - 1;
    }
}
