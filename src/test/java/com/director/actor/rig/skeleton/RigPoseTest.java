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
}
