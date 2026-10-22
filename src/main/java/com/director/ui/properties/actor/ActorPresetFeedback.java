package com.director.ui.properties.actor;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorPresetBinding;
import com.director.timeline.playback.entity.ActorAnimationLayerSample;
import java.util.*;

/** Actual declared-channel influence before the upper manual layer is applied. */
public record ActorPresetFeedback(float weight,List<String> bones,boolean available) {
    public ActorPresetFeedback { bones=List.copyOf(bones); }
    public static ActorPresetFeedback resolve(ActorPresetBinding binding,RigDefinition rig,float local,float duration) {
        if(binding==null || rig==null) return new ActorPresetFeedback(0,List.of(),false);
        var blend=binding.blend();float envelope=blend.envelopeAt(local,duration);
        var pose=new ActorAnimationLayerSample(binding.animationId(),local,binding.loop(),binding.speed(),blend,envelope).sample(rig);
        if(pose==null || !RigPoseComposer.sameLayout(rig,pose.rig())) return new ActorPresetFeedback(0,List.of(),false);
        var bones=new ArrayList<String>();
        for(int i=0;i<pose.rig().boneCount();i++)
            if(blend.includes(pose.rig().bone(i).id()) && (blend.channels()&pose.authoredChannels(i))!=0)
                bones.add(pose.rig().bone(i).id());
        return new ActorPresetFeedback(bones.isEmpty()?0:blend.effectiveWeight(envelope),bones,true);
    }
}
