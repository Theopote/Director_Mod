package com.director.core.animation;

import com.google.gson.Gson;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerDustTest {
    private WholeContainerAnimationConfig settings() {
        var s = new WholeContainerAnimationConfig(); s.setDustEnabled(true); return s;
    }
    @Test void burstUsesActualBottomBlocksAndStaysWithinSpread() {
        var blocks = List.of(new BlockPos(-10, 4, 20), new BlockPos(10, 4, -20), new BlockPos(0, 8, 0));
        var s = settings(); s.setDustSpread(2);
        var particles = WholeContainerDust.sample(blocks, s, 42);
        assertFalse(particles.isEmpty());
        for (var p : particles) {
            assertEquals(4, p.source().getY());
            double dx = p.x() - p.source().getX() - 0.5, dz = p.z() - p.source().getZ() - 0.5;
            assertTrue(dx * dx + dz * dz <= 2.55 * 2.55 + 0.16 + 0.00001);
            assertTrue(Math.abs(dx) >= 0.55 || Math.abs(dz) >= 0.55);
            assertTrue(p.y() >= 4.05 && p.y() <= 4.2); assertTrue(p.vy() > 0);
        }
        var reversed = new ArrayList<>(blocks); Collections.reverse(reversed);
        assertEquals(particles, WholeContainerDust.sample(reversed, s, 42));
    }
    @Test void quantityIsBoundedForLargeAssembliesAndZeroDisablesEmission() {
        var blocks = new ArrayList<BlockPos>();
        for (int i = 0; i < 10000; i++) blocks.add(new BlockPos(i, 0, 0));
        var s = settings(); s.setDustIntensity(5);
        assertEquals(WholeContainerDust.MAX_PARTICLES, WholeContainerDust.sample(blocks, s, 1).size());
        s.setDustIntensity(0); assertTrue(WholeContainerDust.sample(blocks, s, 1).isEmpty());
        s.setDustIntensity(1); s.setDustEnabled(false);
        assertTrue(WholeContainerDust.sample(blocks, s, 1).isEmpty());
        assertTrue(WholeContainerDust.sample(List.of(), settings(), 1).isEmpty());
    }
    @Test void eventUsesEffectiveAnimationWindowForArrivalDepartureAndInstant() {
        var c = ClipAnimationConfig.defaultConfig(); c.setDuration(3); c.setDelay(1);
        assertEquals(14, WholeContainerDust.eventTime(c, 10, 20, false));
        assertEquals(11, WholeContainerDust.eventTime(c, 10, 20, true));
        assertEquals(12, WholeContainerDust.eventTime(c, 10, 12, false));
        c.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.INSTANT);
        assertEquals(11, WholeContainerDust.eventTime(c, 10, 20, false));
    }
    @Test void jsonAndCopiesRetainIndependentSettingsAndOldProjectsDefaultOff() {
        var gson = new Gson();
        var old = gson.fromJson("{}", WholeContainerAnimationConfig.class);
        assertFalse(old.isDustEnabled()); assertEquals(1, old.getDustIntensity()); assertEquals(0.6f, old.getDustSpread());
        var s = settings(); s.setDustIntensity(2); s.setDustSpread(3);
        var loaded = gson.fromJson(gson.toJson(s), WholeContainerAnimationConfig.class);
        assertEquals(WholeContainerDust.sample(List.of(BlockPos.ORIGIN), s, 4),
                WholeContainerDust.sample(List.of(BlockPos.ORIGIN), loaded, 4));
        var copy = s.copy(); copy.setDustSpread(4); assertEquals(3, s.getDustSpread());
        assertTrue(copy.isDustEnabled()); assertEquals(2, copy.getDustIntensity());
        s.setDustIntensity(Float.NaN); s.setDustSpread(Float.POSITIVE_INFINITY);
        assertEquals(1, s.getDustIntensity()); assertEquals(0.6f, s.getDustSpread());
    }
    @Test void presetSelectionCanDisableEffectsAndRoundTripsWithoutResettingTweaks() {
        var s = settings();
        s.setDustPreset(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST);
        assertTrue(s.isDustEnabled()); assertEquals(2.5f, s.getDustIntensity()); assertEquals(1.2f, s.getDustSpread());
        s.setDustIntensity(3); s.setDustSpread(2);
        var loaded = new Gson().fromJson(new Gson().toJson(s), WholeContainerAnimationConfig.class);
        assertEquals(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST, loaded.getDustPreset());
        assertEquals(3, loaded.getDustIntensity()); assertEquals(2, loaded.copy().getDustSpread());
        var particles = WholeContainerDust.sample(List.of(BlockPos.ORIGIN), loaded, 42);
        assertTrue(particles.size() * 2 <= WholeContainerDust.MAX_PARTICLES);
        loaded.setDustPreset(WholeContainerAnimationConfig.DustPreset.NONE);
        assertFalse(loaded.isDustEnabled());
        assertTrue(WholeContainerDust.sample(List.of(BlockPos.ORIGIN), loaded, 42).isEmpty());
        loaded.setDustPreset(WholeContainerAnimationConfig.DustPreset.SOFT_DUST);
        assertEquals(1, loaded.getDustIntensity()); assertEquals(0.6f, loaded.getDustSpread());
    }
    @Test void particlesSpawnOutsideSolidFootprintEvenAtZeroSpread() {
        var blocks = new ArrayList<BlockPos>();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) blocks.add(new BlockPos(x, 0, z));
        var s = settings(); s.setDustSpread(0); s.setDustIntensity(5);
        for (var particle : WholeContainerDust.sample(blocks, s, 42)) {
            var cell = BlockPos.ofFloored(particle.x(), particle.y(), particle.z());
            assertFalse(blocks.contains(cell));
        }
    }
    @Test void changingAnimationPresetsAndResetDoesNotClearVisualEffects() {
        var config = ClipAnimationConfig.defaultConfig();
        config.getWholeContainer().setDustPreset(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST);
        config.getWholeContainer().setDustIntensity(4);
        for (var preset : WholeContainerAnimationPresets.Preset.values()) {
            WholeContainerAnimationPresets.apply(config, preset, null);
            assertEquals(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST, config.getWholeContainer().getDustPreset());
            assertEquals(4, config.getWholeContainer().getDustIntensity());
            WholeContainerAnimationPresets.resetTransform(config);
            assertEquals(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST, config.getWholeContainer().getDustPreset());
        }
    }
    @Test void debrisCloudUsesAllExposedLevelsAndSupportsDensitySpreadAndPersistence() {
        var blocks = List.of(new BlockPos(0, 0, 0), new BlockPos(0, 1, 0), new BlockPos(0, 2, 0));
        var s = settings();
        s.setDustPreset(WholeContainerAnimationConfig.DustPreset.DEBRIS_CLOUD);
        s.setDustIntensity(0.1f); s.setDustSpread(0); s.setDebrisRatio(0.35f);
        var sparse = WholeContainerDust.sample(blocks, s, 42);
        assertTrue(sparse.stream().anyMatch(p -> p.source().getY() > 0));
        for (var particle : sparse) {
            assertFalse(blocks.contains(BlockPos.ofFloored(particle.x(), particle.y(), particle.z())));
        }
        s.setDustIntensity(5); s.setDustSpread(5);
        var dense = WholeContainerDust.sample(blocks, s, 42);
        assertTrue(dense.size() > sparse.size());
        assertTrue(dense.size() <= WholeContainerDust.MAX_PARTICLES);
        assertTrue(dense.stream().anyMatch(p -> Math.abs(p.x() - 0.5) > 1));
        var loaded = new Gson().fromJson(new Gson().toJson(s), WholeContainerAnimationConfig.class);
        assertEquals(dense, WholeContainerDust.sample(blocks, loaded, 42));
        assertEquals(0.35f, loaded.getDebrisRatio());
        assertEquals(0.35f, loaded.copy().getDebrisRatio());
        s.setDustIntensity(0);
        assertTrue(WholeContainerDust.sample(blocks, s, 42).isEmpty());
    }
}
