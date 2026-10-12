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
import org.joml.Vector3f;
import java.util.Map;

/** Snapshot neighbors for AO/culling, with light and biome sampling at the displayed world position. */
public final class WholeContainerRenderView implements BlockRenderView {
    private final BlockRenderView world;
    private final Map<BlockPos, BlockState> states;
    private final BlockPos origin;
    private final Matrix4f transform;
    private final boolean cullNeighbors;
    public WholeContainerRenderView(BlockRenderView world, Map<BlockPos, BlockState> states,
            BlockPos origin, Matrix4f transform) {
        this(world, states, origin, transform, true);
    }
    public WholeContainerRenderView(BlockRenderView world, Map<BlockPos, BlockState> states,
            BlockPos origin, Matrix4f transform, boolean cullNeighbors) {
        this.cullNeighbors = cullNeighbors;
        this.world = world; this.states = states; this.origin = origin;
        this.transform = new Matrix4f(transform);
    }
    public boolean cullNeighbors() { return cullNeighbors; }
    public BlockPos displayedPosition(BlockPos pos) {
        var p = transform.transformPosition(new Vector3f(
                pos.getX() - origin.getX() + 0.5f, pos.getY() - origin.getY() + 0.5f,
                pos.getZ() - origin.getZ() + 0.5f));
        return BlockPos.ofFloored(origin.getX() + (double) p.x, origin.getY() + (double) p.y,
                origin.getZ() + (double) p.z);
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        var state = states.get(pos);
        return state != null ? state : world.getBlockState(displayedPosition(pos));
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return world.getBlockEntity(displayedPosition(pos)); }
    @Override public int getHeight() { return world.getHeight(); }
    @Override public int getBottomY() { return world.getBottomY(); }
    @Override public float getBrightness(Direction direction, boolean shaded) {
        if (!shaded) return world.getBrightness(direction, false);
        var normal = transform.transformDirection(new Vector3f(
                direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ())).normalize();
        // Interpolate face shading continuously as the assembly rotates, matching native shading at rest.
        return normal.x * normal.x * world.getBrightness(normal.x >= 0 ? Direction.EAST : Direction.WEST, true)
                + normal.y * normal.y * world.getBrightness(normal.y >= 0 ? Direction.UP : Direction.DOWN, true)
                + normal.z * normal.z * world.getBrightness(normal.z >= 0 ? Direction.SOUTH : Direction.NORTH, true);
    }
    @Override public LightingProvider getLightingProvider() { return world.getLightingProvider(); }
    @Override public int getLightLevel(LightType type, BlockPos pos) { return world.getLightLevel(type, displayedPosition(pos)); }
    @Override public int getBaseLightLevel(BlockPos pos, int darkness) { return world.getBaseLightLevel(displayedPosition(pos), darkness); }
    @Override public int getColor(BlockPos pos, ColorResolver resolver) { return world.getColor(displayedPosition(pos), resolver); }
}
