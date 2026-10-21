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
