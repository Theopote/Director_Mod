package com.director.ui.actor.pose;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import java.util.Objects;
import java.util.UUID;

/** Frozen asset authoring target. Resolution never creates data or bypasses a locked owner. */
public record ActorAnimationEditContext(TimelineModel model, UUID actorId, TimelineTrack track,
        ActorTrack payload, EntityAnimationClip clip, AnimationAsset asset, float timelineTime,
        float assetTime, float start, float end, float speed, boolean loop,
        float duration, AnimationLoopMode loopMode, long contextRevision, EntityTimelineClip wrapper) {
    public static ActorAnimationEditContext resolve(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) return null;
        TimelineTrack owner;
        try { owner=ActorAnimationAssetEditing.resolveActorTrack(model,actorId); }
        catch(IllegalArgumentException ambiguous) { return null; }
        EntityAnimationClip active=null;
        float time=model.getCurrentTime();
        for(var candidate:ActorEmbeddedAnimation.clips(owner.getActorTrackPayload())) {
            if(candidate instanceof EntityAnimationClip animation && !animation.isMuted()
                    && time>=animation.getStartTime() && time<=animation.getEndTime()
                    && (active==null || animation.getStartTime()>=active.getStartTime())) active=animation;
        }
        // A clicked key belongs to a specific reference, even when another clip overlaps it.
        var selected = model.getSelectedClip();
        if (selected instanceof EntityTimelineClip wrapper && wrapper.getParentTrack() == owner
                && wrapper.getEntityClip() instanceof EntityAnimationClip animation
                && ActorEmbeddedAnimation.clips(owner.getActorTrackPayload()).contains(animation) && !animation.isMuted()
                && time >= animation.getStartTime() && time <= animation.getEndTime()) active = animation;
        return capture(model, owner, active);
    }

    public static ActorAnimationEditContext capture(TimelineModel model, TimelineTrack track,
                                                   EntityAnimationClip clip) {
        if (model == null || track == null || clip == null) return null;
        var payload = track.getActorTrackPayload();
        var asset = AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
        if (payload == null || payload.getActorRef() == null || asset == null) return null;
        float local = asset.normalizeTime((model.getCurrentTime() - clip.getStartTime())
                * Math.max(0.01f, clip.getSpeed()), clip.isLoop() ? asset.loopMode() : AnimationLoopMode.ONCE);
        return new ActorAnimationEditContext(model, payload.getActorRef().getActorId(), track, payload,
                clip, asset, model.getCurrentTime(), local, clip.getStartTime(), clip.getEndTime(),
                clip.getSpeed(), clip.isLoop(), asset.duration(), asset.loopMode(),model.getActorEditContextRevision(),clip.getTimelineClip());
    }

    public boolean isCurrent() {
        return isBound() && Float.isFinite(timelineTime) && timelineTime>=0
                && contextRevision==model.getActorEditContextRevision()
                && Float.compare(model.getCurrentTime(), timelineTime)==0 && timelineTime>=start && timelineTime<=end;
    }
    /** Command target checks deliberately ignore the gesture's playhead and selection version. */
    public boolean isBound() {
        return model!=null && !model.isPlaying() && !model.isScrubbing()
                && model.getAllTracks().contains(track) && track.getType()==TrackType.ACTOR && !track.isLocked() && !track.isMuted()
                && track.getActorTrackPayload() == payload && payload.getActorRef() != null
                && Objects.equals(actorId, payload.getActorRef().getActorId())
                && ActorEmbeddedAnimation.bound(payload,clip) && !clip.isMuted()
                && wrapper!=null && clip.getTimelineClip()==wrapper && wrapper.getParentTrack()==track
                && clip.getTimelineClip().getEntityClip()==clip && (track.getClips().contains(clip.getTimelineClip()) || ActorEmbeddedAnimation.bound(payload,clip))
                && clip.getStartTime() == start && clip.getEndTime() == end && clip.getSpeed() == speed
                && clip.isLoop() == loop && Objects.equals(clip.getAnimationId(), asset.id())
                && asset.duration() == duration && asset.loopMode() == loopMode
                && AnimationAssetLibrary.getInstance().find(asset.id()).orElse(null) == asset
                && ActorRegistryManager.getInstance().getActor(actorId)
                    .flatMap(ActorRigCompatibility::resolveRig)
                    .map(rig -> rig.id().equals(asset.rigId())).orElse(false);
    }
}
