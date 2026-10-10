package com.director.core.animation;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Sequential, decaying horizontal particle ring outside the bottom footprint, moving radially outwards. */
public final class WholeContainerImpactRing {
    public static final int WAVE_INTERVAL_TICKS = 5;
    public static final int WAVE_LIFETIME_TICKS = 28;
    public record Wave(int delayTicks, float strength, List<WholeContainerDust.Particle> particles) {}
    private WholeContainerImpactRing() {}

    public static float alpha(float strength, int ageTicks) {
        float remaining = Math.clamp(1 - ageTicks / (float) WAVE_LIFETIME_TICKS, 0, 1);
        return strength * remaining * remaining;
    }

    public static List<Wave> waves(List<BlockPos> blocks, WholeContainerAnimationConfig settings, long seed) {
        var base = sample(blocks, settings, seed);
        if (base.isEmpty()) return List.of();
        int rings = settings.getShockwaveCount();
        int budget = WholeContainerDust.MAX_PARTICLES / rings;
        var waves = new ArrayList<Wave>();
        for (int wave = 0; wave < rings; wave++) {
            float strength = (float) Math.pow(1 - settings.getShockwaveDecay(), wave);
            if (strength < 0.01f) continue;
            int count = Math.min(budget, Math.max(8, (int) (base.size() * strength)));
            var particles = sampleCount(blocks, settings, seed, count);
            waves.add(new Wave(wave * WAVE_INTERVAL_TICKS, strength, List.copyOf(particles)));
        }
        return List.copyOf(waves);
    }

    public static List<WholeContainerDust.Particle> sample(List<BlockPos> blocks,
            WholeContainerAnimationConfig settings, long seed) {
        return sampleCount(blocks, settings, seed, 0);
    }

    private static List<WholeContainerDust.Particle> sampleCount(List<BlockPos> blocks,
            WholeContainerAnimationConfig settings, long seed, int requestedCount) {
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
        int count = requestedCount > 0 ? requestedCount
                : Math.min(WholeContainerDust.MAX_PARTICLES, Math.max(8,
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
