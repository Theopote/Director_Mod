package com.director.core.animation;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerRenderViewTest {
    static class World implements BlockRenderView {
        BlockPos sampled;
        public BlockState getBlockState(BlockPos p) { sampled = p; return null; }
        public FluidState getFluidState(BlockPos p) { return null; }
        public BlockEntity getBlockEntity(BlockPos p) { sampled = p; return null; }
        public int getHeight() { return 384; }
        public int getBottomY() { return -64; }
        public float getBrightness(Direction d, boolean shaded) {
            if (!shaded) return 1;
            return d == Direction.UP ? 1 : d == Direction.DOWN ? 0.5f : 0.8f;
        }
        public LightingProvider getLightingProvider() { return null; }
        public int getLightLevel(LightType t, BlockPos p) { sampled = p; return 12; }
        public int getBaseLightLevel(BlockPos p, int darkness) { sampled = p; return 12 - darkness; }
        public int getColor(BlockPos p, ColorResolver r) { sampled = p; return 0x123456; }
    }
    @Test void movingAssemblySamplesLightsBiomeAndExternalNeighborsAtDisplayedPosition() {
        var world = new World();
        var origin = new BlockPos(-20, 30, 40);
        var transform = new Matrix4f().translation(8, -2, 3);
        var view = new WholeContainerRenderView(world, Map.of(), origin, transform);
        transform.identity(); // the render view owns a stable matrix copy
        var source = origin.add(1, 2, 3);
        var displayed = source.add(8, -2, 3);
        assertEquals(displayed, view.displayedPosition(source));
        assertEquals(12, view.getLightLevel(LightType.SKY, source));
        assertEquals(displayed, world.sampled);
        assertEquals(10, view.getBaseLightLevel(source, 2));
        assertEquals(displayed, world.sampled);
        assertEquals(0x123456, view.getColor(source, null));
        assertEquals(displayed, world.sampled);
        view.getBlockState(source);
        assertEquals(displayed, world.sampled);
    }
    @Test void identityKeepsNativeShadingAndRotationChangesFaceShadingSmoothly() {
        var world = new World();
        var resting = new WholeContainerRenderView(world, Map.of(), BlockPos.ORIGIN, new Matrix4f());
        for (var direction : Direction.values())
            assertEquals(world.getBrightness(direction, true), resting.getBrightness(direction, true));
        var rotated = new WholeContainerRenderView(world, Map.of(), BlockPos.ORIGIN,
                new Matrix4f().rotateZ((float) Math.PI));
        assertEquals(0.5f, rotated.getBrightness(Direction.UP, true), 0.000001);
        assertEquals(1, rotated.getBrightness(Direction.UP, false));
        var halfTurn = new WholeContainerRenderView(world, Map.of(), BlockPos.ORIGIN,
                new Matrix4f().rotateZ((float) Math.PI / 4));
        assertEquals(0.9f, halfTurn.getBrightness(Direction.UP, true), 0.000001);
    }
}
