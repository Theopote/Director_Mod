package com.director.actor.rig;

import com.director.actor.action.ActorActionType;
import com.director.actor.rig.skeleton.LegacyMobPoseAdapter;
import com.director.actor.rig.skeleton.QuadrupedRig;
import com.director.actor.rig.skeleton.VillagerRig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyMobPoseAdapterTest {
    @Test
    void convertsProceduralVillagerArmsAndLegsToNativeRig() {
        var pose = new Pose();
        pose.headPitch = .4f;
        pose.leftArmPitch = .2f;
        pose.rightArmPitch = .6f;
        pose.leftLegPitch = .3f;
        var converted = LegacyMobPoseAdapter.convert(pose, VillagerRig.DEFINITION);
        assertEquals(.6f, converted.localTransform(VillagerRig.ARMS).rotation()
                .getEulerAnglesXYZ(new org.joml.Vector3f()).x, 1e-5f);
        assertEquals(.3f, converted.localTransform(VillagerRig.LEFT_LEG).rotation()
                .getEulerAnglesXYZ(new org.joml.Vector3f()).x, 1e-5f);
    }

    @Test
    void villagerFoldedArmsKeepTheirPhaseDuringProceduralActions() {
        var profile = new HumanoidRigProfile();
        for (var action : new ActorActionType[]{ActorActionType.WALK, ActorActionType.RUN, ActorActionType.DANCE}) {
            var pose = profile.buildPose(action, 1f, .4f).pose;
            assertEquals(-pose.leftArmPitch, pose.rightArmPitch, 1e-5f);
            org.junit.jupiter.api.Assertions.assertTrue(Math.abs(pose.rightArmPitch) > .1f, action.name());
            var converted = LegacyMobPoseAdapter.convert(pose, VillagerRig.DEFINITION);
            assertEquals(pose.rightArmPitch, converted.localTransform(VillagerRig.ARMS).rotation()
                    .getEulerAnglesXYZ(new org.joml.Vector3f()).x, 1e-5f, action.name());
        }
        var carry = profile.buildPose(ActorActionType.CARRY, 1f, 0f).pose;
        var converted = LegacyMobPoseAdapter.convert(carry, VillagerRig.DEFINITION);
        assertEquals(.5f, converted.localTransform(VillagerRig.ARMS).rotation()
                .getEulerAnglesXYZ(new org.joml.Vector3f()).x, 1e-5f);
    }

    @Test
    void convertsTwoProceduralQuadrupedChannelsToFourLegs() {
        var pose = new Pose();
        pose.leftLegPitch = .35f;
        pose.rightLegPitch = -.25f;
        var converted = LegacyMobPoseAdapter.convert(pose, QuadrupedRig.DEFINITION);
        var angles = new org.joml.Vector3f();
        assertEquals(.35f, converted.localTransform(QuadrupedRig.LEFT_FRONT_LEG).rotation()
                .getEulerAnglesXYZ(angles).x, 1e-5f);
        assertEquals(.35f, converted.localTransform(QuadrupedRig.RIGHT_HIND_LEG).rotation()
                .getEulerAnglesXYZ(angles).x, 1e-5f);
        assertEquals(-.25f, converted.localTransform(QuadrupedRig.RIGHT_FRONT_LEG).rotation()
                .getEulerAnglesXYZ(angles).x, 1e-5f);
        assertEquals(-.25f, converted.localTransform(QuadrupedRig.LEFT_HIND_LEG).rotation()
                .getEulerAnglesXYZ(angles).x, 1e-5f);
    }
}
