package com.director.actor.runtime;

import com.director.actor.animation.*;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.rig.ActorRigProfile;
import com.director.timeline.playback.entity.*;

/** Performance retains its clip clock; declared gait cycles can follow path distance. */
public final class ActorPresetClock {
    private ActorPresetClock() {}
    public static AnimationData.Purpose purpose(String id) {
        return AnimationAssetLibrary.getInstance().find(id).map(a->a.purpose())
                .orElseGet(()->AnimationLibrary.getInstance().get(id).map(AnimationData::purpose).orElse(AnimationData.Purpose.PERFORMANCE));
    }
    public static boolean followsDistance(String id,EntitySample sample) {
        return sample!=null && sample.routeClip!=null && purpose(id)==AnimationData.Purpose.LOCOMOTION
                && (ActorRouteGait.followsDistance(sample) || !sample.actionExplicit
                    && sample.action==com.director.actor.action.ActorActionType.IDLE);
    }
    public static ActorAnimationLayerSample resolve(ActorAnimationLayerSample input,EntitySample sample,ActorRigProfile profile) {
        if(input==null || profile==null || !followsDistance(input.id(),sample)) return input;
        float duration=AnimationAssetLibrary.getInstance().find(input.id()).map(a->a.duration())
                .orElseGet(()->AnimationLibrary.getInstance().get(input.id()).map(a->a.getDuration()).orElse(0f));
        float cycles=ActorRouteGait.phaseCycles(sample,profile);
        if(!Float.isFinite(cycles) || !Float.isFinite(duration) || duration<=0) return input;
        float phase=(float)(cycles-Math.floor(cycles));
        return new ActorAnimationLayerSample(input.id(),phase*duration,true,1,input.settings(),input.envelope()*ActorRouteGait.motionWeight(sample));
    }
}
