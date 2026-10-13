package com.director.lighting;

import com.director.timeline.model.TimelineModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import java.util.List;

/** Publishes scene lights before rendering; chunk workers only read immutable data. */
public final class PointLightRuntime {
    private static volatile List<PointLight> snapshot = List.of();
    private static ClientWorld world;
    private static boolean terrainChanged;
    public static boolean awaitTerrain() {
        var client=MinecraftClient.getInstance();
        if(!terrainChanged || client==null || client.worldRenderer==null) return false;
        if(client.worldRenderer.isTerrainRenderComplete()) {terrainChanged=false;return false;}
        return true;
    }
    private PointLightRuntime() {}
    public static int sample(int vanilla, double x, double y, double z) {
        return PointLightSampler.packed(snapshot,vanilla,x,y,z);
    }
    public static boolean hasLights() { return snapshot.stream().anyMatch(light -> light.enabled() && light.brightness()>0); }
    public static void update(TimelineModel model) {
        MinecraftClient client=MinecraftClient.getInstance();
        if (client == null) return;
        List<PointLight> next=client.world == null || model == null ? List.of() : LightTimelineSampler.sample(model,model.getCurrentTime());
        List<PointLight> previous=snapshot;
        boolean worldChanged=world != client.world;
        if (!worldChanged && previous.equals(next)) return;
        world=client.world;
        snapshot=next;
        if (client.world == null || client.worldRenderer == null) return;
        // Removed/moved lights must also invalidate their former influence volume.
        invalidate(client,worldChanged?List.of():previous,next);
    }
    public static void clear() {
        MinecraftClient client=MinecraftClient.getInstance();
        List<PointLight> previous=snapshot;
        snapshot=List.of();
        if (client != null && client.world != null && client.world == world && client.worldRenderer != null)
            invalidate(client,previous,List.of());
        world=null;
    }
    private static void invalidate(MinecraftClient client,List<PointLight> before,List<PointLight> after) {
        var changed=PointLightChanges.affected(before,after);
        if(!changed.isEmpty()) terrainChanged=true;
        for(var b:changed)
            client.worldRenderer.scheduleBlockRenders(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
    }
}
