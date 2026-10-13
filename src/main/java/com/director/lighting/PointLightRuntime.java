package com.director.lighting;

import com.director.mixin.accessor.LightWorldRendererAccessor;
import com.director.timeline.model.TimelineModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkRenderData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.List;

/** Live entity lighting and batched terrain lighting have separate immutable snapshots. */
public final class PointLightRuntime {
    private static final LightBuildSnapshots builds = new LightBuildSnapshots();
    private static ClientWorld world;
    private static final LightTerrainRefresh refresh = new LightTerrainRefresh(builds);
    private PointLightRuntime() {}
    public static LightBuildSnapshots buildSnapshots() { return builds; }
    public static int sample(int vanilla, double x, double y, double z) {
        return PointLightSampler.packed(builds.sampling(refresh.requested()), vanilla, x, y, z);
    }
    public static boolean hasLights() {
        return builds.sampling(refresh.requested()).stream().anyMatch(light -> light.enabled() && light.brightness() > 0);
    }
    public static void update(TimelineModel model) {
        var client = MinecraftClient.getInstance();
        if (client == null) return;
        if (world != client.world) {
            world = client.world;
            refresh.reset();
        }
        refresh.request(client.world == null || model == null ? List.of()
                : LightTimelineSampler.sample(model, model.getCurrentTime()));
        flush(client);
    }
    public static void clear() {
        refresh.request(List.of());
        var client = MinecraftClient.getInstance();
        if (client == null || client.world != world || client.world == null) {
            world = client == null ? null : client.world;
            refresh.reset();
        } else flush(client);
    }
    /** Called before export capture: newly dirtied terrain must be drawn at least once. */
    public static void onWorldRendered() {
        refresh.rendered();
        flush(MinecraftClient.getInstance());
    }
    public static boolean awaitTerrain() {
        var client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.worldRenderer == null) return false;
        flush(client);
        return refresh.hasTerrainWork() && refresh.awaiting(settled(client));
    }
    private static boolean settled(MinecraftClient client) {
        if (builds.activeJobs() != 0 || !client.worldRenderer.isTerrainRenderComplete()) return false;
        // Vanilla's completion predicate omits running workers and unscheduled dirty sections.
        for (var chunk : client.worldRenderer.getBuiltChunks()) {
            if (chunk.needsRebuild()
                    && (chunk.getCurrentRenderData() != ChunkRenderData.HIDDEN || chunk.shouldBuild())) return false;
        }
        return true;
    }
    private static void flush(MinecraftClient client) {
        if (client == null || world == null || client.world != world || client.worldRenderer == null
                || !refresh.pending()) return;
        var changed = refresh.publish(settled(client));
        if (changed == null || changed.isEmpty()) return;
        var storage = ((LightWorldRendererAccessor) client.worldRenderer).director$getLightChunkStorage();
        if (storage == null) return; // New storage builds with the published snapshot when created.
        // Iterate resident sections once, including sections outside the current camera frustum.
        // Work stays bounded by render distance even for very long linear emitters.
        for (var chunk : storage.chunks) {
            long pos = chunk.getSectionPos();
            int x = ChunkSectionPos.unpackX(pos), y = ChunkSectionPos.unpackY(pos), z = ChunkSectionPos.unpackZ(pos);
            for (var bounds : changed) {
                if (LightSectionBounds.intersects(bounds, x, y, z)) {
                    chunk.scheduleRebuild(false);
                    refresh.requireRender();
                    break;
                }
            }
        }
    }
}
