package com.director.actor.animation;

import com.director.actor.rig.Pose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnimationDataSamplerTest {
    private static final String ID = "test:absolute-sampler";
    @AfterEach void cleanup() { AnimationLibrary.getInstance().remove(ID); }
    private void register(float end) {
        Pose last = new Pose(); last.headYaw = end;
        AnimationLibrary.getInstance().register(new AnimationData(ID, ID, 1f,
                List.of(new AnimationKeyframe(0f, new Pose()), new AnimationKeyframe(1f, last)),
                AnimationData.AnimationSource.CUSTOM));
    }
    private float yaw(float time, boolean loop, float speed) {
        return AnimationDataSampler.sample(ID, time, loop, speed).pose.headYaw;
    }
    @Test void builtInOwnershipIsExplicitAndStableAtNeutralFrames() {
        var rig=com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION;
        var wave=AnimationDataSampler.sampleRig("wave",rig,0,false,1);
        assertEquals(2,wave.authoredChannels(rig.requireBoneIndex("right_arm")));
        assertEquals(2,wave.authoredChannels(rig.requireBoneIndex("body")));
        for(var id:List.of("head","left_arm","left_leg","right_leg","left_forearm"))
            assertEquals(0,wave.authoredChannels(rig.requireBoneIndex(id)));
        for(float time:new float[]{0,.275f,.55f}) {
            var attack=AnimationDataSampler.sampleRig("attack",rig,time,false,1);
            assertEquals(2,attack.authoredChannels(rig.requireBoneIndex("right_arm")));
            assertEquals(1,attack.authoredBones().cardinality());
        }
        assertFalse(AnimationDataSampler.sample("wave",0,false,1).mask.legs);
    }
    @Test void wavePreservesGaitAndManualKeysCanOverrideOneChannel() {
        var rig=com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION;
        var base=new com.director.actor.rig.skeleton.RigPose(rig);
        var rotation=new org.joml.Quaternionf().rotateX(.7f);
        for(var id:List.of("left_leg","right_leg","left_arm"))
            base.setLocalTransform(id,base.localTransform(id).withRotation(rotation));
        var wave=AnimationDataSampler.sampleRig("wave",rig,.4f,false,1);
        var mixed=com.director.actor.rig.skeleton.RigPoseComposer.compose(base,
                List.of(com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT.layer(wave))).pose();
        for(var id:List.of("left_leg","right_leg","left_arm"))
            assertEquals(base.localTransform(id),mixed.localTransform(id));
        var manual=new com.director.actor.rig.skeleton.RigPose(rig);
        int arm=rig.requireBoneIndex("right_arm");
        manual.setLocalTransform(arm,manual.localTransform(arm).withRotation(rotation),2);
        var corrected=com.director.actor.rig.skeleton.RigPoseComposer.compose(mixed,
                List.of(com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT.layer(manual))).pose();
        assertEquals(rotation,corrected.localTransform(arm).rotation());
        assertEquals(mixed.localTransform("body"),corrected.localTransform("body"));
    }
    @Test void userMaskCannotExpandActionOwnership() {
        var rig=com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION;
        var action=AnimationDataSampler.sampleRig("attack",rig,.275f,false,1);
        var settings=new com.director.actor.rig.skeleton.PoseBlendSettings(
                com.director.actor.rig.skeleton.PoseLayer.BlendMode.OVERRIDE,1,7,false,java.util.Set.of("head"));
        var base=new com.director.actor.rig.skeleton.RigPose(rig);
        var mixed=com.director.actor.rig.skeleton.RigPoseComposer.compose(base,List.of(settings.layer(action))).pose();
        assertEquals(base.localTransform("right_arm"),mixed.localTransform("right_arm"));
        assertEquals(base.localTransform("head"),mixed.localTransform("head"));
    }
    @Test void customOwnershipIsDetachedAndRejectsUnsupportedBones() {
        var bones=new java.util.HashSet<>(java.util.Set.of("head"));
        var data=new AnimationData(ID,ID,1,List.of(new AnimationKeyframe(0,new Pose())),
                AnimationData.AnimationSource.CUSTOM,bones);
        bones.clear(); assertEquals(java.util.Set.of("head"),data.controlledBones());
        assertThrows(UnsupportedOperationException.class,()->data.controlledBones().clear());
        assertThrows(IllegalArgumentException.class,()->new AnimationData(ID,ID,1,List.of(),
                AnimationData.AnimationSource.CUSTOM,java.util.Set.of("root")));
        register(1); assertEquals(AnimationData.POSE_BONES,AnimationLibrary.getInstance().get(ID).orElseThrow().controlledBones());
    }

    @Test void rigAdapterAuthorsOnlyAvailableRotationChannels() {
        register(1f);
        var rig=com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION;
        var pose=AnimationDataSampler.sampleRig(ID,rig,.5f,false,1f);
        assertNotNull(pose);
        assertEquals(2,pose.authoredChannels(rig.requireBoneIndex("head")));
        assertEquals(0,pose.authoredChannels(rig.requireBoneIndex("root")));
        assertEquals(0,pose.authoredChannels(rig.requireBoneIndex("left_forearm")));
        assertNull(AnimationDataSampler.sampleRig(ID,null,.5f,false,1f));
    }
    @Test void adaptedActionHonorsWeightMaskAndPreservesUnwrittenChannels() {
        register(1f);
        var rig=com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION;
        var base=new com.director.actor.rig.skeleton.RigPose(rig);
        var head=base.localTransform("head");
        base.setLocalTransform("head",new com.director.actor.rig.skeleton.BoneTransform(
                new org.joml.Vector3f(1,2,3),head.rotation(),new org.joml.Vector3f(2)));
        var action=AnimationDataSampler.sampleRig(ID,rig,1f,false,1f);
        var settings=new com.director.actor.rig.skeleton.PoseBlendSettings(
                com.director.actor.rig.skeleton.PoseLayer.BlendMode.OVERRIDE,.5f,7,false,java.util.Set.of("head"));
        var mixed=com.director.actor.rig.skeleton.RigPoseComposer.compose(base,List.of(settings.layer(action))).pose();
        assertEquals(new org.joml.Vector3f(1,2,3),mixed.localTransform("head").translation());
        assertEquals(new org.joml.Vector3f(2),mixed.localTransform("head").scale());
        assertEquals(.5f,mixed.localTransform("head").rotation().getEulerAnglesXYZ(new org.joml.Vector3f()).y,1e-5f);
        var faded=com.director.actor.rig.skeleton.RigPoseComposer.compose(base,List.of(settings.layer(action,0))).pose();
        assertEquals(base.localTransform("head"),faded.localTransform("head"));
    }

    @Test void sameIdUsesCurrentLoopAndSpeedOnEverySeek() {
        register(1f);
        assertEquals(.25f, yaw(1.25f, true, 1f), 1e-5f);
        assertEquals(1f, yaw(1.25f, false, 1f), 1e-5f);
        assertEquals(.5f, yaw(.25f, false, 2f), 1e-5f);
        assertEquals(.25f, yaw(1.25f, true, 1f), 1e-5f);
    }
    @Test void missingOrRemovedIdNeverRetainsPreviousPose() {
        register(1f); assertNotNull(AnimationDataSampler.sample(ID, .5f, false, 1f));
        assertNull(AnimationDataSampler.sample("test:missing", .5f, false, 1f));
        AnimationLibrary.getInstance().remove(ID);
        assertNull(AnimationDataSampler.sample(ID, .5f, false, 1f));
        assertNull(AnimationDataSampler.sample(null, .5f, false, 1f));
    }
    @Test void replacingAssetWithSameIdTakesEffectImmediately() {
        register(1f); assertEquals(.5f, yaw(.5f, false, 1f), 1e-5f);
        register(2f); assertEquals(1f, yaw(.5f, false, 1f), 1e-5f);
    }
    @Test void runtimeEditsCannotMutateBoundaryKeyframes() {
        register(1f);
        AnimationDataSampler.sample(ID, 1f, false, 1f).pose.headYaw = 99f;
        assertEquals(1f, yaw(1f, false, 1f));
        AnimationDataSampler.sample(ID, 0f, false, 1f).pose.headYaw = 99f;
        assertEquals(0f, yaw(0f, false, 1f));
    }
    @Test void invalidInputsAndHugeTimeRemainFinite() {
        register(1f);
        assertEquals(0f, yaw(Float.NaN, true, 1f));
        assertEquals(.5f, yaw(.5f, false, Float.NaN));
        assertEquals(0f, yaw(-1f, false, 1f));
        assertTrue(Float.isFinite(yaw(Float.MAX_VALUE, true, Float.MAX_VALUE)));
    }
}
