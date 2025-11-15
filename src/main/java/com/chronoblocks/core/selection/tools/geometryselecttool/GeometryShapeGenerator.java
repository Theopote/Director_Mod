package com.chronoblocks.core.selection.tools.geometryselecttool;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * 🧮 几何体方块生成器。
 */
public final class GeometryShapeGenerator {

    private GeometryShapeGenerator() {}

    public static Set<BlockPos> generate(BlockPos center, GeometryShape shape, GeometryConfig config) {
        return switch (shape) {
            case SPHERE -> fillSphere(center, config.getRadius(), config.isHollow());
            case CYLINDER -> fillCylinder(center, config.getRadius(), config.getHeight(), config.isHollow());
            case PYRAMID -> fillPyramid(center, config.getRadius(), config.getHeight(), config.isHollow());
            case BOX -> fillBox(center, config.getRadius(), config.getHeight(), config.isHollow());
        };
    }

    private static Set<BlockPos> fillSphere(BlockPos center, double radius, boolean hollow) {
        Set<BlockPos> result = new HashSet<>();
        int ri = (int) Math.ceil(radius);
        double radiusSq = radius * radius;
        double innerSq = (radius - 1.0) * (radius - 1.0);

        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq <= radiusSq && (!hollow || distanceSq >= innerSq)) {
                        result.add(center.add(dx, dy, dz));
                    }
                }
            }
        }
        return result;
    }

    private static Set<BlockPos> fillCylinder(BlockPos center, double radius, double height, boolean hollow) {
        Set<BlockPos> result = new HashSet<>();
        int ri = (int) Math.ceil(radius);
        int hi = (int) Math.max(1, Math.floor(height / 2.0));
        double radiusSq = radius * radius;
        double innerSq = (radius - 1.0) * (radius - 1.0);

        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq <= radiusSq && (!hollow || distanceSq >= innerSq)) {
                    for (int dy = -hi; dy <= hi; dy++) {
                        result.add(center.add(dx, dy, dz));
                    }
                }
            }
        }
        return result;
    }

    private static Set<BlockPos> fillPyramid(BlockPos center, double radius, double height, boolean hollow) {
        Set<BlockPos> result = new HashSet<>();
        int h = (int) Math.max(1, Math.ceil(height));

        for (int dy = 0; dy < h; dy++) {
            double layerRadius = Math.max(0, radius * (1.0 - (double) dy / h));
            int ri = (int) Math.ceil(layerRadius);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (Math.abs(dx) <= layerRadius && Math.abs(dz) <= layerRadius) {
                        if (!hollow || dy == 0 || dy == h - 1
                            || Math.abs(dx) == ri || Math.abs(dz) == ri) {
                            result.add(center.add(dx, dy, dz));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Set<BlockPos> fillBox(BlockPos center, double radius, double height, boolean hollow) {
        Set<BlockPos> result = new HashSet<>();
        int r = (int) Math.ceil(radius);
        int halfHeight = (int) Math.max(1, Math.floor(height / 2.0));

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -halfHeight; dy <= halfHeight; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!hollow || Math.abs(dx) == r || Math.abs(dz) == r || Math.abs(dy) == halfHeight) {
                        result.add(center.add(dx, dy, dz));
                    }
                }
            }
        }
        return result;
    }
}

