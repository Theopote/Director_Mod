package com.director.ui.actor.pose;

import com.director.actor.action.ActorActionType;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.EntitySample;

/** Describes local bone channel ownership, separately from world/root movement. */
final class ActorBoneControlSource {
    private ActorBoneControlSource() {}
    static String source(String bone,int channel,EntitySample sample,RigPose authored) {
        return source(bone,channel,sample,authored,null);
    }
    static String source(String bone,int channel,EntitySample sample,RigPose authored,RigPose preset) {
        if (sample == null || !sample.enabled || !sample.active || sample.visibility<=.01f) return "演员当前未显示";
        String lower=baseSource(bone,channel,sample,authored);
        boolean quick=sample.poseOverrideEnabled && channel==RigPose.ROTATION
                && (HumanoidRig.HEAD.equals(bone)&&sample.poseOverrideHead
                    || HumanoidRig.BODY.equals(bone)&&sample.poseOverrideBody
                    || (HumanoidRig.LEFT_ARM.equals(bone)||HumanoidRig.RIGHT_ARM.equals(bone)
                        || ArticulatedPlayerRig.LEFT_FOREARM.equals(bone)||ArticulatedPlayerRig.RIGHT_FOREARM.equals(bone))&&sample.poseOverrideArms
                    || (HumanoidRig.LEFT_LEG.equals(bone)||HumanoidRig.RIGHT_LEG.equals(bone)
                        || ArticulatedPlayerRig.LEFT_SHIN.equals(bone)||ArticulatedPlayerRig.RIGHT_SHIN.equals(bone))&&sample.poseOverrideLegs);
        if(!quick && sample.presetAnimation!=null && contributes(preset,bone,channel,
                sample.presetAnimation.settings(),sample.presetAnimation.envelope()))
            lower=describe("动作预设",sample.presetAnimation.settings(),sample.presetAnimation.envelope(),lower);
        if(contributes(authored,bone,channel,sample.animationBlendSettings,sample.animationBlendEnvelope))
            lower=describe(sample.animationIsPreset?"动作预设":"手工骨骼关键帧",
                    sample.animationBlendSettings,sample.animationBlendEnvelope,lower);
        boolean humanoid=authored==null || HumanoidTopology.matches(authored.rig());
        if(humanoid && channel==RigPose.ROTATION && HumanoidRig.HEAD.equals(bone)
                && sample.lookAtEnabled && sample.lookAtStrength>0)
            return sample.lookAtStrength>=1?"注视约束（覆盖头部动画）":"注视约束（部分影响）＋"+lower;
        return lower;
    }
    private static boolean contributes(RigPose pose,String bone,int channel,PoseBlendSettings settings,float envelope) {
        int index=pose==null?-1:pose.rig().findBoneIndex(bone);
        return index>=0 && (pose.authoredChannels(index)&channel)!=0 && (settings.channels()&channel)!=0
                && settings.includes(bone) && settings.effectiveWeight(envelope)>0;
    }
    private static String describe(String name,PoseBlendSettings settings,float envelope,String lower) {
        float weight=settings.effectiveWeight(envelope);
        if(weight>=1 && settings.mode()==PoseLayer.BlendMode.OVERRIDE) return name;
        return String.format(java.util.Locale.ROOT,"%s（%s %.0f%%）＋%s",name,
                settings.mode()==PoseLayer.BlendMode.ADDITIVE?"叠加":"混合",100*weight,lower);
    }
    private static String turnSource(String bone,int channel,EntitySample sample,RigPose reference) {
        if(reference==null || channel==RigPose.SCALE) return null;
        var base=new RigPose(reference.rig());
        boolean turning=com.director.actor.runtime.ActorRouteTurnPose.supports(base,sample);
        if(sample.routeClip==null) return null;
        int index=base.rig().findBoneIndex(bone);
        if(index<0) return null;
        if(turning) com.director.actor.runtime.ActorRouteTurnPose.apply(base,sample);
        if(channel==RigPose.ROTATION && (base.authoredChannels(index)&channel)!=0)
            return "路径端点转身／换脚";
        if(channel==RigPose.ROTATION && (HumanoidRig.LEFT_LEG.equals(bone)||HumanoidRig.RIGHT_LEG.equals(bone)
                ||ArticulatedPlayerRig.LEFT_SHIN.equals(bone)||ArticulatedPlayerRig.RIGHT_SHIN.equals(bone))) {
            com.director.actor.runtime.ActorRouteFootContact.apply(base,sample);
            if((base.authoredChannels(index)&channel)!=0) return "路径脚底接触修正（上层动画优先）";
        }
        return null;
    }

    private static String baseSource(String bone,int channel,EntitySample sample,RigPose authored) {
        boolean humanoid=authored==null || HumanoidTopology.matches(authored.rig());
        if (!humanoid) return "模型默认姿态";
        String turn=turnSource(bone,channel,sample,authored);
        if(turn!=null) return turn;
        if (channel != RigPose.ROTATION || HumanoidRig.ROOT.equals(bone)) return "默认局部姿态／运行时修正";
        boolean arm=HumanoidRig.LEFT_ARM.equals(bone)||HumanoidRig.RIGHT_ARM.equals(bone);
        boolean leg=HumanoidRig.LEFT_LEG.equals(bone)||HumanoidRig.RIGHT_LEG.equals(bone);
        boolean forearm=ArticulatedPlayerRig.LEFT_FOREARM.equals(bone)||ArticulatedPlayerRig.RIGHT_FOREARM.equals(bone);
        boolean shin=ArticulatedPlayerRig.LEFT_SHIN.equals(bone)||ArticulatedPlayerRig.RIGHT_SHIN.equals(bone);
        if (sample.poseOverrideEnabled && (HumanoidRig.HEAD.equals(bone)&&sample.poseOverrideHead
                || HumanoidRig.BODY.equals(bone)&&sample.poseOverrideBody || arm&&sample.poseOverrideArms || leg&&sample.poseOverrideLegs))
            return "姿态片段";
        if (forearm || shin) {
            if (sample.poseOverrideEnabled && (forearm&&sample.poseOverrideArms || shin&&sample.poseOverrideLegs)) return "关节默认姿态";
            if (sample.action != ActorActionType.WALK && sample.action != ActorActionType.RUN) return "关节默认姿态";
        }
        return sample.actionExplicit ? "显式动作："+sample.action : "基础动作／自动步态："+sample.action;
    }
}
