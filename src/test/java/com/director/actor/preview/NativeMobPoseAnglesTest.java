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

    @Test void villagerArmsUseTheMeanOfBothArmPitches() {
        var built = new HumanoidRigProfile().buildPose(ActorActionType.CARRY, 1f, 0f).pose;
        var rig = LegacyHumanoidPoseAdapter.convert(built);
        float mean = (built.leftArmPitch + built.rightArmPitch) * 0.5f;
        var local = NativeMobPoseAngles.average(rig, HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_ARM, false);
        var followed = NativeMobPoseAngles.average(rig, HumanoidRig.LEFT_ARM, HumanoidRig.RIGHT_ARM, true);
        assertEquals(mean, local.pitch(), 1e-3f);
        assertTrue(Math.abs(followed.pitch() - mean) > 1e-3f);
    }
}
