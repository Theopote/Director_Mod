package com.director.ui.actor.group;

import com.director.actor.animation.AnimationData;
import com.director.actor.animation.asset.AnimationAsset;
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
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.EntityDespawnClip;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.ui.actor.pose.ActorPresetValidation;
import com.director.timeline.playback.camera.CameraAccessor;
import com.director.timeline.playback.camera.CameraSample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

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
    private static final float DEFAULT_SPAWN_CLIP_DURATION = 5.0f;

    private static final ActorGroupTimelineCommandService INSTANCE = new ActorGroupTimelineCommandService();
    private static final ActorGroupController GROUP_CONTROLLER = ActorGroupController.getInstance();

    private ActorGroupTimelineCommandService() {
    }

    public static ActorGroupTimelineCommandService getInstance() {
        return INSTANCE;
    }

    public enum SkipReason { MISSING_ACTOR, NO_TRACK, LOCKED, UNSUPPORTED_ACTION, SOURCE_ACTOR, ROUTE_CONFLICT,
        PLACEMENT_CONTROLLED, MUTED, AMBIGUOUS_TRACK, NO_PRESET_SPAWN, PRESET_CONFLICT, INCOMPATIBLE_PRESET }
    public record SkippedMember(UUID actorId, SkipReason reason) {}
    public record Preview(List<UUID> eligible, List<SkippedMember> skipped, String problem, int overwrittenMembers) {
        public Preview { eligible = List.copyOf(eligible); skipped = List.copyOf(skipped); }
        public boolean canExecute() { return problem == null && !eligible.isEmpty(); }
    }

    private record PresetPlacement(TimelineTrack track, float start, float end) {}
    private record PresetPlan(Preview preview, List<PresetPlacement> placements) {}

    public Preview previewPresets(TimelineModel model, ActorGroup group, String animationId, float duration) {
        return planPresets(model, group, animationId, duration).preview();
    }

    public int createPresetClips(TimelineModel model, ActorGroup group, String animationId, float duration, boolean loop) {
        var plan = planPresets(model, group, animationId, duration);
        if (!plan.preview().canExecute()) return 0;
        var asset = AnimationAssetLibrary.getInstance().find(animationId);
        String name = asset.map(AnimationAsset::name).orElseGet(() -> com.director.actor.animation.AnimationLibrary.getInstance()
                .get(animationId).map(AnimationData::getName).orElse(animationId));
        List<ActorGroupEditCommand.Change> changes = new ArrayList<>();
        for (var placement : plan.placements()) {
            var clip = new EntityAnimationClip(name, placement.start(), placement.end());
            clip.setAnimationId(animationId);
            clip.setSource(asset.isPresent() ? EntityAnimationClip.AnimationSource.CUSTOM : EntityAnimationClip.AnimationSource.BUILT_IN);
            clip.setLoop(loop);
            var track = placement.track();
            var wrapper = prepareWrapper(clip, track.getActorTrackPayload().getColorRgba());
            changes.add(addClipChange(track, clip, wrapper));
        }
        ActorGroupEditCommand.execute(model, "Schedule group animation presets", changes);
        return changes.size();
    }

    private PresetPlan planPresets(TimelineModel model, ActorGroup group, String animationId, float duration) {
        Preview members = previewMembers(model, group, null, null);
        if (members.problem() != null) return new PresetPlan(members, List.of());
        if (animationId == null || animationId.isBlank() || !Float.isFinite(duration) || duration < .05f)
            return new PresetPlan(new Preview(List.of(), members.skipped(), "invalid_preset", 0), List.of());

        List<UUID> eligible = new ArrayList<>();
        List<SkippedMember> skipped = new ArrayList<>(members.skipped());
        List<PresetPlacement> placements = new ArrayList<>();
        var library = AnimationAssetLibrary.getInstance();
        var registry = ActorRegistryManager.getInstance();
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, ActorActionType.IDLE, model.getCurrentTime());
        for (var trigger : triggers) {
            UUID id = trigger.actorId();
            if (!members.eligible().contains(id)) continue;
            var track = resolveTrack(model, id);
            float start = trigger.triggerTime();
            float end = start + duration;
            if (!Float.isFinite(end) || start < 0 || end <= start)
                return new PresetPlan(new Preview(List.of(), skipped, "invalid_parameters", 0), List.of());
            List<EntityClip> clips = null;
            if (track != null) {
                clips = track.getActorTrackPayload().getClips().stream()
                        .filter(clip -> !clip.isMuted() && clip.getStartTime() < end && clip.getEndTime() > start).toList();
            }
            List<EntitySpawnClip> spawns = null;
            if (clips != null) {
                spawns = clips.stream().filter(EntitySpawnClip.class::isInstance).map(EntitySpawnClip.class::cast).toList();
            }
            // A separate animation clip plays under manual bone corrections. Existing spawn presets
            // have higher priority, so overlapping preset or animation clips are reported as conflicts.
            if (clips != null && (clips.stream().anyMatch(clip -> clip instanceof EntityAnimationClip animation && animation.hasAnimation())
                    || spawns.stream().anyMatch(spawn -> spawn.getPreset() != null || spawn.getSecondaryPreset() != null))) {
                skipped.add(new SkippedMember(id, SkipReason.PRESET_CONFLICT));
                continue;
            }
            if (spawns != null && (spawns.size() != 1 || !spawns.getFirst().isVisibleOnSpawn()
                    || spawns.getFirst().getStartTime() > start || spawns.getFirst().getEndTime() < end
                    || clips.stream().anyMatch(EntityDespawnClip.class::isInstance))) {
                skipped.add(new SkippedMember(id, SkipReason.NO_PRESET_SPAWN));
                continue;
            }
            try {
                if (spawns != null) {
                    ActorPresetValidation.require(registry.getActor(id).orElse(null), spawns.getFirst(), library, animationId);
                }
            } catch (IllegalArgumentException incompatible) {
                skipped.add(new SkippedMember(id, SkipReason.INCOMPATIBLE_PRESET));
                continue;
            }
            eligible.add(id);
            placements.add(new PresetPlacement(track, start, end));
        }
        return new PresetPlan(new Preview(eligible, skipped, null, 0), List.copyOf(placements));
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
            if (Objects.requireNonNull(resolveTrack(model, id)).getActorTrackPayload().getActionClips().stream()
                    .anyMatch(c -> c.getStartTime() < start + Math.max(0.05f, duration) && c.getEndTime() > start)) overwritten++;
        }
        return new Preview(members.eligible(), members.skipped(), null, overwritten);
    }

    public Preview previewRoutes(TimelineModel model, ActorGroup group, UUID sourceActorId) {
        return previewRoutes(model, group, sourceActorId, null);
    }

    public Preview previewRoutes(TimelineModel model, ActorGroup group, UUID sourceActorId, UUID routeId) {
        return previewRoutes(model, group, sourceActorId, routeId, false);
    }

    public Preview previewRoutes(TimelineModel model, ActorGroup group, UUID sourceActorId, UUID routeId, boolean atPlayhead) {
        Preview members = previewMembers(model, group, null, sourceActorId);
        if (members.problem() != null) return members;
        ActorRouteClip source = resolveRoute(model, sourceActorId, routeId);
        if (source == null || source.getWaypoints().size() < 2 || source.isMuted()
                || ActorRegistryManager.getInstance().getActor(sourceActorId).isEmpty())
            return new Preview(List.of(), members.skipped(), "no_route", 0);
        float shift = atPlayhead ? model.getCurrentTime() - source.getStartTime() : 0;
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, ActorActionType.IDLE, 0);
        List<UUID> eligible = new ArrayList<>();
        List<SkippedMember> skipped = new ArrayList<>(members.skipped());
        for (UUID id : members.eligible()) {
            float delay = Math.max(0, resolveTriggerTime(triggers, id, 0)) + followDelay(group, source, sourceActorId, id);
            boolean conflict = Objects.requireNonNull(resolveTrack(model, id)).getActorTrackPayload().getClips().stream()
                    .anyMatch(c -> !c.isMuted() && (c instanceof ActorRouteClip || c instanceof EntityTransformClip)
                            && c.getStartTime() < source.getEndTime() + shift + delay && c.getEndTime() > source.getStartTime() + shift + delay);
            if (conflict) skipped.add(new SkippedMember(id, SkipReason.ROUTE_CONFLICT));
            else eligible.add(id);
        }
        return new Preview(eligible, skipped, null, 0);
    }

    public Preview previewPlacement(TimelineModel model, ActorGroup group) {
        Preview members = previewMembers(model, group, null, null);
        if (members.problem() != null) return members;
        List<UUID> eligible = new ArrayList<>();
        List<SkippedMember> skipped = new ArrayList<>(members.skipped());
        for (UUID id : members.eligible()) {
            var payload = Objects.requireNonNull(resolveTrack(model, id)).getActorTrackPayload();
            float time = model.getCurrentTime();
            var sample = new com.director.timeline.playback.entity.EntityClipSampler(payload).sampleSnapshot(time);
            var spawn = payload.findSpawnClipAt(time);
            boolean controlled = sample.relationEnabled || sample.routeClip != null || spawn != null && !spawn.getKeyframes().isEmpty()
                    || payload.findClipsAt(time).stream()
                            .anyMatch(c -> !c.isMuted() && c instanceof EntityTransformClip);
            if (controlled) skipped.add(new SkippedMember(id, SkipReason.PLACEMENT_CONTROLLED));
            else eligible.add(id);
        }
        return new Preview(eligible, skipped, null, 0);
    }

    private static TimelineTrack resolveTrack(TimelineModel model, UUID actorId) {
        try {
            return com.director.ui.actor.pose.ActorAnimationAssetEditing.resolveActorTrack(model, actorId);
        } catch (IllegalArgumentException unavailable) {
            return null;
        }
    }

    private Preview previewMembers(TimelineModel model, ActorGroup group, ActorActionType action, UUID source) {
        if (model == null || group == null || group.size() == 0)
            return new Preview(List.of(), List.of(), "no_members", 0);
        if (model.isPlaying() || model.isScrubbing())
            return new Preview(List.of(), List.of(), "pause_required", 0);
        if (model.getCurrentTime() < 0 || !Float.isFinite(model.getCurrentTime()) || !Float.isFinite(group.getDelayStepSeconds())
                || !Float.isFinite(group.getFormationSpacing()))
            return new Preview(List.of(), List.of(), "invalid_parameters", 0);
        List<UUID> eligible = new ArrayList<>();
        List<SkippedMember> skipped = new ArrayList<>();
        var registry = ActorRegistryManager.getInstance();
        for (UUID id : group.getActorIds()) {
            var actor = registry.getActor(id).orElse(null);
            var track = resolveTrack(model, id);
            SkipReason reason = actor == null ? SkipReason.MISSING_ACTOR
                    : track == null ? (model.getAllTracks().stream().filter(t -> t.getActorTrackPayload() != null
                            && t.getActorTrackPayload().getActorRef() != null
                            && id.equals(t.getActorTrackPayload().getActorRef().getActorId())).count() > 1
                            ? SkipReason.AMBIGUOUS_TRACK : SkipReason.NO_TRACK)
                    : track.getActorTrackPayload() == null ? SkipReason.NO_TRACK
                    : track.isLocked() ? SkipReason.LOCKED
                    : track.isMuted() ? SkipReason.MUTED
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
        float clampedIntensity = Math.clamp(intensity, 0, 1);
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, actionType, baseStart);
        List<ActorGroupEditCommand.Change> changes = new ArrayList<>();
        for (UUID actorId : preview.eligible()) {
            var track = resolveTrack(model, actorId);
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

    int copyLatestRouteFromActor(TimelineModel model, ActorGroup group, UUID sourceActorId) {
        return copyRouteFromActor(model, group, sourceActorId, null);
    }

    public int copyRouteFromActor(TimelineModel model, ActorGroup group, UUID sourceActorId, UUID routeId) {
        return copyRouteFromActor(model, group, sourceActorId, routeId, false);
    }

    public int copyRouteFromActor(TimelineModel model, ActorGroup group, UUID sourceActorId, UUID routeId, boolean atPlayhead) {
        Preview preview = previewRoutes(model, group, sourceActorId, routeId, atPlayhead);
        if (!preview.canExecute()) return 0;
        var source = resolveRoute(model, sourceActorId, routeId);
        var ids = group.getActorIds();
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, ActorActionType.IDLE, 0);
        List<ActorGroupEditCommand.Change> changes = new ArrayList<>();
        for (UUID id : preview.eligible()) {
            var track = resolveTrack(model, id);
            float delay = Math.max(0, resolveTriggerTime(triggers, id, 0)) + followDelay(group, source, sourceActorId, id);
            ActorRouteClip copy = null;
            if (source != null) {
                Vector3f offset = computeFormationOffset(group, source, ids.indexOf(id), ids.size());
                if (group.getFormationType() == ActorGroup.FormationType.FREE) {
                    var sourceSpawn = findSpawnClipAt(Objects.requireNonNull(resolveTrack(model, sourceActorId)).getActorTrackPayload(), model.getCurrentTime());
                    EntitySpawnClip memberSpawn = null;
                    if (track != null) {
                        memberSpawn = findSpawnClipAt(track.getActorTrackPayload(), model.getCurrentTime());
                    }
                    if (sourceSpawn != null && memberSpawn != null)
                        offset = new Vector3f(memberSpawn.getPosition()).sub(sourceSpawn.getPosition());
                }
                if (group.getFormationType() == ActorGroup.FormationType.FOLLOW_LEADER) offset = new Vector3f();
                copy = copyRouteClip(source, delay, offset);
                if (atPlayhead) copy.setTimeRange(model.getCurrentTime() + delay,
                        model.getCurrentTime() + delay + source.getEndTime() - source.getStartTime());
            }
            EntityTimelineClip wrapper = null;
            if (copy != null) {
                if (track != null) {
                    wrapper = prepareWrapper(copy, track.getActorTrackPayload().getColorRgba());
                }
            }
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
        var positions = placementPositions(model, group, preview.eligible(), base, yaw);
        float time = model.getCurrentTime();
        List<ActorGroupEditCommand.Change> changes = new ArrayList<>();
        for (UUID id : preview.eligible()) {
            var track = resolveTrack(model, id);
            var payload = track.getActorTrackPayload();
            var formation = positions.stream().filter(p -> p.actorId().equals(id)).findFirst().orElseThrow();
            var position = formation.position();
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
                clip.setYawDeg(formation.yaw()); clip.setPitchDeg(pitch);
                if (existing == null && !payload.getClips().contains(clip)) payload.addClip(clip);
                clip.setTimelineClip(wrapper);
                if (addedWrapper && !track.getClips().contains(wrapper)) track.addClip(wrapper);
            }));
        }
        ActorGroupEditCommand.execute(model, "Place actor group", changes);
        return changes.size();
    }

    private List<ActorGroupController.FormationPosition> placementPositions(TimelineModel model, ActorGroup group,
            List<UUID> members, Vec3d base, float yaw) {
        if (group.getFormationType() != ActorGroup.FormationType.FREE)
            return GROUP_CONTROLLER.calculateFormation(group, base, yaw);
        // Keep existing offsets relative to the first placed, eligible member.
        var spawns = new java.util.LinkedHashMap<UUID, EntitySpawnClip>();
        for (UUID id : members) {
            var spawn = findSpawnClipAt(Objects.requireNonNull(resolveTrack(model, id)).getActorTrackPayload(), model.getCurrentTime());
            if (spawn != null) spawns.put(id, spawn);
        }
        Vector3f anchor = spawns.isEmpty() ? new Vector3f() : new Vector3f(spawns.values().iterator().next().getPosition());
        List<ActorGroupController.FormationPosition> result = new ArrayList<>();
        int unplaced = 0;
        for (UUID id : members) {
            var spawn = spawns.get(id);
            Vec3d position;
            if (spawn != null) {
                var offset = new Vector3f(spawn.getPosition()).sub(anchor);
                position = base.add(offset.x, offset.y, offset.z);
            } else {
                // Append new members beyond the existing formation's world-X extent.
                double edge = spawns.values().stream().mapToDouble(p -> p.getPosition().x - anchor.x).max().orElse(-group.getFormationSpacing());
                position = base.add(edge + (++unplaced) * group.getFormationSpacing(), 0, 0);
            }
            result.add(new ActorGroupController.FormationPosition(id, position, yaw));
        }
        return result;
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

    public List<ActorRouteClip> availableRoutes(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) return List.of();
        var track = resolveTrack(model, actorId);
        if (track == null || track.isMuted()) return List.of();
        return track.getActorTrackPayload().getClips().stream()
                .filter(c -> c instanceof ActorRouteClip && !c.isMuted()).map(c -> (ActorRouteClip)c)
                .sorted(java.util.Comparator.comparing(ActorRouteClip::getStartTime)).toList();
    }

    private ActorRouteClip resolveRoute(TimelineModel model, UUID actorId, UUID routeId) {
        if (routeId == null) return findLatestRouteClip(model, actorId);
        return availableRoutes(model, actorId).stream().filter(c -> c.getId().equals(routeId)).findFirst().orElse(null);
    }

    public ActorRouteClip findLatestRouteClip(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) return null;
        TimelineTrack timelineTrack = resolveTrack(model, actorId);
        if (timelineTrack == null || timelineTrack.getActorTrackPayload() == null) {
            return null;
        }

        float currentTime = model.getCurrentTime();
        ActorRouteClip latestBeforeOrAtPlayhead = null;
        ActorRouteClip earliestFuture = null;
        for (EntityClip clip : timelineTrack.getActorTrackPayload().getClips()) {
            if (!(clip instanceof ActorRouteClip routeClip) || routeClip.isMuted()) {
                continue;
            }
            if (routeClip.getStartTime() > currentTime && (earliestFuture == null || routeClip.getStartTime() < earliestFuture.getStartTime())) {
                earliestFuture = routeClip;
            }
            if (routeClip.getStartTime() <= currentTime
                    && (latestBeforeOrAtPlayhead == null
                    || routeClip.getStartTime() > latestBeforeOrAtPlayhead.getStartTime())) {
                latestBeforeOrAtPlayhead = routeClip;
            }
        }
        return latestBeforeOrAtPlayhead != null ? latestBeforeOrAtPlayhead : earliestFuture;
    }

    private static float followDelay(ActorGroup group, ActorRouteClip source, UUID sourceActorId, UUID memberId) {
        if (group.getFormationType() != ActorGroup.FormationType.FOLLOW_LEADER) return 0;
        int rank = group.getActorIds().indexOf(memberId);
        int sourceRank = group.getActorIds().indexOf(sourceActorId);
        // When the source is a member, reserve the first interval behind that leader.
        if (sourceRank < 0) rank = Math.max(0, rank);
        else rank = (int) group.getActorIds().stream().filter(id -> !id.equals(sourceActorId))
                .takeWhile(id -> !id.equals(memberId)).count() + 1;
        float speed = source.getSpeed();
        if (source.getLoopMode() == ActorRouteClip.LoopMode.ONCE) {
            float length = com.director.timeline.playback.entity.ActorRouteSampler.computeCurveLength(source.getWaypoints(), source.getInterpolationMode());
            speed = length / Math.max(.0001f, source.getLength());
        }
        return rank * group.getFormationSpacing() / Math.max(.05f, speed);
    }

    private ActorRouteClip copyRouteClip(ActorRouteClip source, float startDelaySeconds, Vector3f offset) {
        float start = source.getStartTime() + Math.max(0.0f, startDelaySeconds);
        float end = source.getEndTime() + Math.max(0.0f, startDelaySeconds);
        ActorRouteClip copy = new ActorRouteClip(source.getName(), start, end);
        copy.setSpeed(source.getSpeed());
        copy.setInterpolationMode(source.getInterpolationMode());
        copy.setLoopMode(source.getLoopMode());
        copy.setSmoothTravel(source.isSmoothTravel());
        copy.setEndpointTurnDuration(source.getEndpointTurnDuration());
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
        return actorTrack == null ? null : actorTrack.findSpawnClipAt(timeSeconds);
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
