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
        source = active(payload, time);
        before = source == null ? null : State.of(source);
        draft = new EntityLookAtClip("LookAt", source == null ? time : source.getStartTime(),
                source == null ? time + 1 : source.getEndTime());
        if (before != null) before.apply(draft);
        if (!isCurrent()) throw new IllegalArgumentException("演员注视目标当前不可编辑");
    }

    public static EntityLookAtClip active(ActorTrack payload, float time) {
        EntityLookAtClip best = null;
        for (var c : payload.getClips()) {
            if (c instanceof EntityLookAtClip look && !look.isMuted()
                    && time >= look.getStartTime() && time <= look.getEndTime()
                    && (best == null || look.getStartTime() >= best.getStartTime())) best = look;
        }
        return best;
    }

    public boolean isCurrent() {
        return !finished && contextRevision == model.getActorEditContextRevision() && !model.isPlaying() && Float.isFinite(time) && time >= 0
                && Float.compare(time, model.getCurrentTime()) == 0
                && model.getAllTracks().contains(owner) && owner.getType() == TrackType.ACTOR
                && !owner.isLocked() && !owner.isMuted()
                && owner.getActorTrackPayload() == payload && payload.getActorRef() != null
                && Objects.equals(actorId, payload.getActorRef().getActorId())
                && ActorRegistryManager.getInstance().getActor(actorId).isPresent()
                && active(payload, time) == source
                && (source == null || (before.equals(State.of(source))
                    && source.getTimelineClip() != null && owner.getClips().contains(source.getTimelineClip())));
    }

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
                    "Edit actor look target", () -> before.apply(source), () -> after.apply(source)));
            return source;
        }
        var wrapper = new EntityTimelineClip(draft, payload.getColorRgba());
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Create actor look target", () -> {
            owner.removeClip(wrapper); payload.removeClip(draft.getId());
        }, () -> {
            payload.addClip(draft);
            try { owner.addClip(wrapper); }
            catch (RuntimeException failure) {
                owner.removeClip(wrapper); payload.removeClip(draft.getId()); throw failure;
            }
        }));
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
