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
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityStateClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityTransformClip;
import com.director.timeline.playback.camera.CameraAccessor;
import com.director.timeline.playback.camera.CameraSample;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ActorTrackClipAuthoringService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorTrackClipAuthoringService.class);
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
    private static final String NAME_TRANSFORM = "gui.director.properties.adapter.timeline.track.actor_clip_transform";
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
    private EntityTimelineClip refuse(String key) {
        lastRefusal = key;
        return null;
    }
    private EntityTimelineClip createConfiguredClip(TimelineModel model,TimelineTrack timelineTrack,
            ActorTrackClipKind kind,ActorActionType actionType,Float duration,float intensity,boolean loop,java.util.function.Consumer<EntityClip> configure) {
        if (model == null) return refuse(REFUSE_NO_PROJECT);
        if (model.isPlaying() || model.isScrubbing()) return refuse(REFUSE_BUSY);
        if (timelineTrack == null || kind == null || timelineTrack.getType() != TrackType.ACTOR
                || !model.getAllTracks().contains(timelineTrack)) return refuse(REFUSE_NO_TRACK);
        if (timelineTrack.isLocked()) return refuse(REFUSE_LOCKED);
        if (timelineTrack.isMuted()) return refuse(REFUSE_MUTED);
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
            case TRANSFORM -> createTransformClip(actorTrack, start);
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
        if(clip instanceof ActorActionClip) for(var existing:actorTrack.getClips())
            if(existing instanceof ActorActionClip && !existing.isMuted() && existing.getEndTime()>clip.getStartTime() && existing.getStartTime()<clip.getEndTime()) return refuse(REFUSE_OVERLAP);
        EntityTimelineClip timelineClip = new EntityTimelineClip(clip, actorTrack.getColorRgba());
        com.director.ui.timeline.commands.TimelineCommandBridge.execute(
                new com.director.ui.timeline.commands.StateChangeCommand(model, "Create actor " + kind,
                        () -> {
                            actorTrack.removeClip(clip.getId());
                            timelineTrack.removeClip(timelineClip);
                            if (model.isSelected(timelineClip)) model.selectItem(timelineTrack, false);
                        },
                        () -> {
                            if (!actorTrack.getClips().contains(clip)) actorTrack.addClip(clip);
                            if (!timelineTrack.getClips().contains(timelineClip)) timelineTrack.addClip(timelineClip);
                        }));
        model.selectItem(timelineClip, false);

        lastRefusal = null;
        LOGGER.info("[ActorTrackClipAuthoringService] Created {} clip on actor track {}", kind, timelineTrack.getName());
        return timelineClip;
    }

    private EntityTransformClip createTransformClip(ActorTrack actorTrack, float start) {
        EntityTransformClip clip = new EntityTransformClip(clipName(NAME_TRANSFORM, "Transform"), start, start + DEFAULT_DURATION_SECONDS);
        TransformSeed seed = resolveTransformSeed(actorTrack, start);
        clip.setFromPos(seed.position.x, seed.position.y, seed.position.z);
        clip.setToPos(seed.position.x, seed.position.y, seed.position.z);
        clip.setFromRot(seed.yawDeg, seed.pitchDeg, seed.rollDeg);
        clip.setToRot(seed.yawDeg, seed.pitchDeg, seed.rollDeg);
        clip.setFromScale(seed.scale.x,seed.scale.y,seed.scale.z);
        clip.setToScale(seed.scale.x,seed.scale.y,seed.scale.z);
        return clip;
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

    private TransformSeed resolveTransformSeed(ActorTrack actorTrack, float timeSeconds) {
        var current=new com.director.timeline.playback.entity.EntityClipSampler(actorTrack).sampleSnapshot(timeSeconds);
        if(current.enabled) return new TransformSeed(new Vector3f(current.position),current.rotation.x,current.rotation.y,current.rotation.z,new Vector3f(current.scale));
        EntitySpawnClip spawn=findNearestSpawnClip(actorTrack,timeSeconds);
        if(spawn!=null) return new TransformSeed(new Vector3f(spawn.getPosition()),spawn.getYawDeg(),spawn.getPitchDeg(),spawn.getRollDeg(),new Vector3f(spawn.getScale()));

        CameraSample sample = sampleCamera();
        if (sample != null) {
            return new TransformSeed(
                    new Vector3f((float) sample.position().x, (float) sample.position().y, (float) sample.position().z),
                    sample.yaw(),
                    sample.pitch(),0,new Vector3f(1)
            );
        }

        return new TransformSeed(new Vector3f(), 0.0f, 0.0f,0,new Vector3f(1));
    }

    private EntitySpawnClip findNearestSpawnClip(ActorTrack actorTrack, float timeSeconds) {
        if (actorTrack == null) {
            return null;
        }
        EntitySpawnClip nearestBefore = null;
        EntitySpawnClip earliestAfter = null;
        for (EntityClip clip : actorTrack.getClips()) {
            if (!(clip instanceof EntitySpawnClip spawnClip)) {
                continue;
            }
            if (spawnClip.getStartTime() <= timeSeconds
                    && (nearestBefore == null || spawnClip.getStartTime() > nearestBefore.getStartTime())) {
                nearestBefore = spawnClip;
            }
            if (spawnClip.getStartTime() > timeSeconds
                    && (earliestAfter == null || spawnClip.getStartTime() < earliestAfter.getStartTime())) {
                earliestAfter = spawnClip;
            }
        }
        return nearestBefore != null ? nearestBefore : earliestAfter;
    }

    private CameraSample sampleCamera() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return null;
        }
        return CameraAccessor.sampleFromGameCamera(client);
    }

    private record TransformSeed(Vector3f position, float yawDeg, float pitchDeg, float rollDeg,Vector3f scale) {
    }
}
