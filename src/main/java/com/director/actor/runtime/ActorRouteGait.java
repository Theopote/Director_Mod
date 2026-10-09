package com.director.actor.runtime;

import com.director.actor.action.ActorActionType;
import com.director.timeline.playback.entity.*;
import com.director.timeline.model.entity.ActorRouteClip;

/** Constant-speed route cadence, evaluated from timeline time without frame accumulation. */
public final class ActorRouteGait {
    private ActorRouteGait() {}
    public static float playerTime(EntitySample sample, com.director.actor.rig.RigProfileStyle style) {
        if (sample.actionExplicit || sample.routeClip == null || sample.routeClip.getLoopMode() != ActorRouteClip.LoopMode.ONCE
                || (sample.action != ActorActionType.WALK && sample.action != ActorActionType.RUN)) return time(sample);
        float distance = ActorRouteMotion.once(sample.routeClip, sample.actionLocalTime).distance();
        float phase = distance * (float)(2 * Math.PI) / (.8f * Math.max(.001f, sample.scale.z));
        float frequency = (2 + sample.actionIntensity) * style.tempo * (sample.action == ActorActionType.RUN ? 2 : 1);
        return Math.abs(frequency) < .001f ? 0 : phase / frequency;
    }
    public static float time(EntitySample sample) {
        if (sample.actionExplicit || sample.routeClip == null
                || (sample.action != ActorActionType.WALK && sample.action != ActorActionType.RUN)) return sample.actionLocalTime;
        var route = sample.routeClip;
        float length = ActorRouteSampler.computeCurveLength(route.getWaypoints(), route.getInterpolationMode());
        float speed = route.getLoopMode() == ActorRouteClip.LoopMode.ONCE
                ? length / Math.max(.0001f, route.getLength()) : route.getSpeed();
        float reference = sample.action == ActorActionType.RUN ? 4f : 2f;
        if (route.getLoopMode() == ActorRouteClip.LoopMode.ONCE)
            return ActorRouteMotion.once(route, sample.actionLocalTime).distance() / reference;
        return sample.actionLocalTime * speed / reference;
    }
}
