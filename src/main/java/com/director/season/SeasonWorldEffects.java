package com.director.season;

import com.director.timeline.playback.camera.CameraRuntime;
import com.director.timeline.playback.season.TimelineSeasonController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Shared render-time season progress and capture barrier. All methods run on the client thread. */
public final class SeasonWorldEffects {
    private static int settleFrames;
    private SeasonWorldEffects() {}
    public static Vec3d cameraPosition(MinecraftClient client) {
        var runtime = CameraRuntime.getInstance();
        if (runtime.isTimelineOwner()) return runtime.getCurrentSample().position();
        return client.gameRenderer.getCamera().getPos();
    }
    public static int loadedChunkStamp(ClientWorld world, BlockPos pos, int radius) {
        int hash = 1, cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int x=cx-radius;x<=cx+radius;x++) for (int z=cz-radius;z<=cz+radius;z++)
            hash=31*hash+(world.getChunkManager().isChunkLoaded(x,z)?1:0);
        return hash;
    }
    public static void terrainChanged() { settleFrames=2; }
    public static void tick() {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client==null || client.world==null || client.gameRenderer==null) return;
        Vec3d pos=cameraPosition(client);
        var snow=SeasonSnowManager.get(); var ice=WaterFreezeManager.get();
        boolean work=snow.hasPendingWork() || ice.hasPendingWork();
        snow.tick(client.world,pos); ice.tick(client.world,pos);
        TimelineSeasonController.getInstance().flushRerender(false);
        if(work || snow.hasPendingWork() || ice.hasPendingWork()) terrainChanged();
        else if(settleFrames>0 && settled(client)) settleFrames--;
    }
    public static boolean awaitTerrain() {
        TimelineSeasonController.getInstance().flushRerender(true);
        MinecraftClient client=MinecraftClient.getInstance();
        if(client==null || client.world==null) return false;
        return SeasonSnowManager.get().hasPendingWork() || WaterFreezeManager.get().hasPendingWork()
                || settleFrames>0 || !settled(client);
    }
    private static boolean settled(MinecraftClient client) {
        if(com.director.lighting.PointLightRuntime.buildSnapshots().activeJobs()!=0 || !client.worldRenderer.isTerrainRenderComplete()) return false;
        for(var chunk:client.worldRenderer.getBuiltChunks())
            if(chunk.needsRebuild() && (chunk.getCurrentRenderData()!=net.minecraft.client.render.chunk.ChunkRenderData.HIDDEN || chunk.shouldBuild())) return false;
        return true;
    }
    public static void clear() {
        TimelineSeasonController.getInstance().clear();
        SeasonSnowManager.get().shutdown(); WaterFreezeManager.get().clearAll(); settleFrames=0;
    }
}
