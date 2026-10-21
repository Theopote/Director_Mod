package com.director.timeline.playback.entity;
import com.director.timeline.model.entity.ActorRouteClip;
/** Arc-distance and derivative, independent of render history. */
public final class ActorRouteMotion {
    private ActorRouteMotion() {}
    public record Motion(float distance, float speed) {}
    public static Motion once(ActorRouteClip route, float localTime) {
        float duration = Math.max(.0001f, route.getLength());
        float t = Math.clamp(localTime / duration, 0f, 1f);
        float length = ActorRouteSampler.lengthOf(route);
        float progress = route.isSmoothTravel() ? t*t*(3-2*t) : t;
        float derivative = route.isSmoothTravel() ? 6*t*(1-t) : 1;
        float speed = localTime < 0 || localTime >= duration ? 0 : length / duration * derivative;
        return new Motion(length * progress, speed);
    }
    /** Cumulative travel, continuous through loop wraps and ping-pong turns. */
    public static float distanceTravelled(ActorRouteClip route,float localTime) {
        if(route.getLoopMode()==ActorRouteClip.LoopMode.ONCE) return once(route,localTime).distance();
        if(ActorRouteSampler.lengthOf(route)<=1e-6f) return 0;
        return Math.max(0,localTime)*Math.max(.05f,route.getSpeed());
    }
}
