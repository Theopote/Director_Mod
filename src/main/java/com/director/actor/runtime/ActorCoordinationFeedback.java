package com.director.actor.runtime;

import com.director.timeline.playback.entity.EntitySample;
import java.util.*;

/** Immutable motion and stage metadata captured alongside the displayed pose. */
public record ActorCoordinationFeedback(Motion motion,String presetId,boolean distanceClock,List<ActorPoseStage> stages) {
    public record Motion(UUID routeId,String routeName,float duration,
                         com.director.timeline.model.entity.ActorRouteClip.LoopMode loop,
                         com.director.actor.action.ActorActionType action,float speed,boolean relation) {
        public static Motion capture(EntitySample sample) {
            var route=sample.routeClip;
            return new Motion(route==null?null:route.getId(),route==null?"":route.getName(),route==null?0:route.getLength(),
                    route==null?null:route.getLoopMode(),sample.action,sample.speed,sample.relationEnabled);
        }
    }
    public ActorCoordinationFeedback {Objects.requireNonNull(motion);stages=List.copyOf(stages);}
}
