package com.director.ui.actor.pose;

import com.director.actor.ActorRegistryManager;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.ui.timeline.commands.*;
import java.util.*;

/** Explicit root-key authoring never creates a new clip or track. Times are scene seconds. */
public final class ActorRootKeyframeEditing {
    private ActorRootKeyframeEditing() {}
    public static boolean editable(TimelineModel model, TimelineTrack owner, EntityClip clip) {
        return model != null && owner != null && clip != null && !model.isPlaying() && !model.isScrubbing()
                && Float.isFinite(model.getCurrentTime()) && model.getAllTracks().contains(owner)
                && owner.getType() == TrackType.ACTOR && !owner.isLocked() && !owner.isMuted() && !clip.isMuted()
                && (clip instanceof EntitySpawnClip || clip instanceof EntityTransformClip)
                && owner.getActorTrackPayload() != null && owner.getActorTrackPayload().getActorRef() != null
                && ActorRegistryManager.getInstance().contains(owner.getActorTrackPayload().getActorRef().getActorId())
                && owner.getActorTrackPayload().getClips().contains(clip)
                && clip.getTimelineClip() != null && owner.getClips().contains(clip.getTimelineClip())
                && model.getCurrentTime() >= clip.getStartTime() && model.getCurrentTime() <= clip.getEndTime();
    }
    public static EntityClipKeyframe insert(TimelineModel model, TimelineTrack owner, EntityClip clip) {
        if (!editable(model, owner, clip)) return null;
        float time = model.getCurrentTime();
        var matching = clip.getKeyframes().stream().filter(k -> Math.abs(k.getTime() - time) <= 1e-4f).toList();
        if (!matching.isEmpty()) return matching.size() == 1 ? matching.getFirst() : null;
        List<EntityClipKeyframe> added = new ArrayList<>();
        // Keep the endpoint animation when converting an endpoint transform to keyframes.
        if (clip instanceof EntityTransformClip && clip.getKeyframes().isEmpty()) {
            added.add(sample(clip, clip.getStartTime()));
            if (clip.getEndTime() > clip.getStartTime()) added.add(sample(clip, clip.getEndTime()));
        }
        var key = added.stream().filter(k -> Math.abs(k.getTime() - time) <= 1e-4f).findFirst().orElse(null);
        if (key == null) { key = sample(clip, time); added.add(key); }
        for (var value : added) if (!valid(value)) return null;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Insert actor root keyframe",
                () -> added.forEach(k -> clip.removeKeyframe(k.getId())),
                () -> added.forEach(k -> { if (!clip.getKeyframes().contains(k)) clip.addKeyframe(k); })));
        return key;
    }
    private static EntityClipKeyframe sample(EntityClip clip, float time) {
        var payload = new ActorTrack(); payload.addClip(clip);
        var value = new EntityClipSampler(payload).sampleAt(time);
        var key = new EntityClipKeyframe(time, clip.getInterpolation());
        key.setPosition(value.position.x, value.position.y, value.position.z);
        key.setRotation(value.rotation.x, value.rotation.y, value.rotation.z);
        key.setScale(value.scale.x, value.scale.y, value.scale.z);
        return key;
    }
    private static boolean valid(EntityClipKeyframe key) {
        return key.getPosition().isFinite() && key.getRotation().isFinite() && key.getScale().isFinite()
                && key.getScale().x > 0 && key.getScale().y > 0 && key.getScale().z > 0;
    }
    public static boolean remove(TimelineModel model, TimelineTrack owner, EntityClip clip) {
        if (!editable(model, owner, clip)) return false;
        var matching = clip.getKeyframes().stream().filter(k -> Math.abs(k.getTime() - model.getCurrentTime()) <= 1e-4f).toList();
        if (matching.size() != 1) return false;
        var key = matching.getFirst();
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Remove actor root keyframe",
                () -> clip.addKeyframe(key), () -> clip.removeKeyframe(key.getId())));
        return true;
    }
}
