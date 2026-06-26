package com.director.ui.actor.edit;

import com.director.actor.ActorRef;
import com.director.actor.preview.ActorPreviewManager;
import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.EntityTransformClip;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.timeline.playback.entity.EntitySample;
import net.minecraft.entity.Entity;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * 演员变换/朝向属性编辑：在播放头时刻采样状态，并写入 Transform / LookAt 片段。
 * <p>与 {@link com.director.ui.actor.gizmo.ActorGizmoController} 使用相同的片段查找与创建规则。
 */
public final class ActorTransformEditService {

    private static final float DEFAULT_CLIP_DURATION = 1.0f;
    private static final double MIN_SCALE = 0.01;
    private static final double MAX_SCALE = 100.0;

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
        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ACTOR) {
                continue;
            }
            ActorTrack actorTrack = track.getActorTrackPayload();
            if (actorTrack == null) {
                continue;
            }
            ActorRef ref = actorTrack.getActorRef();
            if (ref != null && Objects.equals(ref.getActorId(), actorId)) {
                return new ActorTrackContext(actorTrack, track);
            }
        }
        return null;
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
        ActorTrackContext ctx = resolveTrack(actorId);
        if (ctx == null) {
            return;
        }
        EntityTransformClip clip = ensureTransformClip(ctx, time, sampleTransform(actorId, time));
        clip.setToPos(position.x, position.y, position.z);
        clip.setToRot(rotation.x, rotation.y, rotation.z);
        clip.setToScale(
                (float) clampScale(scale.x),
                (float) clampScale(scale.y),
                (float) clampScale(scale.z));
        if (time <= clip.getStartTime() + 1e-4f) {
            clip.setFromPos(position.x, position.y, position.z);
            clip.setFromRot(rotation.x, rotation.y, rotation.z);
            clip.setFromScale(clip.getToScale().x, clip.getToScale().y, clip.getToScale().z);
        }
        markDirty();
    }

    public static void applyLookAtWorldPoint(UUID actorId, float time, float x, float y, float z, float strength) {
        ActorTrackContext ctx = resolveTrack(actorId);
        if (ctx == null) {
            return;
        }
        EntityLookAtClip clip = ensureLookAtClip(ctx, time);
        clip.setMode(EntityLookAtClip.TargetMode.WORLD_POINT);
        clip.setWorldPoint(x, y, z);
        clip.setStrength(strength);
        markDirty();
    }

    public static void applyLookAtCamera(UUID actorId, float time, float strength) {
        ActorTrackContext ctx = resolveTrack(actorId);
        if (ctx == null) {
            return;
        }
        EntityLookAtClip clip = ensureLookAtClip(ctx, time);
        clip.setMode(EntityLookAtClip.TargetMode.CAMERA);
        clip.setStrength(strength);
        markDirty();
    }

    private static EntityTransformClip ensureTransformClip(
            ActorTrackContext ctx, float time, TransformSnapshot fallback) {
        EntityTransformClip clip = findActiveTransformClip(ctx.actorTrack(), time);
        if (clip == null) {
            clip = findNearestTransformClipForExtension(ctx.actorTrack(), time);
            if (clip != null) {
                float newStart = Math.min(clip.getStartTime(), time);
                float newEnd = Math.max(clip.getEndTime(), time + DEFAULT_CLIP_DURATION);
                clip.setTimeRange(newStart, newEnd);
            } else {
                clip = createTransformClip(ctx, time, fallback.position());
            }
        } else if (time > clip.getEndTime()) {
            clip.setTimeRange(clip.getStartTime(), time + DEFAULT_CLIP_DURATION);
        }
        return clip;
    }

    private static EntityLookAtClip ensureLookAtClip(ActorTrackContext ctx, float time) {
        EntityLookAtClip clip = findActiveLookAtClip(ctx.actorTrack(), time);
        if (clip == null) {
            clip = createLookAtClip(ctx, time);
        }
        return clip;
    }

    private static EntityTransformClip createTransformClip(ActorTrackContext ctx, float time, Vector3f pos) {
        EntityTransformClip clip = new EntityTransformClip(
                "Transform",
                time,
                time + DEFAULT_CLIP_DURATION);
        clip.setInterpolation(TimelineInterpolation.SMOOTH);
        clip.setFromPos(pos.x, pos.y, pos.z);
        clip.setToPos(pos.x, pos.y, pos.z);
        clip.setFromRot(0, 0, 0);
        clip.setToRot(0, 0, 0);
        clip.setFromScale(1, 1, 1);
        clip.setToScale(1, 1, 1);
        ctx.actorTrack().addClip(clip);
        if (ctx.timelineTrack() != null) {
            ctx.timelineTrack().addClip(new EntityTimelineClip(clip, ctx.actorTrack().getColorRgba()));
        }
        return clip;
    }

    private static EntityLookAtClip createLookAtClip(ActorTrackContext ctx, float time) {
        EntityLookAtClip clip = new EntityLookAtClip("LookAt", time, time + DEFAULT_CLIP_DURATION);
        clip.setMode(EntityLookAtClip.TargetMode.CAMERA);
        clip.setStrength(1f);
        ctx.actorTrack().addClip(clip);
        if (ctx.timelineTrack() != null) {
            ctx.timelineTrack().addClip(new EntityTimelineClip(clip, ctx.actorTrack().getColorRgba()));
        }
        return clip;
    }

    private static EntityTransformClip findActiveTransformClip(ActorTrack track, float time) {
        EntityTransformClip result = null;
        for (EntityClip c : track.getClips()) {
            if (c instanceof EntityTransformClip tc && !tc.isMuted()) {
                if (time >= tc.getStartTime() && time <= tc.getEndTime()) {
                    if (result == null || tc.getStartTime() >= result.getStartTime()) {
                        result = tc;
                    }
                }
            }
        }
        return result;
    }

    private static EntityTransformClip findNearestTransformClipForExtension(ActorTrack track, float time) {
        EntityTransformClip nearest = null;
        float minDistance = Float.MAX_VALUE;
        for (EntityClip c : track.getClips()) {
            if (c instanceof EntityTransformClip tc && !tc.isMuted()) {
                float distance;
                if (time < tc.getStartTime()) {
                    distance = tc.getStartTime() - time;
                } else if (time > tc.getEndTime()) {
                    distance = time - tc.getEndTime();
                } else {
                    continue;
                }
                if (distance < DEFAULT_CLIP_DURATION && distance < minDistance) {
                    minDistance = distance;
                    nearest = tc;
                }
            }
        }
        return nearest;
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

    private static double clampScale(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    private static void markDirty() {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model != null) {
            model.markDirty();
        }
    }
}
