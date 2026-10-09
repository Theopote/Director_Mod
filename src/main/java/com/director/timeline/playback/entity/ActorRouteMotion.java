package com.director.timeline.playback.entity;
import com.director.timeline.model.entity.ActorRouteClip;
/** Arc-distance and derivative, independent of render history. */
public final class ActorRouteMotion {
    private ActorRouteMotion() {}
    public record Motion(float distance, float speed) {}
    public static Motion once(ActorRouteClip route, float localTime) {
        float duration = Math.max(.0001f, route.getLength());
        float t = Math.clamp(localTime / duration, 0f, 1f);
        float length = ActorRouteSampler.computeCurveLength(route.getWaypoints(), route.getInterpolationMode());
        float progress = route.isSmoothTravel() ? t*t*(3-2*t) : t;
        float derivative = route.isSmoothTravel() ? 6*t*(1-t) : 1;
        float speed = localTime < 0 || localTime >= duration ? 0 : length / duration * derivative;
        return new Motion(length * progress, speed);
    }
}
