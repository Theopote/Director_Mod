package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import java.util.UUID;

/** Read-only detail resolution. Never falls back to a stale world selection or creates data. */
public record ActorAnimationDetailTarget(TimelineTrack track, UUID actorId, Actor actor,
        EntityAnimationClip clip, AnimationAsset asset, Status status) {
    public enum Status { SELECT, AMBIGUOUS, UNBOUND, MISSING_ACTOR, UNSUPPORTED_RIG,
        NO_ANIMATION, MISSING_ASSET, INCOMPATIBLE, OUTSIDE, READ_ONLY, READY }

    private static ActorAnimationDetailTarget empty(Status status) {
        return new ActorAnimationDetailTarget(null,null,null,null,null,status);
    }
    public static ActorAnimationDetailTarget resolve(TimelineModel model) {
        if(model==null) return empty(Status.SELECT);
        var owners=model.getSelectedItems().stream().map(item -> item instanceof TimelineTrack t ? t
                : item instanceof TimelineClip c ? c.getParentTrack() : null)
                .filter(t -> t!=null && model.getAllTracks().contains(t)).distinct().toList();
        if(owners.size()>1) return empty(Status.AMBIGUOUS);
        if(owners.isEmpty() || owners.getFirst().getType()!=TrackType.ACTOR) return empty(Status.SELECT);
        var track=owners.getFirst(); var payload=track.getActorTrackPayload();
        if(payload==null || payload.getActorRef()==null || payload.getActorRef().getActorId()==null)
            return new ActorAnimationDetailTarget(track,null,null,null,null,Status.UNBOUND);
        UUID id=payload.getActorRef().getActorId();
        var actor=ActorRegistryManager.getInstance().getActor(id).orElse(null);
        EntityAnimationClip clip=null;
        // An explicit reference stays the inspection target even outside its interval or when muted.
        if(model.getSelectedClip() instanceof EntityTimelineClip wrapper && wrapper.getParentTrack()==track
                && wrapper.getEntityClip() instanceof EntityAnimationClip animation
                && payload.getClips().contains(animation) && track.getClips().contains(wrapper)) clip=animation;
        if(clip==null) for(var candidate:payload.getClips()) {
            if(candidate instanceof EntityAnimationClip animation && !animation.isMuted()
                    && model.getCurrentTime()>=animation.getStartTime() && model.getCurrentTime()<=animation.getEndTime()
                    && (clip==null || animation.getStartTime()>=clip.getStartTime())) clip=animation;
        }
        var asset=clip==null ? null : AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
        Status status=actor==null ? Status.MISSING_ACTOR
                : ActorRigCompatibility.resolveRig(actor).isEmpty() ? Status.UNSUPPORTED_RIG
                : clip==null ? Status.NO_ANIMATION
                : asset==null ? Status.MISSING_ASSET
                : !ActorRigCompatibility.accepts(actor,asset.rigId()) ? Status.INCOMPATIBLE
                : model.getCurrentTime()<clip.getStartTime() || model.getCurrentTime()>clip.getEndTime() ? Status.OUTSIDE
                : track.isLocked() || track.isMuted() || clip.isMuted() || model.isPlaying() || model.isScrubbing()
                    ? Status.READ_ONLY : Status.READY;
        return new ActorAnimationDetailTarget(track,id,actor,clip,asset,status);
    }
}
