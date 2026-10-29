package com.director.core.animation;

import com.director.client.renderer.mesh.SectionGpuBufferCache;
import com.director.core.container.visibility.BlockVisualStateManager.RenderEntry;
import com.director.timeline.model.TimelineClip;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/** Persistent opaque, stationary overlay geometry; unsupported entries keep the native path. */
public final class StaticSectionMeshRenderer {
    private static final AtomicLong INVALIDATION = new AtomicLong();
    private static StaticSectionMeshRenderer instance;
    private final SectionGpuBufferCache gpu = new SectionGpuBufferCache(64L * 1024 * 1024);
    private final Map<TimelineClip, Assembly> assemblies = new LinkedHashMap<>(16, .75f, true);
    private Object world;
    private long epoch = -1, version;
    private int retainedPositions;
    private long hits, builds, lastLog;
    private long stableSince;
    private boolean failed;

    private static final class Assembly {
        final Map<BlockPos, BlockState> states;
        final Map<Long, List<RenderEntry>> sections = new LinkedHashMap<>();
        final Map<Long, Mesh> meshes = new HashMap<>();
        StaticMeshMembership membership;
        final long version;
        Assembly(Map<BlockPos, BlockState> states, long version) { this.states = states; this.version = version; }
    }
    private record Material(TimelineClip clip, RenderLayer layer) {}
    private record Mesh(SectionGpuBufferCache.Key key, int indices) {}

    public static void invalidate() { INVALIDATION.incrementAndGet(); }

    /** World renderer lifecycle callbacks run on the render thread, including disconnect. */
    public static void release() {
        invalidate();
        if (instance == null) return;
        try { instance.gpu.close(); }
        catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger("DirectorSectionMeshes").warn("Could not release all cached GPU buffers", failure);
        } finally {
            instance.assemblies.clear(); instance.retainedPositions = 0;
            instance.world = null; instance.epoch = -1;
        }
    }

    static Set<BlockPos> draw(ClientWorld world, MatrixStack matrices, Collection<RenderEntry> entries,
            Map<TimelineClip, Map<BlockPos, BlockState>> states, Map<TimelineClip, WholeContainerRenderView> views,
            Set<BlockPos> excluded, Predicate<RenderEntry> stationaryOpaque) {
        if (IrisTerrainCompat.shaderPackInUse() || states.isEmpty()) return Set.of();
        if (instance == null) instance = new StaticSectionMeshRenderer();
        return instance.render(world, matrices, entries, states, views, excluded, stationaryOpaque);
    }

    private Set<BlockPos> render(ClientWorld currentWorld, MatrixStack matrices, Collection<RenderEntry> entries,
            Map<TimelineClip, Map<BlockPos, BlockState>> states, Map<TimelineClip, WholeContainerRenderView> views,
            Set<BlockPos> excluded, Predicate<RenderEntry> stationaryOpaque) {
        long currentEpoch = INVALIDATION.get();
        if (world != currentWorld || epoch != currentEpoch) {
            gpu.close(); assemblies.clear(); retainedPositions = 0;
            world = currentWorld; epoch = currentEpoch;
            stableSince = System.nanoTime(); failed = false;
        }
        // Do not repeatedly tessellate/upload while world restoration or lighting is still changing.
        if (failed || System.nanoTime() - stableSince < 100_000_000L) return Set.of();
        var groups = new LinkedHashMap<TimelineClip, Map<Long, List<RenderEntry>>>();
        for (var entry : entries) {
            if (!stationaryOpaque.test(entry) || entry.clip() == null || excluded.contains(entry.pos())
                    || entry.clip().getAnimationConfig() == null || entry.clip().getAnimationConfig().isWholeContainer()
                    || !entry.visualState().shouldSuppressWorldRendering() || !entry.visualState().shouldWriteDepth()
                    || entry.blockState().getRenderType() != BlockRenderType.MODEL || entry.blockState().hasBlockEntity()
                    || !entry.blockState().getFluidState().isEmpty()
                    || RenderLayers.getBlockLayer(entry.blockState()) != BlockRenderLayer.SOLID) continue;
            long section = ChunkSectionPos.asLong(entry.pos().getX() >> 4, entry.pos().getY() >> 4, entry.pos().getZ() >> 4);
            groups.computeIfAbsent(entry.clip(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(section, ignored -> new ArrayList<>()).add(entry);
        }
        var handled = new HashSet<BlockPos>();
        int built = 0;
        long deadline = System.nanoTime() + 2_000_000L;
        var layer = RenderLayer.getSolid();
        for (var group : groups.entrySet()) {
            var clip = group.getKey();
            var snapshot = states.get(clip);
            if (snapshot == null || snapshot.size() > 1_000_000) continue;
            var assembly = assemblies.get(clip);
            if (assembly == null || !assembly.states.equals(snapshot) || !assembly.membership.matches(group.getValue())) {
                if (assembly != null) remove(clip);
                while (!assemblies.isEmpty() && (retainedPositions + snapshot.size() > 1_000_000 || assemblies.size() >= 64))
                    remove(assemblies.keySet().iterator().next());
                assembly = new Assembly(snapshot, ++version);
                assembly.sections.putAll(group.getValue());
                assembly.membership = new StaticMeshMembership(group.getValue());
                assemblies.put(clip, assembly); retainedPositions += snapshot.size();
            }
            for (var section : assembly.sections.entrySet()) {
                var mesh = assembly.meshes.get(section.getKey());
                if (mesh != null && mesh.indices() > 0 && gpu.get(mesh.key()) == null) mesh = null;
                if (mesh == null) {
                    if (built >= 2 || built > 0 && System.nanoTime() >= deadline) continue;
                    try {
                        mesh = build(clip, assembly, section.getKey(), section.getValue(), views.get(clip), layer);
                    } catch (RuntimeException failure) {
                        failed = true;
                        org.slf4j.LoggerFactory.getLogger("DirectorSectionMeshes").warn(
                                "Section mesh upload failed; using native rendering until invalidated", failure);
                        return handled;
                    }
                    built++; builds++;
                    if (mesh == null) continue;
                    assembly.meshes.put(section.getKey(), mesh);
                } else hits++;
                var origin = ChunkSectionPos.from(section.getKey()).getMinPos();
                var transform = sectionTransform(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(),
                        matrices.peek().getPositionMatrix(), origin);
                try {
                    if (mesh.indices() == 0 || gpu.draw(mesh.key(), layer, MinecraftClient.getInstance().getFramebuffer(), transform, mesh.indices()))
                        for (var entry : section.getValue()) handled.add(entry.pos());
                } catch (RuntimeException failure) {
                    failed = true;
                    org.slf4j.LoggerFactory.getLogger("DirectorSectionMeshes").warn(
                            "Section mesh draw failed; using native rendering until invalidated", failure);
                    return handled;
                }
            }
        }
        long now = System.nanoTime();
        if (now - lastLog > 5_000_000_000L) {
            org.slf4j.LoggerFactory.getLogger("DirectorSectionMeshes").info(
                    "[Perf][SectionMeshes] hits={} builds={} residentBytes={}", hits, builds, gpu.residentBytes());
            hits = builds = 0; lastLog = now;
        }
        return handled;
    }

    static org.joml.Matrix4f sectionTransform(org.joml.Matrix4f renderView,
            org.joml.Matrix4f worldMatrices, BlockPos origin) {
        return new org.joml.Matrix4f(renderView).mul(worldMatrices)
                .translate(origin.getX(), origin.getY(), origin.getZ());
    }

    private Mesh build(TimelineClip clip, Assembly assembly, long section, List<RenderEntry> entries,
            WholeContainerRenderView view, RenderLayer layer) {
        if (view == null) return null;
        var origin = ChunkSectionPos.from(section).getMinPos();
        try (var allocator = new BufferAllocator(262144)) {
            var builder = new BufferBuilder(allocator, layer.getDrawMode(), layer.getVertexFormat());
            var matrices = new MatrixStack();
            var manager = MinecraftClient.getInstance().getBlockRenderManager();
            for (var entry : entries) {
                var pos = entry.pos(); var state = entry.blockState();
                matrices.push(); matrices.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
                var parts = new ArrayList<net.minecraft.client.render.model.BlockModelPart>();
                manager.getModel(state).addParts(net.minecraft.util.math.random.Random.create(state.getRenderingSeed(pos)), parts);
                manager.renderBlock(state, pos, view, matrices, builder, true, parts);
                matrices.pop();
            }
            var buffer = builder.endNullable();
            var key = new SectionGpuBufferCache.Key(section, new Material(clip, layer), assembly.version, epoch);
            if (buffer == null) return new Mesh(key, 0);
            try (buffer) {
                if (gpu.upload(key, buffer.getBuffer()) == null) return null;
                return new Mesh(key, buffer.getDrawParameters().indexCount());
            }
        }
    }

    private void remove(TimelineClip clip) {
        var old = assemblies.remove(clip);
        if (old == null) return;
        retainedPositions -= old.states.size();
        for (long section : old.sections.keySet()) gpu.invalidate(section);
    }
}
