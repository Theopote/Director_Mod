package com.director.core.animation;

import com.director.mixin.BillboardParticleAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Delayed waves and alpha follow game ticks, just like the existing dust particles. */
public final class WholeContainerImpactRingPlayer {
    private static final int MAX_BURSTS = 8;
    private static final List<Burst> bursts = new ArrayList<>();
    private static ClientWorld boundWorld;
    private static int tick;
    private record Active(Particle particle, int bornTick, float strength) {}
    private static final class Burst {
        final List<WholeContainerImpactRing.Wave> waves;
        final List<Active> particles = new ArrayList<>();
        final int start;
        int next;
        Burst(List<WholeContainerImpactRing.Wave> waves) { this.waves = waves; start = tick; }
        void clear() { particles.forEach(p -> p.particle().markDead()); }
    }
    private WholeContainerImpactRingPlayer() {}

    public static void play(MinecraftClient client, List<BlockPos> blocks,
            WholeContainerAnimationConfig settings, long seed) {
        bind(client.world);
        if (boundWorld == null) return;
        var waves = WholeContainerImpactRing.waves(blocks, settings, seed);
        if (waves.isEmpty()) return;
        if (bursts.size() >= MAX_BURSTS) bursts.removeFirst().clear();
        var burst = new Burst(waves);
        bursts.add(burst);
        advance(client, burst); // First ring appears on the exact handoff frame.
    }

    public static void clear() {
        bursts.forEach(Burst::clear);
        bursts.clear();
    }

    private static void bind(ClientWorld world) {
        if (boundWorld == world) return;
        clear();
        boundWorld = world; tick = 0;
    }

    public static void tick(MinecraftClient client) {
        bind(client.world);
        if (boundWorld == null || client.isPaused()) return;
        tick++;
        for (Iterator<Burst> it = bursts.iterator(); it.hasNext();) {
            var burst = it.next();
            advance(client, burst);
            if (burst.next == burst.waves.size() && burst.particles.isEmpty()) it.remove();
        }
    }

    private static void advance(MinecraftClient client, Burst burst) {
        while (burst.next < burst.waves.size()
                && tick - burst.start >= burst.waves.get(burst.next).delayTicks()) {
            var wave = burst.waves.get(burst.next++);
            for (var point : wave.particles()) {
                Particle particle = client.particleManager.addParticle(ParticleTypes.END_ROD,
                        point.x(), point.y(), point.z(), point.vx(), point.vy(), point.vz());
                if (particle == null) continue;
                particle.setMaxAge(WholeContainerImpactRing.WAVE_LIFETIME_TICKS);
                particle.scale((float) Math.sqrt(wave.strength()));
                burst.particles.add(new Active(particle, tick, wave.strength()));
            }
        }
        for (Iterator<Active> it = burst.particles.iterator(); it.hasNext();) {
            var active = it.next();
            int age = tick - active.bornTick();
            if (!active.particle().isAlive() || age >= WholeContainerImpactRing.WAVE_LIFETIME_TICKS) {
                active.particle().markDead(); it.remove(); continue;
            }
            if (active.particle() instanceof BillboardParticleAccessor alpha)
                alpha.director$setAlpha(WholeContainerImpactRing.alpha(active.strength(), age));
        }
    }
}
