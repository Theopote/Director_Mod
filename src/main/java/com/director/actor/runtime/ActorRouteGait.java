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
    /** Semantic labels stay discrete; automatic visual gait crosses the threshold continuously. */
    public static float runWeight(float speed) {
        float t=Math.clamp((speed-3f)/2f,0f,1f);return t*t*(3-2*t);
    }
    private static boolean automaticBlend(EntitySample sample,ActorRigProfile profile) {
        return followsDistance(sample) && !sample.actionExplicit && Float.isFinite(sample.speed)
                && profile.supports(ActorActionType.WALK) && profile.supports(ActorActionType.RUN);
    }
    private static float walkIntensity(float speed) { return Math.clamp((speed-.02f)/(4f-.02f)*.5f+.3f,.3f,.8f); }
    private static float runIntensity(float speed) { return Math.clamp((speed-4f)/4f*.5f+.5f,.5f,1f); }
    private static float phaseTime(EntitySample sample,ActorRigProfile profile,ActorActionType action,float intensity) {
        float frequency=profile.locomotionFrequency(action,intensity);
        return Float.isFinite(frequency) && Math.abs(frequency)>.001f?phaseCycles(sample,profile)*(float)(2*Math.PI)/frequency:0;
    }
    public static com.director.actor.rig.RigPoseResult sample(EntitySample sample,ActorRigProfile profile,
            ActorRuntime runtime) {
        var action=sample.action==null?ActorActionType.IDLE:sample.action;
        var result=profile.sampleAt(runtime,action,sample.actionIntensity,time(sample,profile));
        if(!automaticBlend(sample,profile)) return result;
        float walk=walkIntensity(sample.speed),run=runIntensity(sample.speed);
        var a=profile.buildPose(ActorActionType.WALK,walk,phaseTime(sample,profile,ActorActionType.WALK,walk)).pose;
        var b=profile.buildPose(ActorActionType.RUN,run,phaseTime(sample,profile,ActorActionType.RUN,run)).pose;
        return new com.director.actor.rig.RigPoseResult(com.director.actor.rig.Pose.lerp(a,b,runWeight(sample.speed)),result.mask);
    }
    public static void applyJoints(com.director.actor.rig.skeleton.RigPose pose,EntitySample sample,
            ActorRigProfile profile,boolean arms,boolean legs) {
        if(automaticBlend(sample,profile)) ActorJointGait.applyAutomatic(pose,
                phaseCycles(sample,profile)*(float)(2*Math.PI),walkIntensity(sample.speed),runIntensity(sample.speed),
                runWeight(sample.speed),profile.getStyle(),arms,legs,motionWeight(sample));
        else ActorJointGait.apply(pose,sample.action,sample.actionIntensity,time(sample,profile),profile,arms,legs,motionWeight(sample));
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
