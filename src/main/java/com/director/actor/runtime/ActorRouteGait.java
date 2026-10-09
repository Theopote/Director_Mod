package com.director.actor.runtime;

import com.director.actor.action.ActorActionType;
import com.director.timeline.playback.entity.*;
import com.director.timeline.model.entity.ActorRouteClip;

/** Constant-speed route cadence, evaluated from timeline time without frame accumulation. */
public final class ActorRouteGait {
    private ActorRouteGait() {}
    public static float time(EntitySample sample) {
        if (sample.actionExplicit || sample.routeClip == null
                || (sample.action != ActorActionType.WALK && sample.action != ActorActionType.RUN)) return sample.actionLocalTime;
        var route = sample.routeClip;
        float length = ActorRouteSampler.computeCurveLength(route.getWaypoints(), route.getInterpolationMode());
        float speed = route.getLoopMode() == ActorRouteClip.LoopMode.ONCE
                ? length / Math.max(.0001f, route.getLength()) : route.getSpeed();
        float reference = sample.action == ActorActionType.RUN ? 4f : 2f;
        return sample.actionLocalTime * Math.max(.05f, Math.min(4f, speed / reference));
    }
}
