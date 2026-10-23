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
        if(route.hasEndpointTurns()) return stopTurn(route,localTime).travelledDistance();
        if(route.getLoopMode()==ActorRouteClip.LoopMode.ONCE) return once(route,localTime).distance();
        if(ActorRouteSampler.lengthOf(route)<=1e-6f) return 0;
        return Math.max(0,localTime)*Math.max(.05f,route.getSpeed());
    }
    /** One eased travel leg followed by an in-place turn; no history or phase accumulation. */
    public record StopTurn(float curveDistance,float travelledDistance,float speed,boolean reversed,float turnProgress) {}
    public static StopTurn stopTurn(ActorRouteClip route,float localTime) {
        double length=ActorRouteSampler.lengthOf(route),speed=Math.max(.05f,route.getSpeed());
        if(length<=1e-6 || !Float.isFinite(localTime)) return new StopTurn(0,0,0,false,0);
        double travel=length/speed,turn=route.getEndpointTurnDuration(),block=travel+turn;
        double time=Math.max(0,localTime),leg=Math.floor(time/block),within=time-leg*block;
        boolean reversed=leg%2>=1;
        double t=Math.clamp(within/travel,0d,1d),progress=t*t*(3-2*t);
        double at=length*progress;
        float velocity=within>=travel?0:(float)(speed*6*t*(1-t));
        double q=turn<=0?0:Math.clamp((within-travel)/turn,0d,1d);
        return new StopTurn((float)(reversed?length-at:at),(float)(leg*length+at),velocity,reversed,(float)(q*q*(3-2*q)));
    }
    public static org.joml.Vector3f stopTurnFacing(ActorRouteClip route,StopTurn motion) {
        var direction=ActorRouteSampler.facingAtDistance(route,motion.curveDistance());
        if(motion.reversed()) direction.negate();
        if(motion.turnProgress()<=0) return direction;
        double yaw=Math.atan2(-direction.x,direction.z)+Math.PI*motion.turnProgress();
        double pitch=Math.atan2(-direction.y,Math.hypot(direction.x,direction.z))*(1-2*motion.turnProgress());
        return new org.joml.Vector3f((float)(-Math.sin(yaw)*Math.cos(pitch)),(float)-Math.sin(pitch),
                (float)(Math.cos(yaw)*Math.cos(pitch)));
    }
    /** Curve position and cumulative offset within this traversal are distinct quantities. */
    public record Traversal(float curveDistance,float travelInLeg,boolean reversed) {}
    public static Traversal traversalAt(ActorRouteClip route,float distance) {
        float length=ActorRouteSampler.lengthOf(route);
        if(!Float.isFinite(distance) || length<=1e-6f) return new Traversal(0,0,false);
        float travel=Math.max(0,distance);
        if(route.getLoopMode()==ActorRouteClip.LoopMode.ONCE) {
            float at=Math.min(length,travel);return new Traversal(at,at,false);
        }
        float offset=travel%length;
        boolean reversed=route.getLoopMode()==ActorRouteClip.LoopMode.PING_PONG && travel%(2d*length)>=length;
        return new Traversal(reversed?length-offset:offset,offset,reversed);
    }
}
