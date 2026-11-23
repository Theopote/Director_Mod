package com.director.actor.preview;

import com.director.actor.action.ActorActionType;
import com.director.actor.rig.HumanoidRigProfile;
import com.director.actor.rig.QuadrupedRigProfile;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.LegacyHumanoidPoseAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeMobPoseAnglesTest {
    @Test void bodyYawReachesAHeadThatIsNotParentedToTheBody() {
        var built = new HumanoidRigProfile().buildPose(ActorActionType.DANCE, 1f, 0.4f).pose;
        var rig = LegacyHumanoidPoseAdapter.convert(built);
        var followed = NativeMobPoseAngles.local(rig, HumanoidRig.HEAD, true);
        var local = NativeMobPoseAngles.local(rig, HumanoidRig.HEAD, false);
        assertEquals(built.bodyYaw, followed.yaw(), 1e-3f);
        assertEquals(0f, local.yaw(), 1e-4f);
    }

    @Test void quadrupedChannelsKeepIndependentLegAndHeadPitches() {
        var profile = new QuadrupedRigProfile();
        var walk = profile.buildPose(ActorActionType.WALK, 1f, 1f).pose;
        var graze = profile.buildPose(ActorActionType.GRAZE, 1f, 0f).pose;
        var walked = LegacyHumanoidPoseAdapter.convert(walk);
        var grazed = LegacyHumanoidPoseAdapter.convert(graze);
        assertEquals(walk.leftLegPitch, NativeMobPoseAngles.local(walked, HumanoidRig.LEFT_LEG, false).pitch(), 1e-3f);
        assertEquals(walk.rightLegPitch, NativeMobPoseAngles.local(walked, HumanoidRig.RIGHT_LEG, false).pitch(), 1e-3f);
        assertTrue(walk.leftLegPitch * walk.rightLegPitch < 0f);
        assertEquals(graze.headPitch, NativeMobPoseAngles.local(grazed, HumanoidRig.HEAD, false).pitch(), 1e-3f);
    }

    @Test void villagerArmsFollowTheDominantArm() {
        var carryBuilt = new HumanoidRigProfile().buildPose(ActorActionType.CARRY, 1f, 0f).pose;
        var carry = LegacyHumanoidPoseAdapter.convert(carryBuilt);
        assertEquals(0.5f, carryBuilt.rightArmPitch, 1e-4f);
        assertEquals(carryBuilt.leftArmPitch, carryBuilt.rightArmPitch, 1e-4f);
        assertEquals(HumanoidRig.RIGHT_ARM, NativeMobPoseAngles.dominantArm(carry));
        assertEquals(0.5f, NativeMobPoseAngles.local(carry, HumanoidRig.RIGHT_ARM, false).pitch(), 1e-3f);
        assertTrue(Math.abs(NativeMobPoseAngles.local(carry, HumanoidRig.RIGHT_ARM, true).pitch() - 0.5f) > 1e-3f);

        var walkBuilt = new HumanoidRigProfile().buildPose(ActorActionType.WALK, 1f, 0.4f).pose;
        var walk = LegacyHumanoidPoseAdapter.convert(walkBuilt);
        float mean = (walkBuilt.leftArmPitch + walkBuilt.rightArmPitch) * 0.5f;
        var shown = NativeMobPoseAngles.local(walk, NativeMobPoseAngles.dominantArm(walk), false);
        float larger = Math.max(Math.abs(walkBuilt.leftArmPitch), Math.abs(walkBuilt.rightArmPitch));
        assertTrue(larger > 1e-3f);
        assertEquals(larger, Math.abs(shown.pitch()), 1e-3f);
        assertTrue(Math.abs(shown.pitch() - mean) > 1e-3f);

        var wave = new com.director.actor.rig.Pose();
        wave.rightArmPitch = -1.2f;
        var waved = LegacyHumanoidPoseAdapter.convert(wave);
        assertEquals(HumanoidRig.RIGHT_ARM, NativeMobPoseAngles.dominantArm(waved));
        assertEquals(-1.2f, NativeMobPoseAngles.local(waved, HumanoidRig.RIGHT_ARM, false).pitch(), 1e-3f);
    }
}
