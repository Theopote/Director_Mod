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
    private final Map<EntityClipKeyframe, Float> moving;
    private boolean finished;
    public ActorRootKeyframeTimeSession(TimelineModel model, TimelineTrack owner, EntityClip clip, EntityClipKeyframe key) {
        this(model, owner, clip, key, List.of(key));
    }
    public ActorRootKeyframeTimeSession(TimelineModel model, TimelineTrack owner, EntityClip clip,
                                       EntityClipKeyframe key, Collection<EntityClipKeyframe> selection) {
        this.model = Objects.requireNonNull(model); this.owner = Objects.requireNonNull(owner);
        this.clip = Objects.requireNonNull(clip); this.key = Objects.requireNonNull(key);
        var frozen = new LinkedHashMap<EntityClipKeyframe, Float>();
        for (var selected : Objects.requireNonNull(selection)) frozen.put(selected, selected.getTime());
        moving = Collections.unmodifiableMap(frozen);
        if (!moving.containsKey(key) || !clip.getKeyframes().containsAll(moving.keySet())
                || moving.values().stream().anyMatch(t -> !Float.isFinite(t)))
            throw new IllegalArgumentException("无效的整体关键帧选择");
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
    public float start() { return originalTime + start - Collections.min(moving.values()); }
    public float end() { return originalTime + end - Collections.max(moving.values()); }
    public boolean contains(EntityClipKeyframe value) { return moving.containsKey(value); }
    public float previewTime(EntityClipKeyframe value, float anchorTime) {
        return moving.containsKey(value) ? moving.get(value) + anchorTime - originalTime : value.getTime();
    }
    public void cancel() { finished = true; }
    public boolean commit(float time) {
        float delta = time - originalTime;
        boolean invalid = !isCurrent() || !Float.isFinite(time) || Math.abs(delta) <= 1e-4f;
        for (var entry : moving.entrySet()) {
            float target = entry.getValue() + delta;
            if (!Float.isFinite(target) || target < start || target > end
                    || clip.getKeyframes().stream().anyMatch(k -> !moving.containsKey(k) && Math.abs(k.getTime() - target) <= 1e-4f)) invalid = true;
        }
        var times = new ArrayList<>(moving.values());
        for (int i = 0; i < times.size(); i++) for (int j = i + 1; j < times.size(); j++)
            if (Math.abs((times.get(i) + delta) - (times.get(j) + delta)) <= 1e-4f) invalid = true;
        if (invalid) { cancel(); return false; }
        finished = true;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Move actor root keyframe",
                () -> { place(0); model.setCurrentTime(playhead); },
                () -> { place(delta); model.setCurrentTime(time); }));
        return true;
    }
    private void place(float delta) {
        moving.keySet().forEach(k -> clip.removeKeyframe(k.getId()));
        moving.forEach((k, time) -> { k.setTime(time + delta); clip.addKeyframe(k); });
    }
}
