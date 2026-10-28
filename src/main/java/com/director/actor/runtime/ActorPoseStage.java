package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import java.util.*;

/** A stage's accepted channel writes, not an estimate of its final visual contribution. */
public record ActorPoseStage(Kind kind,State state,Set<ActorPoseSources.Channel> channels,float weight,Detail detailCode,String detail) {
    public enum Kind { PRESET, QUICK, MANUAL, CONTACT }
    public enum State { ABSENT, DISABLED, EMPTY, REJECTED, APPLIED, UNCHANGED, UNSUPPORTED }
    public enum Detail { ABSENT, ZERO_WEIGHT, EMPTY, ADDITIVE, OVERRIDE, UNSUPPORTED_QUICK, QUICK_ABSENT,
        UNSUPPORTED_CONTACT, CONTACT_UNCHANGED, CONTACT_APPLIED, DIAGNOSTIC }
    public ActorPoseStage {channels=Set.copyOf(channels);Objects.requireNonNull(kind);Objects.requireNonNull(state);Objects.requireNonNull(detailCode);Objects.requireNonNull(detail);}
    public static ActorPoseStage layer(Kind kind,RigPose pose,PoseBlendSettings settings,float envelope,String rejection) {
        if(pose==null) return status(kind,State.ABSENT,Detail.ABSENT);
        if(rejection!=null) return status(kind,State.REJECTED,rejection);
        float weight=settings.effectiveWeight(envelope);
        if(weight<=0) return new ActorPoseStage(kind,State.DISABLED,Set.of(),weight,Detail.ZERO_WEIGHT,"");
        var channels=new HashSet<ActorPoseSources.Channel>();
        for(int i=0;i<pose.rig().boneCount();i++) if(settings.includes(pose.rig().bone(i).id()))
            for(int ch:new int[]{1,2,4}) if((pose.authoredChannels(i)&settings.channels()&ch)!=0)
                channels.add(new ActorPoseSources.Channel(pose.rig().bone(i).id(),ch));
        return new ActorPoseStage(kind,channels.isEmpty()?State.EMPTY:State.APPLIED,channels,weight,
                channels.isEmpty()?Detail.EMPTY:settings.mode()==PoseLayer.BlendMode.ADDITIVE?Detail.ADDITIVE:Detail.OVERRIDE,"");
    }
    public static ActorPoseStage status(Kind kind,State state,String detail) {return new ActorPoseStage(kind,state,Set.of(),Float.NaN,Detail.DIAGNOSTIC,detail);}
    public static ActorPoseStage status(Kind kind,State state,Detail detail) {return new ActorPoseStage(kind,state,Set.of(),Float.NaN,detail,"");}
    public static ActorPoseStage contact(BoneTransform[] before,RigPose pose,boolean supported) {
        if(!supported) return status(Kind.CONTACT,State.UNSUPPORTED,Detail.UNSUPPORTED_CONTACT);
        var changed=new HashSet<ActorPoseSources.Channel>();
        for(int i=0;i<before.length;i++) {
            var a=before[i];var b=pose.localTransform(i);String id=pose.rig().bone(i).id();
            if(!a.translation().equals(b.translation(),1e-6f)) changed.add(new ActorPoseSources.Channel(id,1));
            if(1-Math.abs(a.rotation().dot(b.rotation()))>1e-7f) changed.add(new ActorPoseSources.Channel(id,2));
        }
        return new ActorPoseStage(Kind.CONTACT,changed.isEmpty()?State.UNCHANGED:State.APPLIED,changed,Float.NaN,
                changed.isEmpty()?Detail.CONTACT_UNCHANGED:Detail.CONTACT_APPLIED,"");
    }
}
