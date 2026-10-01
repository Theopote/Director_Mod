package com.director.ui.actor.edit;

import com.director.actor.preview.ActorPreviewManager;
import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.timeline.playback.entity.EntitySample;
import net.minecraft.entity.Entity;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 演员变换/朝向属性编辑：在播放头时刻采样状态，并写入 Transform / LookAt 片段。
 * <p>与 {@link com.director.ui.actor.gizmo.ActorGizmoController} 使用相同的片段查找与创建规则。
 */
public final class ActorTransformEditService {

    private ActorTransformEditService() {
    }

    public record ActorTrackContext(ActorTrack actorTrack, TimelineTrack timelineTrack) {
    }

    public record TransformSnapshot(Vector3f position, Vector3f rotation, Vector3f scale) {
    }

    public record LookAtSnapshot(
            boolean active,
            EntityLookAtClip.TargetMode mode,
            Vector3f worldPoint,
            float strength
    ) {
    }

    public static ActorTrackContext resolveTrack(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return null;
        }
        try {
            var track = com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model, actorId);
            return new ActorTrackContext(track.getActorTrackPayload(), track);
        } catch (IllegalArgumentException unavailable) { return null; }
    }

    public static TransformSnapshot sampleTransform(UUID actorId, float time) {
        ActorTrackContext ctx = resolveTrack(actorId);
        if (ctx != null) {
            EntitySample sample = new EntityClipSampler(ctx.actorTrack()).sampleAt(time);
            if (sample != null && sample.enabled) {
                return new TransformSnapshot(
                        new Vector3f(sample.position),
                        new Vector3f(sample.rotation),
                        new Vector3f(sample.scale));
            }
        }
        Entity preview = ActorPreviewManager.getInstance().getPreview(actorId);
        if (preview != null) {
            return new TransformSnapshot(
                    new Vector3f((float) preview.getX(), (float) preview.getY(), (float) preview.getZ()),
                    new Vector3f(preview.getYaw(), preview.getPitch(), 0f),
                    new Vector3f(1f, 1f, 1f));
        }
        return new TransformSnapshot(new Vector3f(), new Vector3f(), new Vector3f(1f, 1f, 1f));
    }

    public static LookAtSnapshot sampleLookAt(UUID actorId, float time) {
        ActorTrackContext ctx = resolveTrack(actorId);
        if (ctx != null) {
            EntitySample sample = new EntityClipSampler(ctx.actorTrack()).sampleAt(time);
            if (sample != null && sample.enabled && sample.lookAtEnabled) {
                EntityLookAtClip.TargetMode mode = parseLookAtMode(sample.lookAtMode);
                return new LookAtSnapshot(
                        true,
                        mode,
                        new Vector3f(sample.lookAtPoint),
                        sample.lookAtStrength);
            }
            EntityLookAtClip clip = findActiveLookAtClip(ctx.actorTrack(), time);
            if (clip != null) {
                return new LookAtSnapshot(
                        true,
                        clip.getMode(),
                        new Vector3f(clip.getWorldPoint()),
                        clip.getStrength());
            }
        }
        return new LookAtSnapshot(false, EntityLookAtClip.TargetMode.CAMERA, new Vector3f(), 1f);
    }

    public static void applyTransform(UUID actorId, float time, Vector3f position, Vector3f rotation, Vector3f scale) {
        var model = editableModel(time);
        var ctx = resolveTrack(actorId);
        if (model == null || ctx == null || position == null || rotation == null || scale == null
                || !position.isFinite() || !rotation.isFinite() || !scale.isFinite()) return;
        try {
            var session = new ActorNumericEditSession(model, ctx.timelineTrack(), ActorNumericEditSession.Kind.TRANSFORM,
                    sampleTransform(actorId, time));
            session.updateTransform(position, rotation, scale);
            session.commit();
        } catch (IllegalArgumentException unavailable) { /* Read-only or stale target. */ }
    }

    public static void applyLookAtWorldPoint(UUID actorId, float time, float x, float y, float z, float strength) {
        applyLookAt(actorId, time, new Vector3f(x, y, z), strength);
    }

    public static void applyLookAtCamera(UUID actorId, float time, float strength) {
        applyLookAt(actorId, time, null, strength);
    }

    private static void applyLookAt(UUID actorId, float time, Vector3f point, float strength) {
        var model = editableModel(time);
        var ctx = resolveTrack(actorId);
        if (model == null || ctx == null || !Float.isFinite(strength) || point != null && !point.isFinite()) return;
        try {
            var session = new com.director.ui.actor.lookat.ActorLookAtEditSession(model, ctx.timelineTrack());
            var clip = session.draft();
            clip.setMode(point == null ? EntityLookAtClip.TargetMode.CAMERA : EntityLookAtClip.TargetMode.WORLD_POINT);
            if (point != null) clip.setWorldPoint(point.x, point.y, point.z);
            clip.setStrength(strength);
            session.commit();
        } catch (IllegalArgumentException unavailable) { /* Read-only or stale target. */ }
    }

    private static TimelineModel editableModel(float time) {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        return model != null && Float.isFinite(time) && Float.compare(time, model.getCurrentTime()) == 0
                && !model.isPlaying() && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                && !com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging() ? model : null;
    }

    private static EntityLookAtClip findActiveLookAtClip(ActorTrack track, float time) {
        EntityLookAtClip result = null;
        for (EntityClip c : track.getClips()) {
            if (c instanceof EntityLookAtClip lc && !lc.isMuted()) {
                if (time >= lc.getStartTime() && time <= lc.getEndTime()) {
                    if (result == null || lc.getStartTime() >= result.getStartTime()) {
                        result = lc;
                    }
                }
            }
        }
        return result;
    }

    private static EntityLookAtClip.TargetMode parseLookAtMode(String mode) {
        if (mode == null) {
            return EntityLookAtClip.TargetMode.CAMERA;
        }
        try {
            return EntityLookAtClip.TargetMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return EntityLookAtClip.TargetMode.CAMERA;
        }
    }



}
