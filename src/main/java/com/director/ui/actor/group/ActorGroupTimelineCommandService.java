package com.director.ui.actor.group;

import com.director.core.DirectorServices;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRegistryManager;
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
import com.director.timeline.model.entity.EntityTransformClip;
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

    public enum SkipReason { MISSING_ACTOR, NO_TRACK, LOCKED, UNSUPPORTED_ACTION, SOURCE_ACTOR, ROUTE_CONFLICT }
    public record SkippedMember(UUID actorId, SkipReason reason) {}
    public record Preview(List<UUID> eligible, List<SkippedMember> skipped, String problem, int overwrittenMembers) {
        public Preview { eligible = List.copyOf(eligible); skipped = List.copyOf(skipped); }
        public boolean canExecute() { return problem == null && !eligible.isEmpty(); }
    }

    public Preview previewActions(TimelineModel model, ActorGroup group, ActorActionType action, float duration) {
        Preview members = previewMembers(model, group, action, null);
        if (action == null || !Float.isFinite(duration) || duration <= 0)
            return new Preview(List.of(), members.skipped(), "invalid_action", 0);
        if (members.problem() != null) return members;
        int overwritten = 0;
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, action, Math.max(0, model == null ? 0 : model.getCurrentTime()));
        for (UUID id : members.eligible()) {
            float start = resolveTriggerTime(triggers, id, model.getCurrentTime());
            if (model.findActorTrackByActorId(id).getActorTrackPayload().getActionClips().stream()
                    .anyMatch(c -> c.getStartTime() < start + Math.max(0.05f, duration) && c.getEndTime() > start)) overwritten++;
        }
        return new Preview(members.eligible(), members.skipped(), members.problem(), overwritten);
    }

    public Preview previewRoutes(TimelineModel model, ActorGroup group, UUID sourceActorId) {
        Preview members = previewMembers(model, group, null, sourceActorId);
        if (members.problem() != null) return members;
        ActorRouteClip source = model == null || sourceActorId == null ? null : findLatestRouteClip(model, sourceActorId);
        if (source == null || source.getWaypoints().size() < 2 || source.isMuted()
                || ActorRegistryManager.getInstance().getActor(sourceActorId).isEmpty())
            return new Preview(List.of(), members.skipped(), "no_route", 0);
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, ActorActionType.IDLE, 0);
        List<UUID> eligible = new ArrayList<>();
        List<SkippedMember> skipped = new ArrayList<>(members.skipped());
        for (UUID id : members.eligible()) {
            float delay = Math.max(0, resolveTriggerTime(triggers, id, 0));
            boolean conflict = model.findActorTrackByActorId(id).getActorTrackPayload().getClips().stream()
                    .anyMatch(c -> !c.isMuted() && (c instanceof ActorRouteClip || c instanceof EntityTransformClip)
                            && c.getStartTime() < source.getEndTime() + delay && c.getEndTime() > source.getStartTime() + delay);
            if (conflict) skipped.add(new SkippedMember(id, SkipReason.ROUTE_CONFLICT));
            else eligible.add(id);
        }
        return new Preview(eligible, skipped, members.problem(), 0);
    }

    public Preview previewPlacement(TimelineModel model, ActorGroup group) {
        return previewMembers(model, group, null, null);
    }

    private Preview previewMembers(TimelineModel model, ActorGroup group, ActorActionType action, UUID source) {
        if (model == null || group == null || group.size() == 0)
            return new Preview(List.of(), List.of(), "no_members", 0);
        if (!Float.isFinite(model.getCurrentTime()) || !Float.isFinite(group.getDelayStepSeconds())
                || !Float.isFinite(group.getFormationSpacing()))
            return new Preview(List.of(), List.of(), "invalid_parameters", 0);
        List<UUID> eligible = new ArrayList<>();
        List<SkippedMember> skipped = new ArrayList<>();
        var registry = ActorRegistryManager.getInstance();
        for (UUID id : group.getActorIds()) {
            var actor = registry.getActor(id).orElse(null);
            var track = model.findActorTrackByActorId(id);
            SkipReason reason = actor == null ? SkipReason.MISSING_ACTOR
                    : track == null || track.getActorTrackPayload() == null ? SkipReason.NO_TRACK
                    : track.isLocked() ? SkipReason.LOCKED
                    : id.equals(source) ? SkipReason.SOURCE_ACTOR
                    : action != null && !com.director.actor.ActorCapabilities.of(actor).supports(action)
                            ? SkipReason.UNSUPPORTED_ACTION : null;
            if (reason == null) eligible.add(id); else skipped.add(new SkippedMember(id, reason));
        }
        return new Preview(eligible, skipped, null, 0);
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
        Preview preview = previewActions(model, group, actionType, durationSeconds);
        if (!preview.canExecute() || !Float.isFinite(intensity)) return 0;
        float baseStart = Math.max(0, model.getCurrentTime());
        float duration = Math.max(0.05f, durationSeconds);
        float clampedIntensity = Math.max(0, Math.min(1, intensity));
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, actionType, baseStart);
        List<ActorGroupEditCommand.Change> changes = new ArrayList<>();
        for (UUID actorId : preview.eligible()) {
            var track = model.findActorTrackByActorId(actorId);
            var payload = track.getActorTrackPayload();
            var before = List.copyOf(payload.getActionClips());
            var beforeWrappers = track.getClips().stream()
                    .filter(c -> c instanceof EntityTimelineClip e && e.getEntityClip() instanceof ActorActionClip).toList();
            float start = resolveTriggerTime(triggers, actorId, baseStart);
            var after = ActionClipIntervalResolver.resolve(before, start, start + duration, actionType, clampedIntensity, loop);
            List<TimelineClip> afterWrappers = new ArrayList<>();
            for (var clip : after) {
                var wrapper = beforeWrappers.stream().filter(c -> ((EntityTimelineClip)c).getEntityClip() == clip).findFirst().orElse(null);
                afterWrappers.add(wrapper == null ? prepareWrapper(clip, payload.getColorRgba()) : wrapper);
            }
            changes.add(new ActorGroupEditCommand.Change(
                    () -> setActionState(track, payload, before, beforeWrappers),
                    () -> setActionState(track, payload, after, afterWrappers)));
        }
        ActorGroupEditCommand.execute(model, "Group actor actions", changes);
        return changes.size();
    }

    public int copyLatestRouteFromActor(ActorGroup group, UUID sourceActorId) {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        return copyLatestRouteFromActor(model, group, sourceActorId);
    }

    int copyLatestRouteFromActor(TimelineModel model, ActorGroup group, UUID sourceActorId) {
        Preview preview = previewRoutes(model, group, sourceActorId);
        if (!preview.canExecute()) return 0;
        var source = findLatestRouteClip(model, sourceActorId);
        var ids = group.getActorIds();
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, ActorActionType.IDLE, 0);
        List<ActorGroupEditCommand.Change> changes = new ArrayList<>();
        for (UUID id : preview.eligible()) {
            var track = model.findActorTrackByActorId(id);
            float delay = Math.max(0, resolveTriggerTime(triggers, id, 0));
            var copy = copyRouteClip(source, delay, computeFormationOffset(group, source, ids.indexOf(id), ids.size()));
            var wrapper = prepareWrapper(copy, track.getActorTrackPayload().getColorRgba());
            changes.add(addClipChange(track, copy, wrapper));
        }
        ActorGroupEditCommand.execute(model, "Group actor routes", changes);
        return changes.size();
    }

    public int placeGroupAtCamera(ActorGroup group) {
        MinecraftClient client = MinecraftClient.getInstance();
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (client == null || client.world == null || model == null) return 0;
        CameraSample camera = CameraAccessor.sampleFromGameCamera(client);
        if (camera == null) return 0;
        return placeGroup(model, group, new Vec3d(camera.position().x, camera.position().y, camera.position().z), camera.yaw(), camera.pitch());
    }

    int placeGroup(TimelineModel model, ActorGroup group, Vec3d base, float yaw, float pitch) {
        Preview preview = previewPlacement(model, group);
        if (!preview.canExecute() || base == null || !Double.isFinite(base.x) || !Double.isFinite(base.y)
                || !Double.isFinite(base.z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return 0;
        var positions = GROUP_CONTROLLER.calculateFormation(group, base, yaw);
        float time = model.getCurrentTime();
        List<ActorGroupEditCommand.Change> changes = new ArrayList<>();
        for (UUID id : preview.eligible()) {
            var track = model.findActorTrackByActorId(id);
            var payload = track.getActorTrackPayload();
            var position = resolveFormationPosition(positions, id, base);
            EntitySpawnClip existing = findSpawnClipAt(payload, time);
            EntitySpawnClip clip = existing == null ? new EntitySpawnClip("Spawn", time, time + DEFAULT_SPAWN_CLIP_DURATION) : existing;
            Vector3f beforePosition = new Vector3f(clip.getPosition());
            float beforeYaw = clip.getYawDeg(), beforePitch = clip.getPitchDeg();
            var previousWrapper = clip.getTimelineClip();
            var wrapper = previousWrapper == null ? prepareWrapper(clip, payload.getColorRgba()) : previousWrapper;
            boolean addedWrapper = !track.getClips().contains(wrapper);
            changes.add(new ActorGroupEditCommand.Change(() -> {
                clip.setPosition(beforePosition.x, beforePosition.y, beforePosition.z);
                clip.setYawDeg(beforeYaw); clip.setPitchDeg(beforePitch);
                if (existing == null) payload.removeClip(clip.getId());
                if (addedWrapper) track.removeClip(wrapper);
                if (previousWrapper == null) clip.setTimelineClip(null);
            }, () -> {
                clip.setPosition((float)position.x, (float)position.y, (float)position.z);
                clip.setYawDeg(yaw); clip.setPitchDeg(pitch);
                if (existing == null && !payload.getClips().contains(clip)) payload.addClip(clip);
                clip.setTimelineClip(wrapper);
                if (addedWrapper && !track.getClips().contains(wrapper)) track.addClip(wrapper);
            }));
        }
        ActorGroupEditCommand.execute(model, "Place actor group", changes);
        return changes.size();
    }

    private static EntityTimelineClip prepareWrapper(EntityClip clip, int color) {
        // The wrapper constructor binds itself; staging must not mutate existing clips.
        var previous = clip.getTimelineClip();
        var wrapper = new EntityTimelineClip(clip, color);
        clip.setTimelineClip(previous);
        return wrapper;
    }

    private static ActorGroupEditCommand.Change addClipChange(TimelineTrack track, EntityClip clip, EntityTimelineClip wrapper) {
        var payload = track.getActorTrackPayload();
        return new ActorGroupEditCommand.Change(() -> {
            payload.removeClip(clip.getId()); track.removeClip(wrapper); clip.setTimelineClip(null);
        }, () -> {
            clip.setTimelineClip(wrapper);
            if (!payload.getClips().contains(clip)) payload.addClip(clip);
            if (!track.getClips().contains(wrapper)) track.addClip(wrapper);
        });
    }

    private static void setActionState(TimelineTrack track, ActorTrack payload, List<ActorActionClip> clips, List<TimelineClip> wrappers) {
        for (var clip : payload.getActionClips()) clip.setTimelineClip(null);
        payload.replaceActionClips(clips);
        for (var clip : clips) clip.setTimelineClip(null);
        for (var wrapper : new ArrayList<>(track.getClips()))
            if (wrapper instanceof EntityTimelineClip e && e.getEntityClip() instanceof ActorActionClip) track.removeClip(wrapper);
        for (var wrapper : wrappers) {
            ((EntityTimelineClip)wrapper).getEntityClip().setTimelineClip((EntityTimelineClip)wrapper);
            track.addClip(wrapper);
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
            if (!(clip instanceof ActorRouteClip routeClip) || routeClip.isMuted()) {
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
        copy.setJumpStart(source.getJumpStart());
        copy.setJumpDuration(source.getJumpDuration());
        copy.setJumpHeight(source.getJumpHeight());

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
