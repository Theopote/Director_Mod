package com.director.core.animation;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Bounded, deterministic footprint sampling; emitting game particles is the playback layer's job. */
public final class WholeContainerDust {
    public static final int MAX_PARTICLES = 256;
    private WholeContainerDust() {}
    public record Particle(BlockPos source, double x, double y, double z, double vx, double vy, double vz) {}

    public static float eventTime(ClipAnimationConfig config, float start, float end, boolean exit) {
        var window = WholeContainerAnimation.window(config, start, end);
        return exit ? window.start() : window.end();
    }

    public static List<Particle> sample(List<BlockPos> blocks, WholeContainerAnimationConfig settings, long seed) {
        if (!settings.isDustEnabled() || settings.getDustIntensity() <= 0 || blocks.isEmpty()) return List.of();
        int bottom = blocks.stream().mapToInt(BlockPos::getY).min().orElse(0);
        // Stable ordering keeps the same burst independent of container HashSet iteration order.
        var footprint = blocks.stream().filter(p -> p.getY() == bottom)
                .sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
        int count = Math.min(MAX_PARTICLES, Math.max(1,
                (int) (12 * Math.sqrt(footprint.size()) * settings.getDustIntensity())));
        var random = new Random(seed);
        var particles = new ArrayList<Particle>(count);
        double spread = settings.getDustSpread();
        for (int i = 0; i < count; i++) {
            var source = footprint.get(random.nextInt(footprint.size()));
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = spread * Math.sqrt(random.nextDouble());
            double dx = Math.cos(angle) * radius, dz = Math.sin(angle) * radius;
            particles.add(new Particle(source, source.getX() + 0.5 + dx,
                    bottom + 0.05 + random.nextDouble() * 0.15, source.getZ() + 0.5 + dz,
                    dx * 0.15, 0.08 + random.nextDouble() * 0.1, dz * 0.15));
        }
        return List.copyOf(particles);
    }
}
