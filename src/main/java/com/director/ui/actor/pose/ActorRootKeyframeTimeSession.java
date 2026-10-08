package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import java.util.*;

/** Detached root-key retiming. Rejects stale targets, collisions and out-of-range writes. */
public final class ActorRootKeyframeTimeSession {
    private record Stamp(EntityClipKeyframe key, float time, TimelineInterpolation interpolation,
                         Vector3f position, Vector3f rotation, Vector3f scale) {
        static Stamp of(EntityClipKeyframe k) { return new Stamp(k,k.getTime(),k.getInterpolation(),
                new Vector3f(k.getPosition()),new Vector3f(k.getRotation()),new Vector3f(k.getScale())); }
    }
    private final TimelineModel model;
    private final TimelineTrack owner;
    private final EntityClip clip;
    private final EntityClipKeyframe key;
    private final ActorTrack payload;
    private final UUID actorId;
    private final List<Stamp> keys;
    private final float start, end, playhead, originalTime;
    private final long revision;
    private boolean finished;
    public ActorRootKeyframeTimeSession(TimelineModel model, TimelineTrack owner, EntityClip clip, EntityClipKeyframe key) {
        this.model = Objects.requireNonNull(model); this.owner = Objects.requireNonNull(owner);
        this.clip = Objects.requireNonNull(clip); this.key = Objects.requireNonNull(key);
        payload = owner.getActorTrackPayload();
        actorId = payload == null || payload.getActorRef() == null ? null : payload.getActorRef().getActorId();
        keys = clip.getKeyframes().stream().map(Stamp::of).toList();
        start = clip.getStartTime(); end = clip.getEndTime(); playhead = model.getCurrentTime();
        originalTime = key.getTime(); revision = model.getActorEditContextRevision();
        if (!isCurrent()) throw new IllegalArgumentException("整体关键帧当前不可编辑");
    }
    public boolean isCurrent() {
        return !finished && model.getActorEditContextRevision() == revision
                && model.getSelectedClip() == clip.getTimelineClip() && model.getCurrentTime() == playhead
                && owner.getActorTrackPayload() == payload && payload != null && payload.getActorRef() != null
                && Objects.equals(actorId, payload.getActorRef().getActorId())
                && clip.getStartTime() == start && clip.getEndTime() == end
                && ActorRootKeyframeEditing.editable(model, owner, clip) && clip.getKeyframes().contains(key)
                && keys.equals(clip.getKeyframes().stream().map(Stamp::of).toList());
    }
    public TimelineModel model() { return model; }
    public EntityClipKeyframe key() { return key; }
    public EntityClip clip() { return clip; }
    public float originalTime() { return originalTime; }
    public float start() { return start; }
    public float end() { return end; }
    public void cancel() { finished = true; }
    public boolean commit(float time) {
        if (!isCurrent() || !Float.isFinite(time) || time < start || time > end
                || Math.abs(time - originalTime) <= 1e-4f
                || clip.getKeyframes().stream().anyMatch(k -> k != key && Math.abs(k.getTime() - time) <= 1e-4f)) {
            cancel(); return false;
        }
        finished = true;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Move actor root keyframe",
                () -> { place(originalTime); model.setCurrentTime(playhead); },
                () -> { place(time); model.setCurrentTime(time); }));
        return true;
    }
    private void place(float time) {
        clip.removeKeyframe(key.getId()); key.setTime(time); clip.addKeyframe(key);
    }
}
