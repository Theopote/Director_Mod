package com.director.ui.actor.track;

import com.director.core.DirectorServices;
import com.director.actor.action.ActorActionType;
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

    private ActorTrackClipAuthoringService() {
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
        if (model == null || model.isPlaying() || timelineTrack == null || timelineTrack.isLocked()
                || !model.getAllTracks().contains(timelineTrack)
                || timelineTrack.getType() != TrackType.ACTOR || kind == null) {
            return null;
        }
        ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
        if (actorTrack == null || actorTrack.getActorRef() == null) return null;
        var actor = com.director.actor.ActorRegistryManager.getInstance()
                .getActor(actorTrack.getActorRef().getActorId()).orElse(null);
        if (actor == null) return null;
        ActorActionType action = actionType == null ? ActorActionType.IDLE : actionType;
        if (kind == ActorTrackClipKind.ACTION
                && !com.director.actor.ActorCapabilities.of(actor).supports(action)) return null;
        if (kind == ActorTrackClipKind.POSE
                && !com.director.actor.ActorCapabilities.of(actor).humanoidPose()) return null;
        if (!Float.isFinite(model.getCurrentTime())) return null;
        float start = Math.max(0.0f, model.getCurrentTime());
        EntityClip clip = switch (kind) {
            case TRANSFORM -> createTransformClip(actorTrack, start);
            case LOOK_AT_CAMERA -> createLookAtCameraClip(start);
            case ACTION -> createActionClip(start, actionType);
            case POSE -> createPoseClip(start);
            case STATE -> createStateClip(start);
        };

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

        LOGGER.info("[ActorTrackClipAuthoringService] Created {} clip on actor track {}", kind, timelineTrack.getName());
        return timelineClip;
    }

    private EntityTransformClip createTransformClip(ActorTrack actorTrack, float start) {
        EntityTransformClip clip = new EntityTransformClip("Transform", start, start + DEFAULT_DURATION_SECONDS);
        TransformSeed seed = resolveTransformSeed(actorTrack, start);
        clip.setFromPos(seed.position.x, seed.position.y, seed.position.z);
        clip.setToPos(seed.position.x, seed.position.y, seed.position.z);
        clip.setFromRot(seed.yawDeg, seed.pitchDeg, 0.0f);
        clip.setToRot(seed.yawDeg, seed.pitchDeg, 0.0f);
        clip.setFromScale(1.0f, 1.0f, 1.0f);
        clip.setToScale(1.0f, 1.0f, 1.0f);
        return clip;
    }

    private EntityLookAtClip createLookAtCameraClip(float start) {
        EntityLookAtClip clip = new EntityLookAtClip("Look At Camera", start, start + DEFAULT_DURATION_SECONDS);
        clip.setMode(EntityLookAtClip.TargetMode.CAMERA);
        clip.setStrength(1.0f);
        return clip;
    }

    private ActorActionClip createActionClip(float start, ActorActionType actionType) {
        ActorActionType safeAction = actionType != null ? actionType : ActorActionType.IDLE;
        ActorActionClip clip = new ActorActionClip(safeAction.name(), start, start + DEFAULT_DURATION_SECONDS);
        clip.setActionType(safeAction);
        clip.setIntensity(1.0f);
        clip.setLoop(true);
        return clip;
    }

    private ActorPoseClip createPoseClip(float start) {
        ActorPoseClip clip = new ActorPoseClip("Pose", start, start + DEFAULT_DURATION_SECONDS);
        clip.setOverrideHead(true);
        clip.setOverrideBody(false);
        clip.setOverrideArms(false);
        clip.setOverrideLegs(false);
        return clip;
    }

    private EntityStateClip createStateClip(float start) {
        EntityStateClip clip = new EntityStateClip("State", start, start + DEFAULT_STATE_DURATION_SECONDS);
        clip.setVisibility(1.0f);
        return clip;
    }

    private TransformSeed resolveTransformSeed(ActorTrack actorTrack, float timeSeconds) {
        EntitySpawnClip spawn = findNearestSpawnClip(actorTrack, timeSeconds);
        if (spawn != null) {
            return new TransformSeed(new Vector3f(spawn.getPosition()), spawn.getYawDeg(), spawn.getPitchDeg());
        }

        CameraSample sample = sampleCamera();
        if (sample != null) {
            return new TransformSeed(
                    new Vector3f((float) sample.position().x, (float) sample.position().y, (float) sample.position().z),
                    sample.yaw(),
                    sample.pitch()
            );
        }

        return new TransformSeed(new Vector3f(), 0.0f, 0.0f);
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

    private record TransformSeed(Vector3f position, float yawDeg, float pitchDeg) {
    }
}
