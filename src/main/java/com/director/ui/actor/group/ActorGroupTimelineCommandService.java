package com.director.ui.actor.group;

import com.director.core.DirectorServices;
import com.director.actor.ActorGroup;
import com.director.actor.action.ActorActionType;
import com.director.actor.group.ActorGroupController;
import com.director.ui.timeline.interaction.ActionClipIntervalResolver;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.playback.camera.CameraAccessor;
import com.director.timeline.playback.camera.CameraSample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates timeline clips from project-level ActorGroup commands.
 *
 * <p>Group commands intentionally decompose into actor-level clips. Runtime
 * sampling stays deterministic and existing ActorTrack persistence remains the
 * source of truth.</p>
 */
public final class ActorGroupTimelineCommandService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorGroupTimelineCommandService.class);
    private static final float DEFAULT_SPAWN_CLIP_DURATION = 5.0f;

    private static final ActorGroupTimelineCommandService INSTANCE = new ActorGroupTimelineCommandService();
    private static final ActorGroupController GROUP_CONTROLLER = ActorGroupController.getInstance();

    private ActorGroupTimelineCommandService() {
    }

    public static ActorGroupTimelineCommandService getInstance() {
        return INSTANCE;
    }

    public int createActionClips(ActorGroup group, ActorActionType actionType, float durationSeconds,
                                 float intensity, boolean loop) {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        return createActionClips(model, group, actionType, durationSeconds, intensity, loop);
    }

    int createActionClips(TimelineModel model,
                          ActorGroup group,
                          ActorActionType actionType,
                          float durationSeconds,
                          float intensity,
                          boolean loop) {
        if (model == null) {
            LOGGER.warn("[ActorGroupTimelineCommandService] TimelineModel is not available");
            return 0;
        }
        if (group == null || actionType == null || group.size() == 0) {
            return 0;
        }

        float baseStart = Math.max(0.0f, model.getCurrentTime());
        float duration = Math.max(0.05f, durationSeconds);
        float clampedIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
        List<UUID> actorIds = group.getActorIds();
        List<ActorGroupController.ActionTrigger> triggers =
                GROUP_CONTROLLER.applyDelayPolicy(group, actionType, baseStart);

        int created = 0;
        for (UUID actorId : actorIds) {
            TimelineTrack timelineTrack = model.findActorTrackByActorId(actorId);
            if (timelineTrack == null) {
                LOGGER.debug("[ActorGroupTimelineCommandService] Actor {} has no entity track", actorId);
                continue;
            }

            ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
            if (actorTrack == null) {
                LOGGER.debug("[ActorGroupTimelineCommandService] Track {} has no ActorTrack payload",
                        timelineTrack.getName());
                continue;
            }

            float start = resolveTriggerTime(triggers, actorId, baseStart);
            List<ActorActionClip> resolved = ActionClipIntervalResolver.resolve(
                    actorTrack.getActionClips(),
                    start,
                    start + duration,
                    actionType,
                    clampedIntensity,
                    loop
            );
            actorTrack.replaceActionClips(resolved);
            syncActionClipsToTimeline(model, timelineTrack, actorTrack, resolved);
            created++;
        }

        if (created > 0) {
            model.markDirty();
        }
        LOGGER.info("[ActorGroupTimelineCommandService] Created {} action clips for group {}",
                created, group.getName());
        return created;
    }

    public int copyLatestRouteFromActor(ActorGroup group, UUID sourceActorId) {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        return copyLatestRouteFromActor(model, group, sourceActorId);
    }

    int copyLatestRouteFromActor(TimelineModel model, ActorGroup group, UUID sourceActorId) {
        if (model == null) {
            LOGGER.warn("[ActorGroupTimelineCommandService] TimelineModel is not available");
            return 0;
        }
        if (group == null || sourceActorId == null || group.size() == 0) {
            return 0;
        }

        ActorRouteClip source = findLatestRouteClip(model, sourceActorId);
        if (source == null || source.getWaypoints().size() < 2) {
            LOGGER.warn("[ActorGroupTimelineCommandService] Source actor {} has no usable route", sourceActorId);
            return 0;
        }

        List<UUID> actorIds = group.getActorIds();
        int created = 0;
        for (int i = 0; i < actorIds.size(); i++) {
            UUID actorId = actorIds.get(i);
            TimelineTrack timelineTrack = model.findActorTrackByActorId(actorId);
            if (timelineTrack == null) {
                continue;
            }
            ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
            if (actorTrack == null) {
                continue;
            }

            float delay = computeDelay(group, actorId, i, actorIds.size());
            Vector3f formationOffset = computeFormationOffset(group, source, i, actorIds.size());
            ActorRouteClip copy = copyRouteClip(source, delay, formationOffset);
            actorTrack.addClip(copy);
            EntityTimelineClip timelineClip = new EntityTimelineClip(copy, actorTrack.getColorRgba());
            model.addClip(timelineTrack, timelineClip);
            created++;
        }

        if (created > 0) {
            model.markDirty();
        }
        LOGGER.info("[ActorGroupTimelineCommandService] Copied route from actor {} to {} group members",
                sourceActorId, created);
        return created;
    }

    public int placeGroupAtCamera(ActorGroup group) {
        if (group == null || group.size() == 0) {
            return 0;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        CameraSample cameraSample = CameraAccessor.sampleFromGameCamera(client);
        if (client == null || client.world == null || model == null || cameraSample == null) {
            LOGGER.warn("[ActorGroupTimelineCommandService] Cannot place group: client, world, timeline, or camera unavailable");
            return 0;
        }

        Vector3f basePosition = new Vector3f(
                (float) cameraSample.position().x,
                (float) cameraSample.position().y,
                (float) cameraSample.position().z
        );
        List<ActorGroupController.FormationPosition> formationPositions = GROUP_CONTROLLER.calculateFormation(
                group,
                new Vec3d(basePosition.x, basePosition.y, basePosition.z),
                cameraSample.yaw()
        );
        float currentTime = model.getCurrentTime();
        List<UUID> actorIds = group.getActorIds();

        int placed = 0;
        for (UUID actorId : actorIds) {
            TimelineTrack timelineTrack = model.findActorTrackByActorId(actorId);
            if (timelineTrack == null || timelineTrack.getActorTrackPayload() == null) {
                continue;
            }

            ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
            Vec3d resolvedPosition = resolveFormationPosition(
                    formationPositions,
                    actorId,
                    new Vec3d(basePosition.x, basePosition.y, basePosition.z)
            );
            Vector3f position = new Vector3f((float) resolvedPosition.x, (float) resolvedPosition.y, (float) resolvedPosition.z);
            EntitySpawnClip spawnClip = findSpawnClipAt(actorTrack, currentTime);
            if (spawnClip == null) {
                spawnClip = new EntitySpawnClip("Spawn", currentTime, currentTime + DEFAULT_SPAWN_CLIP_DURATION);
                spawnClip.setVisibleOnSpawn(true);
                actorTrack.addClip(spawnClip);
            }

            spawnClip.setPosition(position.x, position.y, position.z);
            spawnClip.setYawDeg(cameraSample.yaw());
            spawnClip.setPitchDeg(cameraSample.pitch());

            if (spawnClip.getTimelineClip() == null) {
                model.addClip(timelineTrack, new EntityTimelineClip(spawnClip, actorTrack.getColorRgba()));
            }
            placed++;
        }

        if (placed > 0) {
            model.markDirty();
        }
        LOGGER.info("[ActorGroupTimelineCommandService] Placed {} group actors at camera", placed);
        return placed;
    }

    private void syncActionClipsToTimeline(TimelineModel model, TimelineTrack timelineTrack,
                                           ActorTrack actorTrack, List<ActorActionClip> actionClips) {
        for (TimelineClip clip : new ArrayList<>(timelineTrack.getClips())) {
            if (clip instanceof EntityTimelineClip entityTimelineClip
                    && entityTimelineClip.getEntityClip() instanceof ActorActionClip) {
                timelineTrack.removeClip(clip);
            }
        }

        for (ActorActionClip actionClip : actionClips) {
            model.addClip(timelineTrack, new EntityTimelineClip(actionClip, actorTrack.getColorRgba()));
        }
    }

    private ActorRouteClip findLatestRouteClip(TimelineModel model, UUID actorId) {
        TimelineTrack timelineTrack = model.findActorTrackByActorId(actorId);
        if (timelineTrack == null || timelineTrack.getActorTrackPayload() == null) {
            return null;
        }

        float currentTime = model.getCurrentTime();
        ActorRouteClip latestBeforeOrAtPlayhead = null;
        ActorRouteClip latestOverall = null;
        for (EntityClip clip : timelineTrack.getActorTrackPayload().getClips()) {
            if (!(clip instanceof ActorRouteClip routeClip)) {
                continue;
            }
            if (latestOverall == null || routeClip.getStartTime() > latestOverall.getStartTime()) {
                latestOverall = routeClip;
            }
            if (routeClip.getStartTime() <= currentTime
                    && (latestBeforeOrAtPlayhead == null
                    || routeClip.getStartTime() > latestBeforeOrAtPlayhead.getStartTime())) {
                latestBeforeOrAtPlayhead = routeClip;
            }
        }
        return latestBeforeOrAtPlayhead != null ? latestBeforeOrAtPlayhead : latestOverall;
    }

    private ActorRouteClip copyRouteClip(ActorRouteClip source, float startDelaySeconds, Vector3f offset) {
        float start = source.getStartTime() + Math.max(0.0f, startDelaySeconds);
        float end = source.getEndTime() + Math.max(0.0f, startDelaySeconds);
        ActorRouteClip copy = new ActorRouteClip(source.getName(), start, end);
        copy.setSpeed(source.getSpeed());
        copy.setInterpolationMode(source.getInterpolationMode());
        copy.setLoopMode(source.getLoopMode());
        copy.setAlignRotationToRoute(source.isAlignRotationToRoute());

        List<Vector3f> points = new ArrayList<>();
        Vector3f safeOffset = offset != null ? offset : new Vector3f();
        for (Vector3f point : source.getWaypoints()) {
            points.add(new Vector3f(point).add(safeOffset));
        }
        copy.setWaypoints(points);
        return copy;
    }

    private Vector3f computeFormationOffset(ActorGroup group, ActorRouteClip source, int index, int count) {
        if (group == null || source == null || count <= 1) {
            return new Vector3f();
        }
        List<UUID> actorIds = group.getActorIds();
        if (index < 0 || index >= actorIds.size()) {
            return new Vector3f();
        }

        List<Vector3f> points = source.getWaypoints();
        if (points.isEmpty()) {
            return new Vector3f();
        }
        Vector3f centerPoint = points.getFirst();
        float yaw = routeHeadingYaw(source);
        List<ActorGroupController.FormationPosition> formations = GROUP_CONTROLLER.calculateFormation(
                group,
                new Vec3d(centerPoint.x, centerPoint.y, centerPoint.z),
                yaw
        );
        UUID actorId = actorIds.get(index);
        Vec3d resolved = resolveFormationPosition(
                formations,
                actorId,
                new Vec3d(centerPoint.x, centerPoint.y, centerPoint.z)
        );
        return new Vector3f(
                (float) (resolved.x - centerPoint.x),
                (float) (resolved.y - centerPoint.y),
                (float) (resolved.z - centerPoint.z)
        );
    }

    private float routeHeadingYaw(ActorRouteClip source) {
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        List<Vector3f> points = source.getWaypoints();
        if (points.size() >= 2) {
            Vector3f first = points.getFirst();
            for (int i = 1; i < points.size(); i++) {
                Vector3f candidate = new Vector3f(points.get(i)).sub(first);
                candidate.y = 0.0f;
                if (candidate.lengthSquared() > 0.0001f) {
                    forward = candidate.normalize();
                    break;
                }
            }
        }
        return (float) Math.toDegrees(Math.atan2(forward.x, forward.z));
    }

    private EntitySpawnClip findSpawnClipAt(ActorTrack actorTrack, float timeSeconds) {
        if (actorTrack == null) {
            return null;
        }
        for (EntityClip clip : actorTrack.findClipsAt(timeSeconds)) {
            if (clip instanceof EntitySpawnClip spawnClip) {
                return spawnClip;
            }
        }
        return null;
    }

    private Vector3f forwardFromYaw(float yawDeg) {
        double yawRad = Math.toRadians(yawDeg);
        return new Vector3f(
                (float) -Math.sin(yawRad),
                0.0f,
                (float) Math.cos(yawRad)
        );
    }

    private float computeDelay(ActorGroup group, UUID actorId, int index, int count) {
        if (group == null || actorId == null || count <= 0) {
            return 0.0f;
        }

        float step = Math.max(0.0f, group.getDelayStepSeconds());
        return switch (group.getDelayPolicy()) {
            case NONE -> 0.0f;
            case SEQUENTIAL -> index * step;
            case WAVE -> Math.abs(index - ((count - 1) * 0.5f)) * step;
            case RANDOM -> Math.floorMod(group.getGroupId().hashCode() ^ actorId.hashCode(), count) * step;
        };
    }

    private float resolveTriggerTime(List<ActorGroupController.ActionTrigger> triggers, UUID actorId, float fallback) {
        if (triggers == null || actorId == null) {
            return fallback;
        }
        for (ActorGroupController.ActionTrigger trigger : triggers) {
            if (trigger.actorId().equals(actorId)) {
                return trigger.triggerTime();
            }
        }
        return fallback;
    }

    private Vec3d resolveFormationPosition(List<ActorGroupController.FormationPosition> positions,
                                           UUID actorId,
                                           Vec3d fallback) {
        if (positions == null || actorId == null) {
            return fallback;
        }
        for (ActorGroupController.FormationPosition position : positions) {
            if (position.actorId().equals(actorId)) {
                return position.position();
            }
        }
        return fallback;
    }
}
