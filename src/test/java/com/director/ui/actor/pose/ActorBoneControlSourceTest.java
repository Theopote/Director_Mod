package com.director.ui.actor.pose;
import com.director.actor.rig.skeleton.*;
import com.director.actor.action.ActorActionType;
import com.director.timeline.playback.entity.EntitySample;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorBoneControlSourceTest {
    @Test void idleJointDefaultsDoNotHideAnAuthoredPresetJoint() {
        var sample=new EntitySample();sample.enabled=true;sample.action=ActorActionType.IDLE;
        var rig=ArticulatedPlayerRig.DEFINITION;var empty=new RigPose(rig);var preset=new RigPose(rig);
        String joint=ArticulatedPlayerRig.LEFT_SHIN;
        preset.setLocalTransform(rig.requireBoneIndex(joint),preset.localTransform(joint),RigPose.ROTATION);
        sample.presetAnimation=new com.director.timeline.playback.entity.ActorAnimationLayerSample("test:joint",1,false,1,PoseBlendSettings.DEFAULT,1);
        assertEquals("动作预设",ActorBoneControlSource.source(joint,2,sample,empty,preset));
        sample.poseOverrideEnabled=true;sample.poseOverrideLegs=true;
        assertEquals("关节默认姿态",ActorBoneControlSource.source(joint,2,sample,empty,preset));
    }

    @Test void disabledManualChannelsFallThroughToActualPresetOrBase() {
        var sample=new EntitySample();sample.enabled=true;sample.action=ActorActionType.WALK;
        var rig=ArticulatedPlayerRig.DEFINITION;var manual=new RigPose(rig);var preset=new RigPose(rig);
        for(var pose:java.util.List.of(manual,preset)) pose.setLocalTransform(rig.requireBoneIndex("head"),pose.localTransform("head"),2);
        sample.presetAnimation=new com.director.timeline.playback.entity.ActorAnimationLayerSample("wave",1,false,1,PoseBlendSettings.DEFAULT,1);
        sample.animationBlendSettings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,0,7,true,java.util.Set.of());
        assertEquals("动作预设",ActorBoneControlSource.source("head",2,sample,manual,preset));
        sample.animationBlendSettings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,4,true,java.util.Set.of());
        assertEquals("动作预设",ActorBoneControlSource.source("head",2,sample,manual,preset));
        sample.animationBlendSettings=PoseBlendSettings.DEFAULT;sample.animationBlendEnvelope=0;
        assertEquals("动作预设",ActorBoneControlSource.source("head",2,sample,manual,preset));
        sample.presetAnimation=null;
        assertTrue(ActorBoneControlSource.source("head",2,sample,manual,preset).contains("自动步态"));
    }
    @Test void partialAndAdditiveOwnershipNeverClaimsExclusiveControl() {
        var sample=new EntitySample();sample.enabled=true;sample.action=ActorActionType.WALK;
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);pose.setLocalTransform("head",pose.localTransform("head"));
        sample.animationBlendSettings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.25f,2,true,java.util.Set.of());
        String description=ActorBoneControlSource.source("head",2,sample,pose);
        assertTrue(description.contains("25%"));assertTrue(description.contains("自动步态"));
        sample.animationIsPreset=true;sample.animationBlendSettings=new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,1,2,true,java.util.Set.of());
        description=ActorBoneControlSource.source("head",2,sample,pose);
        assertTrue(description.contains("动作预设"));assertTrue(description.contains("叠加"));assertTrue(description.contains("自动步态"));
        sample.lookAtEnabled=true;sample.lookAtStrength=.5f;
        assertTrue(ActorBoneControlSource.source("head",2,sample,pose).contains("部分影响"));
        sample.visibility=0;assertEquals("演员当前未显示",ActorBoneControlSource.source("head",2,sample,pose));
    }

    @Test void gazeThenKeysThenPoseThenBaseReflectRuntimePriority() {
        var sample=new EntitySample();sample.enabled=true; sample.action=ActorActionType.WALK;
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
        var sample=new EntitySample();sample.enabled=true; sample.action=ActorActionType.RUN;
        sample.poseOverrideEnabled=true; sample.poseOverrideLegs=true;
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        String joint=ArticulatedPlayerRig.LEFT_SHIN;
        assertEquals("关节默认姿态",ActorBoneControlSource.source(joint,RigPose.ROTATION,sample,pose));
        pose.setLocalTransform(pose.rig().requireBoneIndex(joint),pose.localTransform(joint),RigPose.ROTATION);
        assertEquals("手工骨骼关键帧",ActorBoneControlSource.source(joint,RigPose.ROTATION,sample,pose));
        sample.enabled=false;
        assertEquals("演员当前未显示",ActorBoneControlSource.source(joint,RigPose.ROTATION,sample,pose));
        sample.enabled=true;sample.active=false;
        assertEquals("演员当前未显示",ActorBoneControlSource.source(joint,RigPose.ROTATION,sample,pose));
    }
    @Test void endpointTurnSourcesRespectAuthoredChannelsAndQuickRegions() {
        var sample=new EntitySample();sample.enabled=true;sample.action=ActorActionType.IDLE;
        var route=new com.director.timeline.model.entity.ActorRouteClip("Turn",0,10);
        route.setWaypoints(java.util.List.of(new org.joml.Vector3f(),new org.joml.Vector3f(0,0,4)));
        route.setSpeed(2);route.setLoopMode(com.director.timeline.model.entity.ActorRouteClip.LoopMode.PING_PONG);
        route.setEndpointTurnDuration(.4f);sample.routeClip=route;sample.routeLocalTime=2.1f;
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        assertTrue(ActorBoneControlSource.source(HumanoidRig.LEFT_LEG,2,sample,pose).contains("换脚"));
        assertTrue(ActorBoneControlSource.source(HumanoidRig.RIGHT_LEG,1,sample,pose).contains("脚底"));
        assertFalse(ActorBoneControlSource.source(HumanoidRig.HEAD,2,sample,pose).contains("换脚"));
        sample.poseOverrideEnabled=true;sample.poseOverrideHead=true;
        assertTrue(ActorBoneControlSource.source(HumanoidRig.RIGHT_LEG,1,sample,pose).contains("脚底"));
        sample.poseOverrideLegs=true;
        assertFalse(ActorBoneControlSource.source(HumanoidRig.RIGHT_LEG,1,sample,pose).contains("脚底"));
        assertEquals("姿态片段",ActorBoneControlSource.source(HumanoidRig.LEFT_LEG,2,sample,pose));
        sample.poseOverrideEnabled=false;
        pose.setLocalTransform(pose.rig().requireBoneIndex(HumanoidRig.LEFT_LEG),pose.localTransform(HumanoidRig.LEFT_LEG),2);
        assertEquals("手工骨骼关键帧",ActorBoneControlSource.source(HumanoidRig.LEFT_LEG,2,sample,pose));
        route.setAlignRotationToRoute(false);
        assertFalse(ActorBoneControlSource.source(HumanoidRig.BODY,2,sample,pose).contains("换脚"));
    }
}
