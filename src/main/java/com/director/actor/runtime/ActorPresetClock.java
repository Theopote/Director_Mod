package com.director.actor.runtime;

import com.director.actor.animation.*;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.rig.ActorRigProfile;
import com.director.timeline.playback.entity.*;

/** Performance retains its clip clock; declared gait cycles can follow path distance. */
public final class ActorPresetClock {
    private ActorPresetClock() {}
    public static boolean followsDistance(String id,EntitySample sample) {
        return !AnimationAssetLibrary.getInstance().contains(id) && ActorRouteGait.followsDistance(sample)
                && AnimationLibrary.getInstance().get(id).map(a->a.purpose()==AnimationData.Purpose.LOCOMOTION).orElse(false);
    }
    public static ActorAnimationLayerSample resolve(ActorAnimationLayerSample input,EntitySample sample,ActorRigProfile profile) {
        if(input==null || profile==null || !followsDistance(input.id(),sample)) return input;
        var data=AnimationLibrary.getInstance().get(input.id()).orElseThrow();
        float cycles=ActorRouteGait.phaseCycles(sample,profile);
        if(!Float.isFinite(cycles) || !Float.isFinite(data.getDuration()) || data.getDuration()<=0) return input;
        float phase=(float)(cycles-Math.floor(cycles));
        return new ActorAnimationLayerSample(input.id(),phase*data.getDuration(),true,1,input.settings(),input.envelope());
    }
}
