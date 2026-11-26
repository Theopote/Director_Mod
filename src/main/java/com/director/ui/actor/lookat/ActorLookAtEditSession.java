package com.director.ui.actor.lookat;

import com.director.actor.ActorRegistryManager;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import java.util.Objects;
import java.util.UUID;
import org.joml.Vector3f;

/** A detached look target draft tied to one actor, owner and playhead. */
public final class ActorLookAtEditSession {
    private final TimelineModel model;
    private final TimelineTrack owner;
    private final ActorTrack payload;
    private final UUID actorId;
    private final float time;
    private final EntityLookAtClip source;
    private final EntitySpawnClip spawn;
    private final float spawnStart, spawnEnd;
    private final State before;
    private final EntityLookAtClip draft;
    private final long contextRevision;
    private boolean finished;

    public ActorLookAtEditSession(TimelineModel model, TimelineTrack owner) {
        this.model = Objects.requireNonNull(model);
        contextRevision = model.getActorEditContextRevision();
        this.owner = Objects.requireNonNull(owner);
        payload = Objects.requireNonNull(owner.getActorTrackPayload());
        actorId = Objects.requireNonNull(payload.getActorRef()).getActorId();
        time = model.getCurrentTime();
        spawn = resolveSpawn(model, owner);
        if (spawn == null) throw new IllegalArgumentException("请将播放头移到有效的出场片段内");
        spawnStart = spawn.getStartTime();
        spawnEnd = spawn.getEndTime();
        source = resolveSource(model,owner);
        before = source == null ? null : State.of(source);
        draft = new EntityLookAtClip("LookAt", source == null ? time : source.getStartTime(),
                source == null ? spawnEnd : source.getEndTime());
        if (before != null) before.apply(draft);
        if (!isCurrent()) throw new IllegalArgumentException("演员注视目标当前不可编辑");
    }

    public static EntityLookAtClip resolveSource(TimelineModel model,TimelineTrack owner) {
        var spawn = resolveSpawn(model, owner);
        return spawn == null ? null : spawn.findLookTargetAt(model.getCurrentTime());
    }

    public static EntitySpawnClip resolveSpawn(TimelineModel model, TimelineTrack owner) {
        if (model == null || owner == null || owner.getActorTrackPayload() == null) return null;
        var payload = owner.getActorTrackPayload();
        float time = model.getCurrentTime();
        if (!payload.isPresentAt(time)) return null;
        var spawn = payload.findSpawnClipAt(time);
        var selected = selectedSpawn(model, owner);
        return (selected == null || selected == spawn) && spawn.getTimelineClip() != null
                && owner.getClips().contains(spawn.getTimelineClip()) ? spawn : null;
    }

    private static EntitySpawnClip selectedSpawn(TimelineModel model, TimelineTrack owner) {
        if (model.getSelectedClip() instanceof EntityTimelineClip wrapper && wrapper.getParentTrack() == owner
                && wrapper.getEntityClip() instanceof EntitySpawnClip selected) return selected;
        return null;
    }

    public static EntityLookAtClip active(ActorTrack payload, float time) {
        return payload.findLookAtClipAt(time);
    }

    public boolean isCurrent() {
        return !finished && contextRevision == model.getActorEditContextRevision() && !model.isPlaying() && !model.isScrubbing() && Float.isFinite(time) && time >= 0
                && Float.compare(time, model.getCurrentTime()) == 0
                && model.getAllTracks().contains(owner) && owner.getType() == TrackType.ACTOR
                && !owner.isLocked() && !owner.isMuted()
                && owner.getActorTrackPayload() == payload && payload.getActorRef() != null
                && Objects.equals(actorId, payload.getActorRef().getActorId())
                && ActorRegistryManager.getInstance().getActor(actorId).isPresent()
                && resolveSpawn(model, owner) == spawn
                && spawn.getStartTime() == spawnStart && spawn.getEndTime() == spawnEnd
                && resolveSource(model, owner) == source
                && (source == null || !source.isMuted() && before.equals(State.of(source)));
    }

    EntityTimelineClip selectionFor() { return spawn.getTimelineClip(); }

    public EntityLookAtClip draft() { return draft; }
    public EntityLookAtClip source() { return source; }
    public TimelineModel model() { return model; }
    public TimelineTrack owner() { return owner; }
    public UUID actorId() { return actorId; }
    public void cancel() { finished = true; }

    public EntityLookAtClip commit() {
        if (!isCurrent()) { cancel(); return null; }
        State after = State.of(draft);
        if (!after.valid()) { cancel(); return null; }
        finished = true;
        if (source != null) {
            if (!before.equals(after)) TimelineCommandBridge.execute(new StateChangeCommand(model,
                    "Edit actor look target", () -> spawn.editLookTarget(source, before::apply),
                    () -> spawn.editLookTarget(source, after::apply)));
            return source;
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Create actor look target",
                () -> spawn.removeLookTarget(draft), () -> spawn.addLookTarget(draft)));
        return draft;
    }

    private record State(EntityLookAtClip.TargetMode mode, Vector3f point, UUID target,
                         float strength, float start, float end, boolean muted) {
        static State of(EntityLookAtClip clip) {
            return new State(clip.getMode(), new Vector3f(clip.getWorldPoint()), clip.getTargetEntityUuid(),
                    clip.getStrength(), clip.getStartTime(), clip.getEndTime(), clip.isMuted());
        }
        boolean valid() { return point.isFinite() && Float.isFinite(strength)
                && Float.isFinite(start) && Float.isFinite(end) && start >= 0 && end >= start; }
        void apply(EntityLookAtClip clip) {
            clip.setMode(mode); clip.setWorldPoint(point.x, point.y, point.z);
            clip.setTargetEntityUuid(target); clip.setStrength(strength);
            clip.setTimeRange(start, end); clip.setMuted(muted);
        }
    }
}
