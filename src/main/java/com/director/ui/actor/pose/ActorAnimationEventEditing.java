package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntityActionEvent;
import com.director.actor.prop.PropEventParameters;
import com.director.ui.timeline.commands.*;
import java.util.*;

/** Detached event-list edits are one checked undo command. */
public final class ActorAnimationEventEditing {
    private ActorAnimationEventEditing() {}
    public static List<AnimationEvent> copy(List<AnimationEvent> events) {
        return events.stream().map(e->new AnimationEvent(e.getNormalizedTime(),new EntityActionEvent(e.getAction().type(),
                PropEventParameters.copy(e.getAction().params(),false)),e.getTiming())).toList();
    }
    public static boolean editable(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip) {
        return model!=null&&!model.isPlaying()&&!model.isScrubbing()&&owner!=null&&model.getAllTracks().contains(owner)
                &&!owner.isLocked()&&!owner.isMuted()&&!clip.isMuted()&&owner.getActorTrackPayload()!=null
                &&ActorEmbeddedAnimation.bound(owner.getActorTrackPayload(),clip);
    }
    public static void replace(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip,List<AnimationEvent> expected,List<AnimationEvent> events) {
        if(!editable(model,owner,clip)) throw new IllegalArgumentException("动画事件当前不可编辑");
        var before=copy(expected);var after=copy(events).stream().sorted(Comparator.comparing(AnimationEvent::getNormalizedTime)).toList();
        if(!clip.getEvents().equals(before)) throw new IllegalArgumentException("事件已变化，请重新加载");
        if(before.equals(after)) return;
        var actor=com.director.actor.ActorRegistryManager.getInstance().getActor(owner.getActorTrackPayload().getActorRef().getActorId()).orElse(null);
        for(var event:after) {
            if(!Float.isFinite(event.getNormalizedTime())) throw new IllegalArgumentException("事件时间无效");
            if(!before.contains(event)) PropEventParameters.validateActor(actor,event.getAction().type(),event.getAction().params());
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Edit actor animation events",
                ()->apply(model,owner,clip,after,before),()->apply(model,owner,clip,before,after)));
    }
    private static void apply(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip,List<AnimationEvent> expected,List<AnimationEvent> next) {
        if(!editable(model,owner,clip)||!clip.getEvents().equals(expected)) throw new IllegalArgumentException("事件或目标已变化");
        clip.clearEvents();copy(next).forEach(clip::addEvent);
    }
}
