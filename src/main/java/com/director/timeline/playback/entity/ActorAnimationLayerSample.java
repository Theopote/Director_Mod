package com.director.timeline.playback.entity;

import com.director.actor.animation.AnimationDataSampler;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;

/** Immutable clip-local animation input; never owns or edits an animation asset. */
public record ActorAnimationLayerSample(String id, float localTime, boolean loop, float speed,
                                       PoseBlendSettings settings, float envelope) {
    public static ActorAnimationLayerSample from(EntitySample sample) {
        return new ActorAnimationLayerSample(sample.animationId,sample.animationLocalTime,sample.animationLoop,
                sample.animationSpeed,sample.animationBlendSettings,sample.animationBlendEnvelope);
    }
    public RigPose sample(RigDefinition fallbackRig) {
        var asset=AnimationAssetLibrary.getInstance().find(id).orElse(null);
        if(asset==null) return AnimationDataSampler.sampleRig(id,fallbackRig,localTime,loop,speed);
        return asset.sample(localTime*Math.max(.01f,speed),loop?asset.loopMode():AnimationLoopMode.ONCE);
    }
}
