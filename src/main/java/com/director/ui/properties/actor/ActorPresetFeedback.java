package com.director.ui.properties.actor;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorPresetBinding;
import com.director.timeline.playback.entity.ActorAnimationLayerSample;
import java.util.*;

/** Actual declared-channel influence before the upper manual layer is applied. */
public record ActorPresetFeedback(float weight,List<String> bones,boolean available,Set<String> rotationBones) {
    public ActorPresetFeedback { bones=List.copyOf(bones);rotationBones=Set.copyOf(rotationBones); }
    public static ActorPresetFeedback resolve(ActorPresetBinding binding,RigDefinition rig,float local,float duration) {
        return resolve(binding,rig,local,duration,null,null);
    }
    public static ActorPresetFeedback resolve(ActorPresetBinding binding,RigDefinition rig,float local,float duration,
            com.director.timeline.playback.entity.EntitySample sample,com.director.actor.rig.ActorRigProfile profile) {
        if(binding==null || rig==null) return new ActorPresetFeedback(0,List.of(),false,Set.of());
        var blend=binding.blend();float envelope=blend.envelopeAt(local,duration);
        var input=new ActorAnimationLayerSample(binding.animationId(),local,binding.loop(),binding.speed(),blend,envelope);
        var clock=sample==null?input:com.director.actor.runtime.ActorPresetClock.resolve(input,sample,profile);
        envelope=clock.envelope();
        var pose=clock.sample(rig);
        if(pose==null || !RigPoseComposer.sameLayout(rig,pose.rig())) return new ActorPresetFeedback(0,List.of(),false,Set.of());
        var bones=new ArrayList<String>();
        var rotations=new HashSet<String>();
        for(int i=0;i<pose.rig().boneCount();i++) {
            String bone=pose.rig().bone(i).id();
            if(blend.includes(bone) && (blend.channels()&pose.authoredChannels(i))!=0) {
                bones.add(bone);
                if((blend.channels()&pose.authoredChannels(i)&RigPose.ROTATION)!=0) rotations.add(bone);
            }
        }
        return new ActorPresetFeedback(bones.isEmpty()?0:blend.effectiveWeight(envelope),bones,true,rotations);
    }
}
