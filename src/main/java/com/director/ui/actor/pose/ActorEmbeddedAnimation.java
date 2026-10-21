package com.director.ui.actor.pose;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.ActorRegistryManager;
import com.director.ui.timeline.commands.*;
import java.util.*;

/** Spawn owns the animation reference; no extra visible timeline clip is authored. */
public final class ActorEmbeddedAnimation {
    private ActorEmbeddedAnimation() {}
    public static List<EntityClip> clips(ActorTrack payload) {
        var clips=new ArrayList<EntityClip>(payload.getClips());
        for(var c:payload.getClips())if(c instanceof EntitySpawnClip spawn && spawn.getBoneAnimation()!=null)clips.add(spawn.getBoneAnimation());
        return clips;
    }
    /** Resolve derived animation references back to the existing visible owner; never create a wrapper. */
    public static EntityTimelineClip visibleOwner(TimelineTrack track,EntityAnimationClip animation) {
        if(track==null || animation==null || track.getActorTrackPayload()==null) return null;
        EntityClip owner=null;
        for(var clip:track.getActorTrackPayload().getClips()) {
            if(clip==animation || clip instanceof EntitySpawnClip spawn && spawn.getBoneAnimation()==animation) { owner=clip;break; }
        }
        var wrapper=owner==null?null:owner.getTimelineClip();
        return wrapper!=null && wrapper.getParentTrack()==track && track.getClips().contains(wrapper)?wrapper:null;
    }
    public static boolean bound(ActorTrack payload,EntityAnimationClip clip) {
        return clips(payload).contains(clip);
    }
    public static EntitySpawnClip target(TimelineModel model,TimelineTrack track) {
        if(model==null || track==null || track.getActorTrackPayload()==null)return null;
        return resolveSpawn(model,track);
    }
    private static EntitySpawnClip resolveSpawn(TimelineModel model,TimelineTrack track) {
        var spawn=com.director.ui.actor.gizmo.ActorTransformEditSession.resolveInitialSpawn(track.getActorTrackPayload(),model.getCurrentTime());
        return spawn!=null && ActorRootKeyframeEditing.editable(model,track,spawn)?spawn:null;
    }
    public static boolean firstKey(TimelineModel model,TimelineTrack owner,String bone,int channel,InterpolationMode interpolation) {
        if(channel!=1 && channel!=2 && channel!=4)return false;
        var spawn=target(model,owner);if(spawn==null || spawn.getBoneAnimationId()!=null)return false;
        var actor=ActorRegistryManager.getInstance().getActor(owner.getActorTrackPayload().getActorRef().getActorId()).orElse(null);
        var rig=actor==null?null:ActorRigCompatibility.resolveRig(actor).orElse(null);
        if(rig==null || rig.findBoneIndex(bone)<0)return false;
        var definition=rig.bone(rig.requireBoneIndex(bone));var caps=definition.capabilities();
        if(channel==2 && com.director.actor.rig.skeleton.HumanoidTopology.matches(rig) && com.director.actor.rig.skeleton.HumanoidRig.ROOT.equals(bone))return false;
        if(channel==1&&!caps.translation() || channel==2&&!caps.rotation() || channel==4&&!caps.scale())return false;
        String id="project:actor_"+UUID.randomUUID();
        var asset=new AnimationAsset(id,spawn.getName()+" 骨骼动作",rig,Math.max(.1f,spawn.getLength()),AnimationLoopMode.ONCE);
        var value=asset.sample(0,AnimationLoopMode.ONCE).localTransform(bone);var track=asset.getOrCreateBoneTrack(bone);
        float time=model.getCurrentTime()-spawn.getStartTime();
        if(channel==1)track.translation().put(time,value.translation(),interpolation);
        else if(channel==2)track.rotation().put(time,value.rotation(),interpolation);
        else track.scale().put(time,value.scale(),interpolation);
        var library=AnimationAssetLibrary.getInstance();
        TimelineCommandBridge.execute(new ActorAnimationAssetCommand(model,"First actor bone keyframe",
                ()->{if(!model.getAllTracks().contains(owner) || owner.isLocked() || owner.isMuted() || model.isPlaying() || model.isScrubbing() || !owner.getActorTrackPayload().getClips().contains(spawn) || spawn.getBoneAnimationId()!=null)throw new IllegalArgumentException("演员动画目标已变化");},
                List.of(new ActorAnimationAssetCommand.Change(()->library.remove(id),()->{if(!library.addIfAbsent(asset))throw new IllegalArgumentException("动画资源冲突");}),
                        new ActorAnimationAssetCommand.Change(()->spawn.setBoneAnimationId(null),()->spawn.setBoneAnimationId(id)))));
        return true;
    }
}
