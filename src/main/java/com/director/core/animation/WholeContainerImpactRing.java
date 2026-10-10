package com.director.core.animation;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** A single horizontal particle ring outside the bottom footprint, moving radially outwards. */
public final class WholeContainerImpactRing {
    private WholeContainerImpactRing() {}

    public static List<WholeContainerDust.Particle> sample(List<BlockPos> blocks,
            WholeContainerAnimationConfig settings, long seed) {
        if (!settings.isDustEnabled() || settings.getDustIntensity() <= 0 || blocks.isEmpty()) return List.of();
        int bottom = blocks.stream().mapToInt(BlockPos::getY).min().orElse(0);
        var footprint = blocks.stream().filter(pos -> pos.getY() == bottom)
                .sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        double centerX = (minX + (double) maxX + 1) / 2;
        double centerZ = (minZ + (double) maxZ + 1) / 2;
        double halfWidth = (maxX - (double) minX + 1) / 2;
        double halfDepth = (maxZ - (double) minZ + 1) / 2;
        // Outside the circumscribed bottom rectangle even for hollow or disconnected shapes.
        double radius = Math.hypot(halfWidth, halfDepth) + 0.25 + settings.getDustSpread();
        int count = Math.min(WholeContainerDust.MAX_PARTICLES, Math.max(8,
                (int) (32 * Math.sqrt(footprint.size()) * settings.getDustIntensity())));
        double phase = new Random(seed).nextDouble() * Math.PI * 2;
        double speed = 0.2 + settings.getDustSpread() * 0.1;
        var particles = new ArrayList<WholeContainerDust.Particle>(count);
        for (int i = 0; i < count; i++) {
            double angle = phase + i * Math.PI * 2 / count;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            particles.add(new WholeContainerDust.Particle(footprint.getFirst(),
                    centerX + dx * radius, bottom + 0.15, centerZ + dz * radius,
                    dx * speed, 0, dz * speed));
        }
        return List.copyOf(particles);
    }
}
