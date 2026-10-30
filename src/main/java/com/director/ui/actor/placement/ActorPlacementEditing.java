package com.director.ui.actor.placement;

import com.director.actor.Actor;
import com.director.actor.*;
import com.director.actor.query.ActorUsageQuery;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.actor.pose.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import java.util.*;

/** Creation and relocation keep actor identity and commit one undoable change. */
public final class ActorPlacementEditing {
    private ActorPlacementEditing() {}
    private static UUID failedActor;
    private static String failure="";
    public static void reportFailure(UUID actor,String message) {failedActor=actor;failure=message==null?"放置失败":message;}
    public static String failure(UUID actor) {return Objects.equals(actor,failedActor)?failure:"";}
    private static void editable(TimelineModel model,ActorRegistry registry) {
        if(model==null || model.isPlaying() || model.isScrubbing() || registry!=ActorRegistryManager.getInstance()
                || !Float.isFinite(model.getCurrentTime()) || model.getCurrentTime()<0)
            throw new IllegalArgumentException("请先暂停播放并选择当前工程");
    }
    public static TimelineTrack create(TimelineModel model,ActorRegistry registry,Actor actor,Vector3f position,Vector3f rotation) {
        editable(model,registry);
        if(registry.contains(actor.getActorId())) throw new IllegalArgumentException("演员已存在，请使用重新放置");
        return attach(model,registry,actor,position,rotation,true);
    }
    private static TimelineTrack attach(TimelineModel model,ActorRegistry registry,Actor actor,Vector3f position,Vector3f rotation,boolean createActor) {
        if(!position.isFinite() || !rotation.isFinite()) throw new IllegalArgumentException("演员位置无效");
        var owner=new TimelineTrack(actor.getName(),TrackType.ACTOR);
        var payload=new ActorTrack();payload.setActorRef(new ActorRef(actor.getActorId()));payload.setDisplayName(actor.getName());owner.setActorTrackPayload(payload);
        var spawn=new EntitySpawnClip("Spawn",model.getCurrentTime(),model.getCurrentTime()+5);
        spawn.setPosition(position.x,position.y,position.z);spawn.setYawDeg(rotation.x);spawn.setPitchDeg(rotation.y);spawn.setRollDeg(rotation.z);
        payload.addClip(spawn);owner.addClip(new EntityTimelineClip(spawn,payload.getColorRgba()));
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Place actor",()->{
            editable(model,registry);
            if(registry.getActor(actor.getActorId()).orElse(null)!=actor || !model.getAllTracks().contains(owner)
                    || ActorUsageQuery.findTimelineTracksUsingActor(model,actor.getActorId()).stream().anyMatch(t->t!=owner))
                throw new IllegalArgumentException("演员引用已变化，无法撤销放置");
            model.removeTrack(owner);if(createActor) registry.removeActor(actor.getActorId());ActorRegistryManager.save();
        },()->{
            editable(model,registry);
            if(createActor?registry.contains(actor.getActorId()):registry.getActor(actor.getActorId()).orElse(null)!=actor)
                throw new IllegalArgumentException("演员身份已变化");
            if(!ActorUsageQuery.findTimelineTracksUsingActor(model,actor.getActorId()).isEmpty()) throw new IllegalArgumentException("演员已有轨道");
            if(createActor) registry.registerActor(actor);
            try {model.addTrack(owner);} catch(RuntimeException failure) {if(createActor) registry.removeActor(actor.getActorId());throw failure;}
            ActorRegistryManager.save();
        }));return owner;
    }
    public static void relocate(TimelineModel model,ActorRegistry registry,Actor actor,Vector3f position,Vector3f rotation) {
        editable(model,registry);
        if(registry.getActor(actor.getActorId()).orElse(null)!=actor) throw new IllegalArgumentException("演员已变化");
        if(ActorUsageQuery.findTimelineTracksUsingActor(model,actor.getActorId()).isEmpty()) {attach(model,registry,actor,position,rotation,false);return;}
        var owner=ActorAnimationAssetEditing.resolveActorTrack(model,actor.getActorId());
        if(owner.isLocked() || owner.isMuted() || !position.isFinite() || !rotation.isFinite()) throw new IllegalArgumentException("演员轨道不可编辑");
        var sample=new com.director.timeline.playback.entity.EntityClipSampler(owner.getActorTrackPayload()).sampleSnapshot(model.getCurrentTime());
        if(sample.relationEnabled) throw new IllegalArgumentException("演员关系正在控制位置，请先停用关系再重新放置");
        if(ActorMovementSource.routeOwnsInterval(model,owner)) throw new IllegalArgumentException("路径正在控制演员位置，请先解除路径再重新放置");
        EntityClip source=com.director.ui.actor.gizmo.ActorTransformEditSession.resolveSource(model,owner);
        if(source==null) source=ActorEmbeddedAnimation.target(model,owner);
        if(source==null) throw new IllegalArgumentException("请将播放头移到演员的可编辑片段内");
        if(ActorRootKeyframeEditing.writeChannels(model,owner,source,3,position,rotation,new Vector3f(1))==null)
            throw new IllegalArgumentException("当前整体位置不可编辑，请先完成片段转换");
        reportFailure(actor.getActorId(),"");
    }
}

