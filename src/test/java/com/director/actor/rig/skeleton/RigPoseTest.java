package com.director.actor.rig.skeleton;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigPoseTest {
    @Test
    void calculatesModelSpaceThroughParentHierarchy() {
        RigPose pose = new RigPose(HumanoidRig.DEFINITION);

        Matrix4f[] model = pose.calculateModelTransforms();
        Vector3f headPosition = model[HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.HEAD)]
                .getTranslation(new Vector3f());

        assertEquals(0f, headPosition.x, 1.0e-5f);
        assertEquals(1.625f, headPosition.y, 1.0e-5f);
        assertEquals(0f, headPosition.z, 1.0e-5f);
    }

    @Test
    void copiesAuthoredStateIndependently() {
        RigPose original = new RigPose(HumanoidRig.DEFINITION);
        int head = HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.HEAD);
        original.setLocalTransform(head, BoneTransform.translation(1f, 2f, 3f));
        RigPose copy = original.copy();

        original.setLocalTransform(head, BoneTransform.translation(4f, 5f, 6f));

        assertTrue(copy.isAuthored(head));
        assertEquals(1f, copy.localTransform(head).translation().x, 1.0e-5f);
        assertFalse(copy.isAuthored(HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.ROOT)));
    }

    @Test
    void resolvesActorTransformIntoWorldSpace() {
        RigPose pose = new RigPose(HumanoidRig.DEFINITION);
        RigWorldPose world = RigWorldPose.resolve(pose, new Matrix4f().translate(10f, 2f, -3f));

        Vector3f head = world.position(HumanoidRig.HEAD);

        assertEquals(10f, head.x, 1.0e-5f);
        assertEquals(3.625f, head.y, 1.0e-5f);
        assertEquals(-3f, head.z, 1.0e-5f);
    }

    @Test
    void overlaysOnlyBonesAuthoredByHigherLayer() {
        RigPose base = new RigPose(HumanoidRig.DEFINITION);
        base.setLocalTransform(HumanoidRig.HEAD, BoneTransform.translation(1f, 2f, 3f));
        RigPose animation = new RigPose(HumanoidRig.DEFINITION);
        animation.setLocalTransform(HumanoidRig.LEFT_ARM, BoneTransform.translation(4f, 5f, 6f));

        RigPose result = base.overlay(animation);

        assertEquals(1f, result.localTransform(HumanoidRig.HEAD).translation().x, 1.0e-5f);
        assertEquals(4f, result.localTransform(HumanoidRig.LEFT_ARM).translation().x, 1.0e-5f);
        assertFalse(base.isAuthored(HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.LEFT_ARM)));
    }
}
