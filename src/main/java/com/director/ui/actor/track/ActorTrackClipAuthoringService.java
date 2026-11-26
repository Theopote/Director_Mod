package com.director.ui.actor.track;

import com.director.core.DirectorServices;
import com.director.actor.action.ActorActionType;
import com.director.i18n.DirectorLang;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.ActorPoseClip;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.EntityStateClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.ActorTrack;

public final class ActorTrackClipAuthoringService {
    private static final ActorTrackClipAuthoringService INSTANCE = new ActorTrackClipAuthoringService();

    private static final float DEFAULT_DURATION_SECONDS = 2.0f;
    private static final float DEFAULT_STATE_DURATION_SECONDS = 3.0f;
    private static final String REFUSE_NO_PROJECT = "gui.director.actor.track.create_no_project";
    private static final String REFUSE_BUSY = "gui.director.actor.track.create_busy";
    private static final String REFUSE_NO_TRACK = "gui.director.actor.track.create_no_track";
    private static final String REFUSE_LOCKED = "gui.director.actor.track.create_locked";
    private static final String REFUSE_MUTED = "gui.director.actor.track.create_muted";
    private static final String REFUSE_UNSUPPORTED_ACTION = "gui.director.actor.track.create_unsupported_action";
    private static final String REFUSE_POSE = "gui.director.actor.track.create_pose_unsupported";
    private static final String REFUSE_BAD_TIME = "gui.director.actor.track.create_bad_time";
    private static final String REFUSE_OVERLAP = "gui.director.actor.track.create_action_overlap";
    private static final String REFUSE_REJECTED = "gui.director.actor.track.create_rejected";
    private static final String REFUSE_BAD_VALUES = "gui.director.actor.track.create_bad_values";
    private static final String NAME_LOOK_AT = "gui.director.properties.adapter.timeline.track.actor_clip_look_at";
    private static final String NAME_POSE = "gui.director.properties.adapter.timeline.track.actor_clip_pose";
    private static final String NAME_STATE = "gui.director.properties.adapter.timeline.track.actor_clip_state";

    private String lastRefusal;

    private ActorTrackClipAuthoringService() {
    }

    /** Translation key for the latest refusal, or an empty string after a successful create. */
    public String lastRefusal() {
        return lastRefusal == null ? "" : lastRefusal;
    }

    public static ActorTrackClipAuthoringService getInstance() {
        return INSTANCE;
    }

    public EntityTimelineClip createClip(TimelineTrack timelineTrack, ActorTrackClipKind kind, ActorActionType actionType) {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        return createClip(model, timelineTrack, kind, actionType);
    }

    /** All menu/property entry points use the same validation and one undo transaction. */
    public EntityTimelineClip createClip(TimelineModel model, TimelineTrack timelineTrack,
                                         ActorTrackClipKind kind, ActorActionType actionType) {
        return createConfiguredClip(model,timelineTrack,kind,actionType,null,1,true,null);
    }
    public EntityTimelineClip createAction(TimelineModel model,TimelineTrack track,ActorActionType action,float duration,float intensity,boolean loop) {
        if(!Float.isFinite(duration)||duration<.1f||!Float.isFinite(intensity)||intensity<0||intensity>1) return refuse(REFUSE_BAD_VALUES);
        return createConfiguredClip(model,track,ActorTrackClipKind.ACTION,action,duration,intensity,loop,null);
    }
    public EntityTimelineClip createPose(TimelineModel model,TimelineTrack track,float duration,java.util.function.Consumer<ActorPoseClip> configure) {
        if(!Float.isFinite(duration)||duration<.1f) return refuse(REFUSE_BAD_VALUES);
        return createConfiguredClip(model,track,ActorTrackClipKind.POSE,null,duration,1,false,c->{configure.accept((ActorPoseClip)c);c.setTimeRange(c.getStartTime(),c.getStartTime()+duration);});
    }
    /**
     * 当前能否在这条轨道上创建片段。返回 null 表示可以；否则返回说明原因的翻译键。
     * 界面用它在按钮被禁用时直接显示原因，而不必等用户点击失败后才看到。
     */
    public String creationBlockReason(TimelineModel model, TimelineTrack timelineTrack) {
        if (model == null) return REFUSE_NO_PROJECT;
        if (model.isPlaying() || model.isScrubbing()) return REFUSE_BUSY;
        if (timelineTrack == null || timelineTrack.getType() != TrackType.ACTOR
                || !model.getAllTracks().contains(timelineTrack)) return REFUSE_NO_TRACK;
        if (timelineTrack.isLocked()) return REFUSE_LOCKED;
        if (timelineTrack.isMuted()) return REFUSE_MUTED;
        return null;
    }

    private EntityTimelineClip refuse(String key) {
        lastRefusal = key;
        return null;
    }
    private EntityTimelineClip createConfiguredClip(TimelineModel model,TimelineTrack timelineTrack,
            ActorTrackClipKind kind,ActorActionType actionType,Float duration,float intensity,boolean loop,java.util.function.Consumer<EntityClip> configure) {
        String blocked = creationBlockReason(model, timelineTrack);
        if (blocked != null) return refuse(blocked);
        if (kind == null) return refuse(REFUSE_NO_TRACK);
        ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
        if (actorTrack == null || actorTrack.getActorRef() == null) return refuse("gui.director.actor.track.unbound");
        var actor = com.director.actor.ActorRegistryManager.getInstance()
                .getActor(actorTrack.getActorRef().getActorId()).orElse(null);
        if (actor == null) return refuse("gui.director.actor.track.missing_actor");
        ActorActionType action = actionType == null ? ActorActionType.IDLE : actionType;
        if (kind == ActorTrackClipKind.ACTION
                && !com.director.actor.ActorCapabilities.of(actor).supports(action)) return refuse(REFUSE_UNSUPPORTED_ACTION);
        if (kind == ActorTrackClipKind.POSE
                && !com.director.actor.ActorCapabilities.of(actor).humanoidPose()) return refuse(REFUSE_POSE);
        if (!Float.isFinite(model.getCurrentTime())) return refuse(REFUSE_BAD_TIME);
        float start = Math.max(0.0f, model.getCurrentTime());
        EntityClip clip = switch (kind) {
            case LOOK_AT_CAMERA -> createLookAtCameraClip(start);
            case ACTION -> createActionClip(start, actionType);
            case POSE -> createPoseClip(start);
            case STATE -> createStateClip(start);
        };

        if(configure!=null) configure.accept(clip);
        if(!Float.isFinite(clip.getEndTime())) return refuse(REFUSE_REJECTED);
        if(clip instanceof ActorActionClip configured && duration!=null) {
            configured.setTimeRange(start,start+duration);configured.setIntensity(intensity);configured.setLoop(loop);
            if(!Float.isFinite(configured.getEndTime())) return refuse(REFUSE_BAD_VALUES);
        }
        var spawn = actorTrack.findSpawnClipAt(start);
        if (spawn == null || !actorTrack.isPresentAt(start) || spawn.getEndTime() <= start
                || spawn.getTimelineClip() == null || !timelineTrack.getClips().contains(spawn.getTimelineClip()))
            return refuse("gui.director.actor.track.behavior_requires_spawn");
        float end = Math.min(clip.getEndTime(), spawn.getEndTime());
        if (clip instanceof ActorActionClip && (end - start < .1f - 1e-5f
                || actorTrack.hasActionOverlap(spawn, null, start, end)))
            return refuse(end - start < .1f - 1e-5f ? REFUSE_BAD_TIME : REFUSE_OVERLAP);
        clip.setTimeRange(start, end);
        Runnable add;
        Runnable remove;
        switch (clip) {
            case ActorActionClip actionClip -> {
                add = () -> spawn.addActionInterval(actionClip);
                remove = () -> spawn.removeActionInterval(actionClip);
            }
            case ActorPoseClip pose -> {
                add = () -> spawn.addPoseInterval(pose);
                remove = () -> spawn.removePoseInterval(pose);
            }
            case EntityStateClip state -> {
                add = () -> spawn.addStateInterval(state);
                remove = () -> spawn.removeStateInterval(state);
            }
            case EntityLookAtClip look -> {
                add = () -> spawn.addLookTarget(look);
                remove = () -> spawn.removeLookTarget(look);
            }
            default -> throw new IllegalStateException("Unsupported actor interval " + kind);
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Create actor " + kind + " interval", remove, add));
        model.selectItem(spawn.getTimelineClip(), false);
        lastRefusal = null;
        return spawn.getTimelineClip();
    }

    public EntityTimelineClip selectTransformTarget(TimelineModel model, TimelineTrack track) {
        String blocked = creationBlockReason(model, track);
        if (blocked != null) return refuse(blocked);
        var wrapper = transformTarget(model, track);
        if (wrapper == null) return refuse("gui.director.actor.transform_edit.require_spawn");
        model.selectItem(wrapper, false);
        lastRefusal = null;
        return wrapper;
    }

    public boolean canOpenTransformInspector(TimelineModel model, TimelineTrack track) {
        return creationBlockReason(model, track) == null && transformTarget(model, track) != null;
    }

    private static EntityTimelineClip transformTarget(TimelineModel model, TimelineTrack track) {
        var payload = track.getActorTrackPayload();
        float time = model.getCurrentTime();
        if (payload == null || !payload.isPresentAt(time)) return null;
        var spawn = payload.findSpawnClipAt(time);
        var wrapper = spawn.getTimelineClip();
        return wrapper != null && track.getClips().contains(wrapper) ? wrapper : null;
    }

    public boolean openTransformInspector(TimelineModel model, TimelineTrack track) {
        var wrapper = selectTransformTarget(model, track);
        if (wrapper == null) return false;
        DirectorServices.get().uiSelection().selectClip(wrapper.getEntityClip());
        com.director.ui.properties.PropertyContextService.get().onTimelineSelectionChanged(wrapper);
        return true;
    }

    private EntityLookAtClip createLookAtCameraClip(float start) {
        EntityLookAtClip clip = new EntityLookAtClip(clipName(NAME_LOOK_AT, "Look At Camera"), start, start + DEFAULT_DURATION_SECONDS);
        clip.setMode(EntityLookAtClip.TargetMode.CAMERA);
        clip.setStrength(1.0f);
        return clip;
    }

    private ActorActionClip createActionClip(float start, ActorActionType actionType) {
        ActorActionType safeAction = actionType != null ? actionType : ActorActionType.IDLE;
        ActorActionClip clip = new ActorActionClip(actionClipName(safeAction), start, start + DEFAULT_DURATION_SECONDS);
        clip.setActionType(safeAction);
        clip.setIntensity(1.0f);
        clip.setLoop(true);
        return clip;
    }

    private ActorPoseClip createPoseClip(float start) {
        ActorPoseClip clip = new ActorPoseClip(clipName(NAME_POSE, "Pose"), start, start + DEFAULT_DURATION_SECONDS);
        clip.setOverrideHead(true);
        clip.setOverrideBody(false);
        clip.setOverrideArms(false);
        clip.setOverrideLegs(false);
        return clip;
    }

    private EntityStateClip createStateClip(float start) {
        return new EntityStateClip(clipName(NAME_STATE, "State"), start, start + DEFAULT_STATE_DURATION_SECONDS);
    }

    /** English fallback keeps unit tests independent of Minecraft language loading. */
    private static String clipName(String key, String fallback) {
        try {
            String value = net.minecraft.util.Language.getInstance().get(key, fallback);
            if (value == null || value.isBlank() || value.equals(key)) return fallback;
            return value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String actionClipName(ActorActionType action) {
        String key = switch (action) {
            case IDLE -> DirectorLang.Properties.AdapterActor.Semantic.IDLE;
            case WALK -> DirectorLang.Properties.AdapterActor.Semantic.WALK;
            case RUN -> DirectorLang.Properties.AdapterActor.Semantic.RUN;
            case DANCE -> DirectorLang.Properties.AdapterActor.Semantic.DANCE;
            case CELEBRATE -> DirectorLang.Properties.AdapterActor.Semantic.CELEBRATE;
            case SIT -> DirectorLang.Properties.AdapterActor.Semantic.SIT;
            case MEDITATE -> DirectorLang.Properties.AdapterActor.Semantic.MEDITATE;
            case CARRY -> DirectorLang.Properties.AdapterActor.Semantic.CARRY;
            case THROW_PREPARE -> DirectorLang.Properties.AdapterActor.Semantic.THROW_PREPARE;
            case ATTACK -> DirectorLang.Properties.AdapterActor.Semantic.ATTACK;
            case GRAZE -> DirectorLang.Properties.AdapterActor.Semantic.GRAZE;
            case ROAR -> DirectorLang.Properties.AdapterActor.Semantic.ROAR;
            case CUSTOM -> DirectorLang.Properties.AdapterActor.Semantic.CUSTOM;
        };
        return clipName(key, action.name());
    }

}
