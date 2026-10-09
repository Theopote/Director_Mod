package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import java.util.*;

/** One transient selection, scoped by project, owner, reference and asset identity. */
public final class ActorKeyframeSelection {
    private static final ActorKeyframeSelection INSTANCE=new ActorKeyframeSelection();
    public static ActorKeyframeSelection getInstance() { return INSTANCE; }
    public record Snapshot(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip,AnimationAsset asset,
                           List<ActorAnimationAuthoringService.Key> keys,boolean focused,long revision,ActorTrack payload,UUID actorId) {}
    private Snapshot current;
    private long revision;
    public long revision() { return revision; }
    public Snapshot snapshot(TimelineModel model) {
        if(current==null) return null;
        if(current.model()!=model) return null;
        var s=current; var payload=s.owner().getActorTrackPayload();
        if(!model.getAllTracks().contains(s.owner()) || s.owner().getType()!=TrackType.ACTOR
                || payload==null || payload!=s.payload() || payload.getActorRef()==null
                || !Objects.equals(payload.getActorRef().getActorId(),s.actorId()) || !ActorEmbeddedAnimation.bound(payload,s.clip())
                || model.getSelectedClip()!=s.clip().getTimelineClip()
                || s.clip().getTimelineClip()==null || s.clip().getTimelineClip().getParentTrack()!=s.owner()
                || !Objects.equals(s.clip().getAnimationId(),s.asset().id())
                || AnimationAssetLibrary.getInstance().find(s.asset().id()).orElse(null)!=s.asset()) {
            current=null; revision++; return null;
        }
        var valid=s.keys().stream().filter(k -> exists(s.asset(),k)).toList();
        if(!valid.equals(s.keys())) { replace(model,s.owner(),s.clip(),s.asset(),valid,s.focused()); }
        return current;
    }
    public void replace(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip,AnimationAsset asset,
                        Collection<ActorAnimationAuthoringService.Key> keys,boolean focused) {
        var unique=List.copyOf(new LinkedHashSet<>(keys));
        var payload=owner.getActorTrackPayload();
        UUID actorId=payload==null || payload.getActorRef()==null?null:payload.getActorRef().getActorId();
        if(current!=null && current.model()==model && current.owner()==owner && current.clip()==clip
                && current.asset()==asset && current.payload()==payload && Objects.equals(current.actorId(),actorId) && current.keys().equals(unique) && current.focused()==focused) return;
        current=new Snapshot(model,owner,clip,asset,unique,focused,++revision,payload,actorId);
    }
    private static boolean exists(AnimationAsset asset,ActorAnimationAuthoringService.Key k) {
        if(k==null || k.channel()==null || !Float.isFinite(k.time())) return false;
        var track=asset.findBoneTrack(k.boneId()).orElse(null); if(track==null) return false;
        return switch(k.channel()) {
            case TRANSLATION -> track.translation().findAt(k.time()).isPresent();
            case ROTATION -> track.rotation().findAt(k.time()).isPresent();
            case SCALE -> track.scale().findAt(k.time()).isPresent();
        };
    }
}
