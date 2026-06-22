package com.director.ui.actor.group;

import com.director.core.DirectorServices;
import com.director.actor.ActorGroup;
import com.director.actor.action.ActorActionType;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.interaction.ActionClipIntervalResolver;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.entity.ActorActionClip;
import com.director.ui.timeline.model.entity.ActorRouteClip;
import com.director.ui.timeline.model.entity.EntityClip;
import com.director.ui.timeline.model.entity.EntitySpawnClip;
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.entity.ActorTrack;
import com.director.ui.timeline.playback.camera.CameraAccessor;
import com.director.ui.timeline.playback.camera.CameraSample;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private ActorGroupTimelineCommandService() {
    }

    public static ActorGroupTimelineCommandService getInstance() {
        return INSTANCE;
    }

    public int createActionClips(ActorGroup group, ActorActionType actionType, float durationSeconds,
                                 float intensity, boolean loop) {
        if (group == null || actionType == null || group.size() == 0) {
            return 0;
        }

        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorGroupTimelineCommandService] TimelineModel is not available");
            return 0;
        }

        float baseStart = Math.max(0.0f, model.getCurrentTime());
        float duration = Math.max(0.05f, durationSeconds);
        float clampedIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
        List<UUID> actorIds = group.getActorIds();

        int created = 0;
        for (int i = 0; i < actorIds.size(); i++) {
            UUID actorId = actorIds.get(i);
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

            float start = baseStart + computeDelay(group, actorId, i, actorIds.size());
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
        if (group == null || sourceActorId == null || group.size() == 0) {
            return 0;
        }

        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorGroupTimelineCommandService] TimelineModel is not available");
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
        Vector3f forward = forwardFromYaw(cameraSample.yaw());
        float currentTime = model.getCurrentTime();
        List<UUID> actorIds = group.getActorIds();

        int placed = 0;
        for (int i = 0; i < actorIds.size(); i++) {
            UUID actorId = actorIds.get(i);
            TimelineTrack timelineTrack = model.findActorTrackByActorId(actorId);
            if (timelineTrack == null || timelineTrack.getActorTrackPayload() == null) {
                continue;
            }

            ActorTrack actorTrack = timelineTrack.getActorTrackPayload();
            Vector3f offset = computeFormationOffset(group, forward, i, actorIds.size());
            Vector3f position = new Vector3f(basePosition).add(offset);
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

    private float computeDelay(ActorGroup group, UUID actorId, int index, int count) {
        float step = Math.max(0.0f, group.getDelayStepSeconds());
        return switch (group.getDelayPolicy()) {
            case NONE -> 0.0f;
            case SEQUENTIAL -> index * step;
            case WAVE -> Math.abs(index - ((count - 1) * 0.5f)) * step;
            case RANDOM -> Math.floorMod(Objects.hash(group.getGroupId(), actorId), Math.max(1, count)) * step;
        };
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

        RouteAxes axes = computeRouteAxes(source);
        return computeFormationOffset(group, axes.forward, index, count);
    }

    private Vector3f computeFormationOffset(ActorGroup group, Vector3f forward, int index, int count) {
        if (group == null || count <= 1) {
            return new Vector3f();
        }

        Vector3f safeForward = new Vector3f(forward != null ? forward : new Vector3f(0.0f, 0.0f, 1.0f));
        safeForward.y = 0.0f;
        if (safeForward.lengthSquared() <= 0.0001f) {
            safeForward.set(0.0f, 0.0f, 1.0f);
        } else {
            safeForward.normalize();
        }
        Vector3f right = new Vector3f(-safeForward.z, 0.0f, safeForward.x);
        if (right.lengthSquared() <= 0.0001f) {
            right.set(1.0f, 0.0f, 0.0f);
        } else {
            right.normalize();
        }

        RouteAxes axes = new RouteAxes(safeForward, right);
        float spacing = Math.max(0.1f, group.getFormationSpacing());
        float center = (count - 1) * 0.5f;

        return switch (group.getFormationType()) {
            case FREE -> new Vector3f();
            case LINE -> new Vector3f(axes.right).mul((index - center) * spacing);
            case CIRCLE -> {
                float radius = Math.max(spacing, (spacing * count) / (float) (Math.PI * 2.0));
                float angle = ((float) Math.PI * 2.0f * index) / count;
                Vector3f rightPart = new Vector3f(axes.right).mul((float) Math.cos(angle) * radius);
                Vector3f forwardPart = new Vector3f(axes.forward).mul((float) Math.sin(angle) * radius);
                yield rightPart.add(forwardPart);
            }
            case GRID -> {
                int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
                int row = index / columns;
                int col = index % columns;
                int rows = (int) Math.ceil(count / (float) columns);
                float colOffset = (col - (columns - 1) * 0.5f) * spacing;
                float rowOffset = (row - (rows - 1) * 0.5f) * spacing;
                Vector3f rightPart = new Vector3f(axes.right).mul(colOffset);
                Vector3f backPart = new Vector3f(axes.forward).mul(-rowOffset);
                yield rightPart.add(backPart);
            }
            case FOLLOW_LEADER -> new Vector3f(axes.forward).mul(-index * spacing);
        };
    }

    private RouteAxes computeRouteAxes(ActorRouteClip source) {
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        List<Vector3f> points = source.getWaypoints();
        if (points.size() >= 2) {
            Vector3f first = points.get(0);
            for (int i = 1; i < points.size(); i++) {
                Vector3f candidate = new Vector3f(points.get(i)).sub(first);
                candidate.y = 0.0f;
                if (candidate.lengthSquared() > 0.0001f) {
                    forward = candidate.normalize();
                    break;
                }
            }
        }
        Vector3f right = new Vector3f(-forward.z, 0.0f, forward.x);
        if (right.lengthSquared() <= 0.0001f) {
            right.set(1.0f, 0.0f, 0.0f);
        } else {
            right.normalize();
        }
        return new RouteAxes(forward, right);
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

    private record RouteAxes(Vector3f forward, Vector3f right) {
    }
}
