package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;

/** Shared reference ownership for numeric editing, Gizmo and timeline inspection. */
public final class ActorAnimationTarget {
    private ActorAnimationTarget() {}

    public static EntityAnimationClip resolve(TimelineModel model,TimelineTrack owner) {
        if(model==null || owner==null || owner.getActorTrackPayload()==null) return null;
        var payload=owner.getActorTrackPayload();
        var selected=model.getSelectedClip();
        if(selected instanceof EntityTimelineClip wrapper && wrapper.getParentTrack()==owner
                && owner.getClips().contains(wrapper)) {
            if(wrapper.getEntityClip() instanceof EntityAnimationClip animation && payload.getClips().contains(animation))
                return animation;
            if(wrapper.getEntityClip() instanceof EntitySpawnClip spawn && payload.getClips().contains(spawn)
                    && spawn.getBoneAnimation()!=null) return spawn.getBoneAnimation();
        }
        return active(payload,model.getCurrentTime());
    }

    public static EntityAnimationClip active(ActorTrack payload,float time) {
        EntitySpawnClip spawn=null;
        EntityAnimationClip animation=null;
        for(var clip:payload.getClips()) {
            if(clip.isMuted() || time<clip.getStartTime() || time>clip.getEndTime()) continue;
            if(clip instanceof EntitySpawnClip next && (spawn==null || next.getStartTime()>=spawn.getStartTime())) spawn=next;
            if(clip instanceof EntityAnimationClip next && (animation==null || next.getStartTime()>=animation.getStartTime())) animation=next;
        }
        return spawn!=null && spawn.getBoneAnimation()!=null?spawn.getBoneAnimation():animation;
    }
}
