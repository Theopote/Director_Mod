package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorRigPoseCompositionTest {
    @Test void manualRotationWinsOverQuickPoseWhileLegsKeepWalking() {
        var rig = HumanoidRig.DEFINITION;
        int head = rig.requireBoneIndex(HumanoidRig.HEAD);
        int leg = rig.requireBoneIndex(HumanoidRig.LEFT_LEG);
        RigPose base = new RigPose(rig);
        var walking = new Quaternionf().rotateX(.4f);
        base.setLocalTransform(leg, base.localTransform(leg).withRotation(walking), RigPose.ROTATION);
        RigPose keys = new RigPose(rig);
        var manual = new Quaternionf().rotateY(.7f);
        keys.setLocalTransform(head, keys.localTransform(head).withRotation(manual), RigPose.ROTATION);
        RigPose result = ActorRigPoseComposition.compose(base, keys, pose ->
                pose.setLocalTransform(head, pose.localTransform(head).withRotation(
                        new Quaternionf().rotateX(.8f)), RigPose.ROTATION));
        assertTrue(result.localTransform(head).rotation().equals(manual, 1e-5f));
        assertTrue(result.localTransform(leg).rotation().equals(walking, 1e-5f));
        assertTrue(base.localTransform(head).rotation().equals(new Quaternionf(), 1e-5f));
    }

    @Test void scaleKeysRetainProceduralRotationAndCustomRigsApplyQuickPose() {
        var rig = HumanoidRig.DEFINITION;
        int arm = rig.requireBoneIndex(HumanoidRig.LEFT_ARM);
        RigPose base = new RigPose(rig);
        var swing = new Quaternionf().rotateX(.3f);
        base.setLocalTransform(arm, base.localTransform(arm).withRotation(swing), RigPose.ROTATION);
        RigPose keys = new RigPose(rig);
        keys.setLocalTransform(arm, new BoneTransform(keys.localTransform(arm).translation(),
                new Quaternionf(), new Vector3f(2f)), RigPose.SCALE);
        RigPose result = ActorRigPoseComposition.compose(base, keys, pose -> {});
        assertTrue(result.localTransform(arm).rotation().equals(swing, 1e-5f));
        assertEquals(2f, result.localTransform(arm).scale().x);
        RigPose custom = ActorRigPoseComposition.compose(null, keys, pose -> pose.setLocalTransform(arm,pose.localTransform(arm).withRotation(swing),RigPose.ROTATION));
        assertNotSame(keys, custom);
        assertEquals(2f, custom.localTransform(arm).scale().x);
        assertTrue(custom.localTransform(arm).rotation().equals(swing,1e-5f));
    }
    @Test void playbackCanReportAndSkipAnIncompatibleLayerWithoutLosingBasePose() {
        var base=new RigPose(HumanoidRig.DEFINITION);
        base.setLocalTransform("left_leg",base.localTransform("left_leg").withRotation(new Quaternionf().rotateX(.4f)));
        var incompatible=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var diagnostics=new java.util.ArrayList<RigPoseComposer.Diagnostic>();
        var result=ActorRigPoseComposition.compose(base,incompatible,pose->{},diagnostics::add);
        assertEquals(1,diagnostics.size());
        assertTrue(result.localTransform("left_leg").rotation().equals(base.localTransform("left_leg").rotation(),1e-5f));
    }
    @Test void runtimeAppliesPersistentWeightModeAndChannelMask() {
        var base=new RigPose(HumanoidRig.DEFINITION);var keys=new RigPose(HumanoidRig.DEFINITION);
        int head=base.rig().requireBoneIndex(HumanoidRig.HEAD),leg=base.rig().requireBoneIndex(HumanoidRig.LEFT_LEG);
        base.setLocalTransform(head,base.localTransform(head).withRotation(new Quaternionf().rotateY(.4f)),RigPose.ROTATION);
        base.setLocalTransform(leg,base.localTransform(leg).withRotation(new Quaternionf().rotateX(.5f)),RigPose.ROTATION);
        keys.setLocalTransform(head,new BoneTransform(keys.localTransform(head).translation(),new Quaternionf().rotateY(.8f),new Vector3f(2)),RigPose.ROTATION|RigPose.SCALE);
        keys.setLocalTransform(leg,keys.localTransform(leg).withRotation(new Quaternionf().rotateX(1)),RigPose.ROTATION);
        var settings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.5f,RigPose.ROTATION,false,java.util.Set.of(HumanoidRig.HEAD));
        var result=ActorRigPoseComposition.compose(base,keys,p->{},d->fail(d.reason()),settings);
        assertTrue(result.localTransform(head).rotation().equals(new Quaternionf().rotateY(.6f),1e-5f));
        assertEquals(new Vector3f(1),result.localTransform(head).scale());
        assertEquals(base.localTransform(leg),result.localTransform(leg));
        settings=new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,.5f,RigPose.ROTATION,false,java.util.Set.of(HumanoidRig.HEAD));
        result=ActorRigPoseComposition.compose(base,keys,p->{},d->fail(d.reason()),settings);
        assertTrue(result.localTransform(head).rotation().equals(new Quaternionf().rotateY(.8f),1e-5f));
        settings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,0,7,true,java.util.Set.of());
        result=ActorRigPoseComposition.compose(base,keys,p->{},d->fail(d.reason()),settings);
        assertEquals(base.localTransform(head),result.localTransform(head));
    }
}
