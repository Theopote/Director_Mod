package com.director.core.animation;

import com.google.gson.Gson;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerImpactRingTest {
    private WholeContainerAnimationConfig settings() {
        var s = new WholeContainerAnimationConfig();
        s.setDustPreset(WholeContainerAnimationConfig.DustPreset.SHOCKWAVE);
        return s;
    }
    @Test void ringIsHorizontalSymmetricAndMovesOutwardAroundTheFootprint() {
        var blocks = List.of(new BlockPos(-2, 3, -1), new BlockPos(1, 3, 0), new BlockPos(40, 10, 40));
        var s = settings();
        var particles = WholeContainerDust.sample(blocks, s, 42);
        double centerX = 0, centerZ = 0;
        double radius = Math.hypot(2, 1) + 0.25 + s.getDustSpread();
        double sumX = 0, sumZ = 0, sumVx = 0, sumVz = 0;
        for (var p : particles) {
            assertEquals(3.15, p.y(), 0.000001);
            assertEquals(0, p.vy());
            double dx = p.x() - centerX, dz = p.z() - centerZ;
            assertEquals(radius, Math.hypot(dx, dz), 0.000001);
            assertTrue(dx * p.vx() + dz * p.vz() > 0);
            sumX += p.x(); sumZ += p.z(); sumVx += p.vx(); sumVz += p.vz();
        }
        assertEquals(0, sumX / particles.size(), 0.000001);
        assertEquals(0, sumZ / particles.size(), 0.000001);
        assertEquals(0, sumVx, 0.000001); assertEquals(0, sumVz, 0.000001);
    }
    @Test void ringStaysOutsideSolidHollowAndDisconnectedBottomShapes() {
        var solid = new ArrayList<BlockPos>();
        for (int x = -3; x < 4; x++) for (int z = -2; z < 3; z++) solid.add(new BlockPos(x, -10, z));
        var hollow = solid.stream().filter(p -> Math.abs(p.getX()) == 3 || Math.abs(p.getZ()) == 2).toList();
        for (var shape : List.of(solid, hollow, List.of(new BlockPos(-10, -10, 0), new BlockPos(10, -10, 0)))) {
            var s = settings(); s.setDustSpread(0);
            for (var p : WholeContainerDust.sample(shape, s, 42))
                assertFalse(shape.contains(BlockPos.ofFloored(p.x(), p.y(), p.z())));
        }
    }
    @Test void spreadControlsRadiusAndExpansionAndIntensityControlsBoundedCount() {
        var blocks = List.of(BlockPos.ORIGIN);
        var s = settings(); s.setDustSpread(0); s.setDustIntensity(0.5f);
        var small = WholeContainerDust.sample(blocks, s, 42);
        s.setDustSpread(5); s.setDustIntensity(5);
        var big = WholeContainerDust.sample(blocks, s, 42);
        assertTrue(big.size() > small.size());
        assertTrue(Math.hypot(big.getFirst().vx(), big.getFirst().vz())
                > Math.hypot(small.getFirst().vx(), small.getFirst().vz()));
        assertTrue(Math.hypot(big.getFirst().x() - 0.5, big.getFirst().z() - 0.5)
                > Math.hypot(small.getFirst().x() - 0.5, small.getFirst().z() - 0.5));
        var large = new ArrayList<BlockPos>();
        for (int i = 0; i < 10000; i++) large.add(new BlockPos(i, 0, 0));
        assertEquals(WholeContainerDust.MAX_PARTICLES, WholeContainerDust.sample(large, s, 42).size());
    }
    @Test void zeroIntensityNoEffectAndEmptyAssembliesDoNotEmit() {
        var s = settings(); s.setDustIntensity(0);
        assertTrue(WholeContainerDust.sample(List.of(BlockPos.ORIGIN), s, 42).isEmpty());
        s.setDustIntensity(1); s.setDustPreset(WholeContainerAnimationConfig.DustPreset.NONE);
        assertTrue(WholeContainerDust.sample(List.of(BlockPos.ORIGIN), s, 42).isEmpty());
        assertTrue(WholeContainerDust.sample(List.of(), settings(), 42).isEmpty());
    }
    @Test void jsonCopyAndInputOrderKeepTheSameRingWithoutSharingSettings() {
        var s = settings(); s.setDustIntensity(2); s.setDustSpread(3);
        var blocks = List.of(BlockPos.ORIGIN, new BlockPos(4, 0, 4));
        var expected = WholeContainerDust.sample(blocks, s, 42);
        var loaded = new Gson().fromJson(new Gson().toJson(s), WholeContainerAnimationConfig.class);
        assertEquals(WholeContainerAnimationConfig.DustPreset.SHOCKWAVE, loaded.getDustPreset());
        assertEquals(expected, WholeContainerDust.sample(blocks, loaded, 42));
        var reverse = new ArrayList<>(blocks); Collections.reverse(reverse);
        assertEquals(expected, WholeContainerDust.sample(reverse, s.copy(), 42));
        var copy = s.copy(); copy.setDustSpread(1); assertEquals(3, s.getDustSpread());
    }
    @Test void multipleWavesHaveOrderedDelaysAndDecayWithoutExceedingTotalBudget() {
        var s = settings(); s.setShockwaveCount(4); s.setShockwaveDecay(0.4f);
        var waves = WholeContainerImpactRing.waves(List.of(BlockPos.ORIGIN), s, 42);
        assertEquals(4, waves.size());
        int total = 0;
        for (int i = 0; i < waves.size(); i++) {
            var wave = waves.get(i);
            assertEquals(i * WholeContainerImpactRing.WAVE_INTERVAL_TICKS, wave.delayTicks());
            assertEquals(Math.pow(0.6, i), wave.strength(), 0.00001);
            assertTrue(wave.particles().size() >= 8);
            if (i > 0) assertTrue(wave.particles().size() <= waves.get(i - 1).particles().size());
            total += wave.particles().size();
        }
        assertTrue(total <= WholeContainerDust.MAX_PARTICLES);
        var large = new ArrayList<BlockPos>();
        for (int i = 0; i < 10000; i++) large.add(new BlockPos(i, 0, 0));
        s.setShockwaveCount(6); s.setDustIntensity(5); s.setShockwaveDecay(0);
        assertTrue(WholeContainerImpactRing.waves(large, s, 42).stream()
                .mapToInt(w -> w.particles().size()).sum() <= WholeContainerDust.MAX_PARTICLES);
    }
    @Test void eachWaveHasEvenAngularSpacingAndBalancedVelocity() {
        var s = settings();
        for (var wave : WholeContainerImpactRing.waves(List.of(BlockPos.ORIGIN), s, 42)) {
            double sumX = 0, sumZ = 0;
            for (var p : wave.particles()) { sumX += p.vx(); sumZ += p.vz(); }
            assertEquals(0, sumX, 0.000001); assertEquals(0, sumZ, 0.000001);
        }
    }
    @Test void eachWaveFadesMonotonicallyToZeroWithReducedInitialAlpha() {
        for (float strength : new float[] {1, 0.65f, 0.3f}) {
            assertEquals(strength, WholeContainerImpactRing.alpha(strength, 0));
            float previous = strength;
            for (int age = 1; age <= WholeContainerImpactRing.WAVE_LIFETIME_TICKS; age++) {
                float alpha = WholeContainerImpactRing.alpha(strength, age);
                assertTrue(alpha >= 0 && alpha <= previous); previous = alpha;
            }
            assertEquals(0, WholeContainerImpactRing.alpha(strength, WholeContainerImpactRing.WAVE_LIFETIME_TICKS));
        }
    }
    @Test void singleWaveNoDecayAndFullDecayAreSupported() {
        var s = settings(); s.setShockwaveCount(1);
        assertEquals(1, WholeContainerImpactRing.waves(List.of(BlockPos.ORIGIN), s, 42).size());
        s.setShockwaveCount(3); s.setShockwaveDecay(0);
        var waves = WholeContainerImpactRing.waves(List.of(BlockPos.ORIGIN), s, 42);
        assertEquals(3, waves.size());
        for (var wave : waves) assertEquals(1, wave.strength());
        s.setShockwaveDecay(1);
        assertEquals(1, WholeContainerImpactRing.waves(List.of(BlockPos.ORIGIN), s, 42).size());
    }
    @Test void waveControlsHaveLegacyDefaultsAndSurviveJsonCopiesAndAnimationPresets() {
        var gson = new Gson();
        var legacy = gson.fromJson("{}", WholeContainerAnimationConfig.class);
        assertEquals(3, legacy.getShockwaveCount()); assertEquals(0.35f, legacy.getShockwaveDecay());
        var c = ClipAnimationConfig.defaultConfig(); var s = c.getWholeContainer();
        s.setDustPreset(WholeContainerAnimationConfig.DustPreset.SHOCKWAVE);
        s.setShockwaveCount(5); s.setShockwaveDecay(0.2f);
        var loaded = gson.fromJson(gson.toJson(c), ClipAnimationConfig.class);
        assertEquals(5, loaded.getWholeContainer().getShockwaveCount());
        assertEquals(0.2f, loaded.getWholeContainer().getShockwaveDecay());
        assertEquals(5, c.copy().getWholeContainer().getShockwaveCount());
        WholeContainerAnimationPresets.apply(c, WholeContainerAnimationPresets.Preset.ELASTIC_GROW, null);
        assertEquals(5, c.getWholeContainer().getShockwaveCount());
        assertEquals(0.2f, c.getWholeContainer().getShockwaveDecay());
        s.setShockwaveCount(99); s.setShockwaveDecay(Float.NaN);
        assertEquals(6, s.getShockwaveCount()); assertEquals(0.35f, s.getShockwaveDecay());
    }
}
