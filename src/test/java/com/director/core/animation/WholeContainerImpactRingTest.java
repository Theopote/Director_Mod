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
}
