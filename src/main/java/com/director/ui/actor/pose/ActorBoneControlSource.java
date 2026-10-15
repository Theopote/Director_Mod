package com.director.ui.actor.pose;

import com.director.actor.action.ActorActionType;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.EntitySample;

/** Describes local bone channel ownership, separately from world/root movement. */
final class ActorBoneControlSource {
    private ActorBoneControlSource() {}
    static String source(String bone,int channel,EntitySample sample,RigPose authored) {
        if (sample == null || !sample.active) return "演员当前未显示";
        boolean humanoid = authored == null || HumanoidTopology.matches(authored.rig());
        if (humanoid && channel == RigPose.ROTATION && HumanoidRig.HEAD.equals(bone)
                && sample.lookAtEnabled && sample.lookAtStrength > 0) return "注视约束（覆盖头部动画）";
        int index = authored == null ? -1 : authored.rig().findBoneIndex(bone);
        if (index >= 0 && (authored.authoredChannels(index)&channel)!=0) return "手工骨骼关键帧";
        if (!humanoid) return "模型默认姿态";
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
