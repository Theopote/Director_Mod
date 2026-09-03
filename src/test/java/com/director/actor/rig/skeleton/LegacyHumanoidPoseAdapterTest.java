package com.director.actor.rig.skeleton;

import com.director.actor.rig.Pose;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyHumanoidPoseAdapterTest {
    @Test
    void preservesLegacyArmRotationInStandardRig() {
        Pose legacy = new Pose();
        legacy.leftArmPitch = 0.4f;
        legacy.leftArmYaw = -0.2f;
        legacy.leftArmRoll = 0.7f;

        RigPose converted = LegacyHumanoidPoseAdapter.convert(legacy);
        int arm = HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.LEFT_ARM);
        Vector3f euler = converted.localTransform(arm).rotation().getEulerAnglesXYZ(new Vector3f());

        assertTrue(converted.isAuthored(arm));
        assertEquals(legacy.leftArmPitch, euler.x, 1.0e-5f);
        assertEquals(legacy.leftArmYaw, euler.y, 1.0e-5f);
        assertEquals(legacy.leftArmRoll, euler.z, 1.0e-5f);
    }
}
