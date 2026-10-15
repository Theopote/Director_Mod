package com.director.ui.actor.pose;
import com.director.actor.rig.skeleton.*;
import com.director.actor.action.ActorActionType;
import com.director.timeline.playback.entity.EntitySample;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorBoneControlSourceTest {
    @Test void gazeThenKeysThenPoseThenBaseReflectRuntimePriority() {
        var sample=new EntitySample(); sample.action=ActorActionType.WALK;
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        assertTrue(ActorBoneControlSource.source("head",RigPose.ROTATION,sample,pose).contains("自动步态"));
        sample.poseOverrideEnabled=true; sample.poseOverrideHead=true;
        assertEquals("姿态片段",ActorBoneControlSource.source("head",RigPose.ROTATION,sample,pose));
        pose.setLocalTransform(pose.rig().requireBoneIndex("head"),pose.localTransform("head"),RigPose.ROTATION);
        assertEquals("手工骨骼关键帧",ActorBoneControlSource.source("head",RigPose.ROTATION,sample,pose));
        assertFalse(ActorBoneControlSource.source("head",RigPose.SCALE,sample,pose).contains("关键帧"));
        sample.lookAtEnabled=true; sample.lookAtStrength=1;
        assertTrue(ActorBoneControlSource.source("head",RigPose.ROTATION,sample,pose).contains("注视"));
    }
    @Test void regionPoseDisablesProceduralJointButNotAuthoredJoint() {
        var sample=new EntitySample(); sample.action=ActorActionType.RUN;
        sample.poseOverrideEnabled=true; sample.poseOverrideLegs=true;
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        String joint=ArticulatedPlayerRig.LEFT_SHIN;
        assertEquals("关节默认姿态",ActorBoneControlSource.source(joint,RigPose.ROTATION,sample,pose));
        pose.setLocalTransform(pose.rig().requireBoneIndex(joint),pose.localTransform(joint),RigPose.ROTATION);
        assertEquals("手工骨骼关键帧",ActorBoneControlSource.source(joint,RigPose.ROTATION,sample,pose));
        sample.active=false;
        assertEquals("演员当前未显示",ActorBoneControlSource.source(joint,RigPose.ROTATION,sample,pose));
    }
}
