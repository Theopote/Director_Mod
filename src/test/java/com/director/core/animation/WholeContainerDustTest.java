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
            assertTrue(dx * dx + dz * dz <= 4.00001);
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
}
