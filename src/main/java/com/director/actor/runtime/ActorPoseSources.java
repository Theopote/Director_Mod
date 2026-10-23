package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import java.util.*;

/** Channel attribution collected inside the solver, never by rerunning it in the UI. */
public final class ActorPoseSources {
    public record Channel(String bone, int channel) {}
    private final Map<Channel,String> values = new HashMap<>();
    public Map<Channel,String> snapshot() { return Map.copyOf(values); }
    public void layer(ActorPoseEvaluator.Layer layer, String label) {
        if (layer == null) return;
        var pose = layer.pose(); var settings = layer.settings();
        float weight = settings.effectiveWeight(layer.envelope());
        for (int i=0;i<pose.rig().boneCount();i++) {
            String bone=pose.rig().bone(i).id();
            if (!settings.contributes(pose,i,layer.envelope())) continue;
            for (int channel : new int[]{1,2,4}) {
                if ((pose.authoredChannels(i)&settings.channels()&channel)==0) continue;
                var key=new Channel(bone,channel);
                if (weight==1 && settings.mode()==PoseLayer.BlendMode.OVERRIDE) values.put(key,label);
                else values.put(key,String.format(Locale.ROOT,"%s（%s %.0f%%）＋%s",label,
                        settings.mode()==PoseLayer.BlendMode.ADDITIVE?"叠加":"混合",weight*100,
                        values.getOrDefault(key,"基础姿态")));
            }
        }
    }
    public static BoneTransform[] capture(RigPose pose) {
        var result=new BoneTransform[pose.rig().boneCount()];
        for(int i=0;i<result.length;i++) result[i]=pose.localTransform(i);
        return result;
    }
    public void changed(BoneTransform[] before,RigPose after,String label) {
        for(int i=0;i<before.length;i++) {
            var a=before[i];var b=after.localTransform(i);String bone=after.rig().bone(i).id();
            if(!a.translation().equals(b.translation(),1e-6f)) values.put(new Channel(bone,1),label);
            if(1-Math.abs(a.rotation().dot(b.rotation()))>1e-7f) values.put(new Channel(bone,2),label);
            if(!a.scale().equals(b.scale(),1e-6f)) values.put(new Channel(bone,4),label);
        }
    }
}
