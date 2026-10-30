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
        float duration, AnimationLoopMode loopMode, long contextRevision, EntityTimelineClip wrapper, com.director.actor.rig.skeleton.PoseBlendSettings blendSettings, ActorPresetEditState presetState, boolean distanceDriven) {
    private static final ActorInspectorPlayback clockPlayback=new ActorInspectorPlayback();

    public static ActorAnimationEditContext resolve(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) return null;
        TimelineTrack owner;
        try { owner=ActorAnimationAssetEditing.resolveActorTrack(model,actorId); }
        catch(IllegalArgumentException ambiguous) { return null; }
        return capture(model, owner, ActorAnimationTarget.resolve(model,owner));
    }

    public static ActorAnimationEditContext capture(TimelineModel model, TimelineTrack track,
                                                   EntityAnimationClip clip) {
        if (model == null || track == null || clip == null) return null;
        var payload = track.getActorTrackPayload();
        var asset = AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
        if (payload == null || payload.getActorRef() == null || asset == null) return null;
        var actor=ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
        var sample=com.director.actor.runtime.ActorPresetClock.purpose(clip.getAnimationId())==com.director.actor.animation.AnimationData.Purpose.LOCOMOTION
                ?clockPlayback.sample(payload,model.getCurrentTime()):null;
        var input=new com.director.timeline.playback.entity.ActorAnimationLayerSample(clip.getAnimationId(),
                Math.max(0,model.getCurrentTime()-clip.getStartTime()),clip.isLoop(),clip.getSpeed(),clip.getBlendSettings(),1);
        boolean distance=actor!=null && actor.getRigProfile()!=null
                && com.director.actor.runtime.ActorPresetClock.followsDistance(clip.getAnimationId(),sample);
        var clock=com.director.actor.runtime.ActorPresetClock.resolve(input,sample,actor==null?null:actor.getRigProfile());
        float local=asset.normalizeTime(clock.localTime()*Math.max(.01f,clock.speed()),
                clock.loop()?asset.loopMode():AnimationLoopMode.ONCE);
        return new ActorAnimationEditContext(model, payload.getActorRef().getActorId(), track, payload,
                clip, asset, model.getCurrentTime(), local, clip.getStartTime(), clip.getEndTime(),
                clip.getSpeed(), clip.isLoop(), asset.duration(), asset.loopMode(),model.getActorEditContextRevision(),clip.getTimelineClip(),clip.getBlendSettings(),ActorPresetEditState.capture(track),distance);
    }

    public boolean isCurrent() {
        return isBound() && presetState.equals(ActorPresetEditState.capture(track)) && Objects.equals(blendSettings,clip.getBlendSettings()) && Float.isFinite(timelineTime) && timelineTime>=0
                && contextRevision==model.getActorEditContextRevision()
                && Float.compare(model.getCurrentTime(), timelineTime)==0 && timelineTime>=start && timelineTime<=end
                && (!distanceDriven || Float.compare(assetTime,capture(model,track,clip).assetTime())==0);
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
                    .map(actor -> ActorRigCompatibility.accepts(actor, asset.rigId())).orElse(false);
    }
}
