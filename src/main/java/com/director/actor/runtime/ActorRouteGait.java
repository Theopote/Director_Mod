package com.director.actor.runtime;

import com.director.actor.action.ActorActionType;
import com.director.actor.rig.ActorRigProfile;
import com.director.timeline.playback.entity.*;

/** Distance phase belongs to locomotion, action/animation local time belongs to performance. */
public final class ActorRouteGait {
    private ActorRouteGait() {}
    public static boolean followsDistance(EntitySample sample) {
        return sample.routeClip!=null && (sample.action==ActorActionType.WALK || sample.action==ActorActionType.RUN);
    }
    /** Bounded start/stop pose release; never changes distance, speed, or the gait phase. */
    public static float motionWeight(EntitySample sample) {
        if(sample.routeClip==null || sample.routeClip.getLoopMode()!=com.director.timeline.model.entity.ActorRouteClip.LoopMode.ONCE) return 1;
        float duration=sample.routeClip.getLength(),local=sample.routeLocalTime;
        if(!Float.isFinite(duration) || duration<=0 || !Float.isFinite(local) || local<=0 || local>=duration) return 0;
        float length=ActorRouteSampler.lengthOf(sample.routeClip);
        if(!Float.isFinite(length) || length<=1e-6f) return 0;
        float ramp=Math.min(.2f,duration*.25f);
        float boundary=Math.clamp(Math.min(local/ramp,(duration-local)/ramp),0f,1f);
        float speedRatio=ActorRouteMotion.once(sample.routeClip,local).speed()/(length/duration);
        float t=Math.min(boundary,Math.clamp(speedRatio,0f,1f));
        return t*t*(3-2*t);
    }
    public static com.director.actor.rig.Pose settle(com.director.actor.rig.Pose pose,EntitySample sample,ActorRigProfile profile) {
        if(!followsDistance(sample)) return pose;
        float weight=motionWeight(sample);
        if(weight>=1) return pose;
        var idle=profile.buildPose(ActorActionType.IDLE,1,0).pose;
        return com.director.actor.rig.Pose.lerp(idle,pose,weight);
    }
    public static float stride(EntitySample sample,ActorRigProfile profile) {
        float stride=profile.locomotionStride()*sample.scale.z;
        return Float.isFinite(stride) && stride>.0001f?stride:Float.NaN;
    }
    public static float phaseCycles(EntitySample sample,ActorRigProfile profile) {
        float stride=stride(sample,profile);
        return Float.isFinite(stride)?ActorRouteMotion.distanceTravelled(sample.routeClip,sample.routeLocalTime)/stride:0;
    }
    public static float time(EntitySample sample,ActorRigProfile profile) {
        if(!followsDistance(sample)) return sample.actionExplicit?sample.actionLocalTime:sample.motionLocalTime;
        float frequency=profile.locomotionFrequency(sample.action,sample.actionIntensity);
        if(!Float.isFinite(frequency) || Math.abs(frequency)<.001f) return 0;
        return phaseCycles(sample,profile)*(float)(2*Math.PI)/frequency;
    }
}
