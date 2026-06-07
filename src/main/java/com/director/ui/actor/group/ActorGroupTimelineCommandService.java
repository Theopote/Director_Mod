package com.director.ui.actor.group;

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
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.entity.EntityTrack;
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
 * sampling stays deterministic and existing EntityTrack persistence remains the
 * source of truth.</p>
 */
public final class ActorGroupTimelineCommandService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorGroupTimelineCommandService.class);

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

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
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
            TimelineTrack timelineTrack = model.findEntityTrackByActorId(actorId);
            if (timelineTrack == null) {
                LOGGER.debug("[ActorGroupTimelineCommandService] Actor {} has no entity track", actorId);
                continue;
            }

            EntityTrack entityTrack = timelineTrack.getEntityTrackPayload();
            if (entityTrack == null) {
                LOGGER.debug("[ActorGroupTimelineCommandService] Track {} has no EntityTrack payload",
                        timelineTrack.getName());
                continue;
            }

            float start = baseStart + computeDelay(group, actorId, i, actorIds.size());
            List<ActorActionClip> resolved = ActionClipIntervalResolver.resolve(
                    entityTrack.getActionClips(),
                    start,
                    start + duration,
                    actionType,
                    clampedIntensity,
                    loop
            );
            entityTrack.replaceActionClips(resolved);
            syncActionClipsToTimeline(model, timelineTrack, entityTrack, resolved);
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

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
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
            TimelineTrack timelineTrack = model.findEntityTrackByActorId(actorId);
            if (timelineTrack == null) {
                continue;
            }
            EntityTrack entityTrack = timelineTrack.getEntityTrackPayload();
            if (entityTrack == null) {
                continue;
            }

            float delay = computeDelay(group, actorId, i, actorIds.size());
            ActorRouteClip copy = copyRouteClip(source, delay);
            entityTrack.addClip(copy);
            EntityTimelineClip timelineClip = new EntityTimelineClip(copy, entityTrack.getColorRgba());
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
                                           EntityTrack entityTrack, List<ActorActionClip> actionClips) {
        for (TimelineClip clip : new ArrayList<>(timelineTrack.getClips())) {
            if (clip instanceof EntityTimelineClip entityTimelineClip
                    && entityTimelineClip.getEntityClip() instanceof ActorActionClip) {
                timelineTrack.removeClip(clip);
            }
        }

        for (ActorActionClip actionClip : actionClips) {
            model.addClip(timelineTrack, new EntityTimelineClip(actionClip, entityTrack.getColorRgba()));
        }
    }

    private ActorRouteClip findLatestRouteClip(TimelineModel model, UUID actorId) {
        TimelineTrack timelineTrack = model.findEntityTrackByActorId(actorId);
        if (timelineTrack == null || timelineTrack.getEntityTrackPayload() == null) {
            return null;
        }

        float currentTime = model.getCurrentTime();
        ActorRouteClip latestBeforeOrAtPlayhead = null;
        ActorRouteClip latestOverall = null;
        for (EntityClip clip : timelineTrack.getEntityTrackPayload().getClips()) {
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

    private ActorRouteClip copyRouteClip(ActorRouteClip source, float startDelaySeconds) {
        float start = source.getStartTime() + Math.max(0.0f, startDelaySeconds);
        float end = source.getEndTime() + Math.max(0.0f, startDelaySeconds);
        ActorRouteClip copy = new ActorRouteClip(source.getName(), start, end);
        copy.setSpeed(source.getSpeed());
        copy.setInterpolationMode(source.getInterpolationMode());
        copy.setLoopMode(source.getLoopMode());
        copy.setAlignRotationToRoute(source.isAlignRotationToRoute());

        List<Vector3f> points = new ArrayList<>();
        for (Vector3f point : source.getWaypoints()) {
            points.add(new Vector3f(point));
        }
        copy.setWaypoints(points);
        return copy;
    }
}
