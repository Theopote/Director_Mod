package com.director.actor.path;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.OptionalDouble;

/** Uses collision shapes rather than visual block height, including slabs and low ceilings. */
public final class ActorGroundTerrain implements GroundRoute.Terrain {
    private final Entity entity;
    public ActorGroundTerrain(Entity entity) { this.entity = entity; }

    @Override public OptionalDouble floor(double x, double z, double y, double up, double down) {
        if (!entity.getEntityWorld().isChunkLoaded(BlockPos.ofFloored(x, y, z))) return OptionalDouble.empty();
        // Footprint support permits stepping onto slabs before the centre reaches the riser.
        double half = Math.max(0.05, entity.getWidth() / 2.0 - 0.001);
        Box column = new Box(x - half, y - down - 0.001, z - half, x + half, y + up + 0.001, z + half);
        double best = Double.NEGATIVE_INFINITY;
        for (var shape : entity.getEntityWorld().getBlockCollisions(entity, column)) {
            for (Box box : shape.getBoundingBoxes()) {
                if (box.maxY > y + up + 1e-5 || box.maxY < y - down - 1e-5) continue;
                if (box.maxY > best && clear(new Vec3d(x, box.maxY, z))) best = box.maxY;
            }
        }
        return Double.isFinite(best) ? OptionalDouble.of(best) : OptionalDouble.empty();
    }

    @Override public boolean clear(Vec3d feet) {
        double half = Math.max(0.05, entity.getWidth() / 2.0 - 0.001);
        for (double dx : new double[]{-half, half}) for (double dz : new double[]{-half, half}) {
            if (!entity.getEntityWorld().isChunkLoaded(BlockPos.ofFloored(feet.x + dx, feet.y, feet.z + dz))) return false;
        }
        Box body = new Box(feet.x - half, feet.y + 0.001, feet.z - half,
                feet.x + half, feet.y + entity.getHeight() - 0.001, feet.z + half);
        return !entity.getEntityWorld().getBlockCollisions(entity, body).iterator().hasNext();
    }
}
