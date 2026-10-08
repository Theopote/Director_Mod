package com.director.actor.runtime;

import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.actor.ActorRegistryManager;
import com.director.actor.preview.ActorPreviewManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shared gaze target policy. Missing dynamic targets never fall back to saved world coordinates. */
public final class ActorLookAtTargetResolver {
    private ActorLookAtTargetResolver() {}
    public static Vec3d resolve(String mode, Vector3f point, UUID target, UUID source,
                               Supplier<Vec3d> camera, Function<UUID, Vec3d> entities) {
        Vec3d result;
        if ("WORLD_POINT".equals(mode)) result = point == null ? null : new Vec3d(point.x, point.y, point.z);
        else if ("CAMERA".equals(mode)) result = camera.get();
        else if ("ENTITY_UUID".equals(mode)) result = target == null || target.equals(source) ? null : entities.apply(target);
        else result = null;
        return result != null && Double.isFinite(result.x) && Double.isFinite(result.y) && Double.isFinite(result.z) ? result : null;
    }
    public static Vec3d resolveClient(String mode, Vector3f point, UUID target, UUID source) {
        return resolve(mode, point, target, source, () -> {
            var client = MinecraftClient.getInstance();
            var runtime = com.director.timeline.playback.camera.CameraRuntime.getInstance();
            if (runtime.isTimelineOwner()) return runtime.getCurrentSample().position();
            return client == null || client.gameRenderer == null ? null : client.gameRenderer.getCamera().getPos();
        }, id -> {
            var previews = ActorPreviewManager.getInstance();
            var preview = previews.getPreview(id);
            if (preview != null) return preview.isInvisible() || preview.isRemoved() ? null : preview.getEyePos();
            // A project actor may temporarily have no visible preview; do not reinterpret its ID.
            if (ActorRegistryManager.getInstance().contains(id)) return null;
            var client = MinecraftClient.getInstance();
            if (client == null || client.world == null) return null;
            for (var entity : client.world.getEntities()) {
                if (id.equals(entity.getUuid())) {
                    if (source != null && source.equals(previews.getActorId(entity))) return null;
                    return entity.isRemoved() || entity.isInvisible() ? null : entity.getEyePos();
                }
            }
            return null;
        });
    }
}
