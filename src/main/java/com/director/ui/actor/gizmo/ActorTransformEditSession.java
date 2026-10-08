package com.director.ui.actor.gizmo;

import com.director.actor.ActorRegistryManager;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.joml.Vector3f;

/** Detached whole-actor transform gesture; source data changes only on commit. */
public final class ActorTransformEditSession {
    private final TimelineModel model;
    private final TimelineTrack owner;
    private final ActorTrack payload;
    private final UUID actorId;
    private final float time;
    private final EntityTransformClip source;
    private final State before;
    private final EntitySpawnClip initialSpawn;
    private final SpawnState spawnBefore;
    private final List<EntityClip> sourceClips;
    private final List<KeyStamp> sourceKeys;
    private final EntityTransformClip draft;
    private State initial;
    private final long contextRevision;
    private boolean finished;

    public ActorTransformEditSession(TimelineModel model, TimelineTrack owner,
                                     EntityTransformClip source) {
        this.model = Objects.requireNonNull(model);
        contextRevision = model.getActorEditContextRevision();
        this.owner = Objects.requireNonNull(owner);
        payload = Objects.requireNonNull(owner.getActorTrackPayload());
        actorId = Objects.requireNonNull(payload.getActorRef()).getActorId();
        time = model.getCurrentTime();
        this.source = source;
        initialSpawn = source == null ? resolveInitialSpawn(payload, time) : null;
        spawnBefore = initialSpawn == null ? null : SpawnState.of(initialSpawn);
        before = source == null ? null : State.of(source);
        sourceKeys = source == null ? List.of() : keys(source);
        sourceClips = payload.getClips();
        draft = new EntityTransformClip("Transform", time, time + 1);
        if (before != null) before.apply(draft);
        initial = State.of(draft);
        if (!isCurrent()) throw new IllegalArgumentException("演员变换当前不可编辑");
    }

    public boolean isCurrent() {
        return !finished && contextRevision == model.getActorEditContextRevision() && !model.isPlaying() && Float.isFinite(time) && time >= 0
                && Float.compare(time, model.getCurrentTime()) == 0
                && model.getAllTracks().contains(owner) && owner.getType() == TrackType.ACTOR
                && !owner.isLocked() && !owner.isMuted() && owner.getActorTrackPayload() == payload
                && payload.getActorRef() != null && Objects.equals(actorId, payload.getActorRef().getActorId())
                && ActorRegistryManager.getInstance().getActor(actorId).isPresent()
                && sourceClips.equals(payload.getClips())
                && (initialSpawn == null || resolveInitialSpawn(payload, time) == initialSpawn && spawnBefore.equals(SpawnState.of(initialSpawn)))
                && resolveSource(model, owner) == source
                && (source == null || (!source.isMuted() && payload.getClips().contains(source)
                    && source.getTimelineClip() != null && owner.getClips().contains(source.getTimelineClip())
                    && before.matches(source) && keysAreCurrent()));
    }

    public EntityTransformClip draft() { return draft; }
    public TimelineModel model() { return model; }
    public TimelineTrack owner() { return owner; }
    public UUID actorId() { return actorId; }
    /** Call after initializing sampled values, before the first pointer delta. */
    public void arm() { initial = State.of(draft); }
    public void cancel() { finished = true; }

    /** An explicit transform reference remains the target even outside its time interval. */
    public static EntityTransformClip resolveSource(TimelineModel model, TimelineTrack owner) {
        var selected = model.getSelectedClip();
        if (selected instanceof EntityTimelineClip wrapper && owner.getClips().contains(wrapper)
                && wrapper.getEntityClip() instanceof EntityTransformClip transform) {
            return transform; // Keep the target; isCurrent rejects a muted source instead of creating a replacement.
        }
        return resolveSource(owner.getActorTrackPayload(), model.getCurrentTime());
    }

    public static EntityTransformClip resolveSource(ActorTrack payload, float time) {
        EntityTransformClip active = null;
        EntityTransformClip nearest = null;
        float nearestDistance = Float.POSITIVE_INFINITY;
        for (var c : payload.getClips()) {
            if (!(c instanceof EntityTransformClip transform) || transform.isMuted()) continue;
            if (time >= transform.getStartTime() && time <= transform.getEndTime()) {
                if (active == null || transform.getStartTime() >= active.getStartTime()) active = transform;
            } else {
                float distance = time < transform.getStartTime() ? transform.getStartTime() - time : time - transform.getEndTime();
                if (distance < nearestDistance) { nearest = transform; nearestDistance = distance; }
            }
        }
        return active == null ? nearest : active;
    }

    private static EntitySpawnClip resolveInitialSpawn(ActorTrack payload, float time) {
        EntitySpawnClip result = null;
        for (var clip : payload.getClips()) {
            if (clip instanceof EntitySpawnClip spawn && !spawn.isMuted()
                    && time >= spawn.getStartTime()
                    && (result == null || spawn.getStartTime() >= result.getStartTime())) result = spawn;
        }
        return result;
    }
    private record SpawnState(Vector3f position, Vector3f rotation, Vector3f scale) {
        static SpawnState of(EntitySpawnClip clip) {
            return new SpawnState(new Vector3f(clip.getPosition()),
                    new Vector3f(clip.getYawDeg(), clip.getPitchDeg(), clip.getRollDeg()), new Vector3f(clip.getScale()));
        }
        void apply(EntitySpawnClip clip) {
            clip.setPosition(position.x, position.y, position.z);
            clip.setYawDeg(rotation.x); clip.setPitchDeg(rotation.y); clip.setRollDeg(rotation.z);
            clip.setScale(scale.x, scale.y, scale.z);
        }
    }

    public EntityTransformClip commit() {
        if (!isCurrent()) { cancel(); return null; }
        State after = State.of(draft);
        finished = true;
        if (!after.valid() || initial.equals(after)) return null;
        if (source != null) {
            if (!sourceKeys.isEmpty()) return commitKey(after);
            if (before.equals(after)) return null;
            TimelineCommandBridge.execute(new StateChangeCommand(model, "Transform actor",
                    () -> before.apply(source), () -> after.apply(source)));
            return source;
        }
        if (initialSpawn != null) {
            var updated = new SpawnState(new Vector3f(after.toPos), new Vector3f(after.toRot), new Vector3f(after.toScale));
            if (updated.equals(spawnBefore)) return null;
            TimelineCommandBridge.execute(new StateChangeCommand(model, "Adjust actor initial transform",
                    () -> spawnBefore.apply(initialSpawn), () -> updated.apply(initialSpawn)));
            return draft; // Detached gesture result; no new timeline reference was created.
        }
        var wrapper = new EntityTimelineClip(draft, payload.getColorRgba());
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Transform actor", () -> {
            owner.removeClip(wrapper); payload.removeClip(draft.getId());
        }, () -> {
            payload.addClip(draft);
            try {
                if (!owner.addClip(wrapper)) throw new IllegalStateException("Cannot attach actor transform clip");
            }
            catch (RuntimeException failure) {
                owner.removeClip(wrapper); payload.removeClip(draft.getId()); throw failure;
            }
        }));
        return draft;
    }

    private EntityTransformClip commitKey(State after) {
        var matches = source.getKeyframes().stream().filter(key -> Math.abs(key.getTime() - time) <= 1e-4f).toList();
        if (matches.size() > 1) return null;
        EntityClipKeyframe existing = matches.isEmpty() ? null : matches.getFirst();
        var sample = new com.director.timeline.playback.entity.EntityClipSampler(payload).sampleAt(time);
        var target = existing == null ? new EntityClipKeyframe(time, source.getInterpolation()) : existing;
        var oldPosition = new Vector3f(target.getPosition());
        var oldRotation = new Vector3f(target.getRotation());
        var oldScale = new Vector3f(target.getScale());
        boolean positionChanged = !initial.fromPos.equals(after.fromPos) || !initial.toPos.equals(after.toPos);
        boolean rotationChanged = !initial.fromRot.equals(after.fromRot) || !initial.toRot.equals(after.toRot);
        boolean scaleChanged = !initial.fromScale.equals(after.fromScale) || !initial.toScale.equals(after.toScale);
        var position = positionChanged ? after.toPos : existing == null ? new Vector3f(sample.position) : oldPosition;
        var rotation = rotationChanged ? after.toRot : existing == null ? new Vector3f(sample.rotation) : oldRotation;
        var scale = scaleChanged ? after.toScale : existing == null ? new Vector3f(sample.scale) : oldScale;
        if (!position.isFinite() || !rotation.isFinite() || !State.validScale(scale)) return null;
        if (existing != null && position.equals(oldPosition) && rotation.equals(oldRotation) && scale.equals(oldScale)
                && before.start == after.start && before.end == after.end) return null;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Transform actor keyframe", () -> {
            before.apply(source);
            if (existing == null) source.removeKeyframe(target.getId());
            else writeKey(target, oldPosition, oldRotation, oldScale);
        }, () -> {
            source.setTimeRange(after.start, after.end);
            writeKey(target, position, rotation, scale);
            if (existing == null && !source.getKeyframes().contains(target)) source.addKeyframe(target);
        }));
        return source;
    }

    private static void writeKey(EntityClipKeyframe key, Vector3f position, Vector3f rotation, Vector3f scale) {
        key.setPosition(position.x, position.y, position.z);
        key.setRotation(rotation.x, rotation.y, rotation.z);
        key.setScale(scale.x, scale.y, scale.z);
    }

    private record KeyStamp(EntityClipKeyframe key, float time, TimelineInterpolation interpolation,
                            Vector3f position, Vector3f rotation, Vector3f scale) {}
    private static List<KeyStamp> keys(EntityTransformClip clip) {
        return clip.getKeyframes().stream().map(key -> new KeyStamp(key, key.getTime(), key.getInterpolation(),
                new Vector3f(key.getPosition()), new Vector3f(key.getRotation()), new Vector3f(key.getScale()))).toList();
    }

    private boolean keysAreCurrent() {
        var current = source.getKeyframes();
        if (current.size() != sourceKeys.size()) return false;
        for (int i = 0; i < current.size(); i++) {
            var key = current.get(i); var stamp = sourceKeys.get(i);
            if (key != stamp.key || key.getTime() != stamp.time || key.getInterpolation() != stamp.interpolation
                    || !stamp.position.equals(key.getPosition()) || !stamp.rotation.equals(key.getRotation())
                    || !stamp.scale.equals(key.getScale())) return false;
        }
        return true;
    }

    private record State(float start, float end, Vector3f fromPos, Vector3f toPos,
                         Vector3f fromRot, Vector3f toRot, Vector3f fromScale, Vector3f toScale,
                         TimelineInterpolation interpolation, String pathId, boolean followPath,
                         boolean muted, float weight) {
        static State of(EntityTransformClip clip) {
            return new State(clip.getStartTime(), clip.getEndTime(),
                    new Vector3f(clip.getFromPos()), new Vector3f(clip.getToPos()),
                    new Vector3f(clip.getFromRot()), new Vector3f(clip.getToRot()),
                    new Vector3f(clip.getFromScale()), new Vector3f(clip.getToScale()),
                    clip.getInterpolation(), clip.getPathId(), clip.isFollowPath(), clip.isMuted(), clip.getWeight());
        }
        boolean matches(EntityTransformClip clip) {
            return start == clip.getStartTime() && end == clip.getEndTime()
                    && fromPos.equals(clip.getFromPos()) && toPos.equals(clip.getToPos())
                    && fromRot.equals(clip.getFromRot()) && toRot.equals(clip.getToRot())
                    && fromScale.equals(clip.getFromScale()) && toScale.equals(clip.getToScale())
                    && interpolation == clip.getInterpolation() && Objects.equals(pathId, clip.getPathId())
                    && followPath == clip.isFollowPath() && muted == clip.isMuted() && weight == clip.getWeight();
        }
        boolean valid() {
            return Float.isFinite(start) && Float.isFinite(end) && start >= 0 && end >= start
                    && fromPos.isFinite() && toPos.isFinite() && fromRot.isFinite() && toRot.isFinite()
                    && validScale(fromScale) && validScale(toScale) && Float.isFinite(weight);
        }
        private static boolean validScale(Vector3f scale) {
            return scale.isFinite() && scale.x > 0 && scale.y > 0 && scale.z > 0;
        }
        void apply(EntityTransformClip clip) {
            clip.setTimeRange(start, end);
            clip.setFromPos(fromPos.x, fromPos.y, fromPos.z); clip.setToPos(toPos.x, toPos.y, toPos.z);
            clip.setFromRot(fromRot.x, fromRot.y, fromRot.z); clip.setToRot(toRot.x, toRot.y, toRot.z);
            clip.setFromScale(fromScale.x, fromScale.y, fromScale.z); clip.setToScale(toScale.x, toScale.y, toScale.z);
            clip.setInterpolation(interpolation); clip.setPathId(pathId); clip.setFollowPath(followPath);
            clip.setMuted(muted); clip.setWeight(weight);
        }
    }
}
