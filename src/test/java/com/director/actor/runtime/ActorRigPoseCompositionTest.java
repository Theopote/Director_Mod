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

    @Test void scaleKeysRetainProceduralRotationAndCustomRigsSkipQuickPose() {
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
        RigPose custom = ActorRigPoseComposition.compose(null, keys, pose -> fail("No humanoid override"));
        assertNotSame(keys, custom);
        assertEquals(2f, custom.localTransform(arm).scale().x);
    }
}
