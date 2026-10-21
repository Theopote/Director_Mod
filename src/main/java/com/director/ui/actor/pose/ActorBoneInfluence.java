package com.director.ui.actor.pose;

import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.rig.skeleton.*;

/** Read-only explanation of one selected reference, not a claim about the final constrained pose. */
final class ActorBoneInfluence {
    enum Reason { ACTIVE, NO_KEYS, BONE_EXCLUDED, CHANNEL_EXCLUDED, ZERO_WEIGHT, FADE_ZERO }
    record Channel(boolean hasKeys,Reason reason,float configuredWeight) {}
    private ActorBoneInfluence() {}
    static Channel evaluate(AnimationAsset asset,PoseBlendSettings settings,String bone,int channel,float localTime,float duration) {
        var track=asset==null?null:asset.findBoneTrack(bone).orElse(null);
        boolean keyed=switch(channel) {
            case RigPose.TRANSLATION -> track!=null && !track.translation().isEmpty();
            case RigPose.ROTATION -> track!=null && !track.rotation().isEmpty();
            case RigPose.SCALE -> track!=null && !track.scale().isEmpty();
            default -> throw new IllegalArgumentException("Unknown bone channel");
        };
        if(!settings.includes(bone)) return new Channel(keyed,Reason.BONE_EXCLUDED,0);
        if((settings.channels()&channel)==0) return new Channel(keyed,Reason.CHANNEL_EXCLUDED,0);
        if(settings.weight()==0) return new Channel(keyed,Reason.ZERO_WEIGHT,0);
        float weight=settings.effectiveWeight(settings.envelopeAt(localTime,duration));
        if(weight==0) return new Channel(keyed,Reason.FADE_ZERO,0);
        return new Channel(keyed,keyed?Reason.ACTIVE:Reason.NO_KEYS,weight);
    }
}
