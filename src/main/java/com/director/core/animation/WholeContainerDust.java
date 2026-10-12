package com.director.core.animation;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import net.minecraft.util.math.Direction;

/** Bounded, deterministic footprint sampling; emitting game particles is the playback layer's job. */
public final class WholeContainerDust {
    public static final int MAX_PARTICLES = 256;
    private WholeContainerDust() {}
    private record Edge(BlockPos source, Direction direction) {}
    public record Particle(BlockPos source, double x, double y, double z, double vx, double vy, double vz) {}

    public static float eventTime(ClipAnimationConfig config, float start, float end, boolean exit) {
        var window = WholeContainerAnimation.window(config, start, end);
        return exit ? window.start() : window.end();
    }

    public static List<Particle> sample(List<BlockPos> blocks, WholeContainerAnimationConfig settings, long seed) {
        if (!settings.isDustEnabled() || settings.getDustIntensity() <= 0 || blocks.isEmpty()) return List.of();
        if (settings.getDustPreset() == WholeContainerAnimationConfig.DustPreset.SHOCKWAVE)
            return WholeContainerImpactRing.sample(blocks, settings, seed);
        int bottom = blocks.stream().mapToInt(BlockPos::getY).min().orElse(0);
        // Stable ordering keeps the same burst independent of container HashSet iteration order.
        var footprint = blocks.stream().filter(p -> p.getY() == bottom)
                .sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
        var occupied = new HashSet<>(blocks);
        var edges = new ArrayList<Edge>();
        boolean debris = settings.getDustPreset() == WholeContainerAnimationConfig.DustPreset.DEBRIS_CLOUD;
        var sources = debris ? blocks.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList() : footprint;
        for (var pos : sources)
            for (var direction : Direction.Type.HORIZONTAL)
                if (!occupied.contains(pos.offset(direction))) edges.add(new Edge(pos, direction));
        int count = debris ? Math.clamp(
                (int) (24 * Math.sqrt(edges.size()) * settings.getDustIntensity()), 8, MAX_PARTICLES)
                : Math.clamp(
                (int) (12 * Math.sqrt(footprint.size()) * settings.getDustIntensity()), 1, settings.getDustPreset() == WholeContainerAnimationConfig.DustPreset.IMPACT_DUST
                        ? MAX_PARTICLES / 2 : MAX_PARTICLES);
        var random = new Random(seed);
        var particles = new ArrayList<Particle>(count);
        double spread = settings.getDustSpread();
        for (int i = 0; i < count; i++) {
            var edge = edges.get(random.nextInt(edges.size()));
            var source = edge.source();
            var direction = edge.direction();
            double outward = 0.55 + spread * random.nextDouble();
            double tangent = (random.nextDouble() - 0.5) * 0.8;
            double dx = direction.getOffsetX() * outward + direction.getOffsetZ() * tangent;
            double dz = direction.getOffsetZ() * outward + direction.getOffsetX() * tangent;
            particles.add(new Particle(source, source.getX() + 0.5 + dx,
                    (debris ? source.getY() + 0.15 : bottom + 0.05) + random.nextDouble() * (debris ? 0.7 : 0.15),
                    source.getZ() + 0.5 + dz,
                    dx * (debris ? 0.25 : 0.15), 0.08 + random.nextDouble() * (debris ? 0.2 : 0.1),
                    dz * (debris ? 0.25 : 0.15)));
        }
        return List.copyOf(particles);
    }
}
