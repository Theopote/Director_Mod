package com.director.ui.actor.pose;

import com.director.actor.rig.Pose;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.client.gizmo.Axis;
import com.director.timeline.model.entity.ActorPoseClip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BonePoseChannelMapperTest {
    @Test
    void mapsArmAxesAndEnablesArmOverride() {
        ActorPoseClip clip = new ActorPoseClip("pose", 0f, 1f);

        BonePoseChannelMapper.enableOverride(clip, HumanoidRig.LEFT_ARM);
        BonePoseChannelMapper.write(clip, HumanoidRig.LEFT_ARM, Axis.Y, 0.75f);

        assertTrue(clip.isOverrideArms());
        assertEquals(0.75f, clip.getLeftArmYaw(), 1.0e-5f);
        assertEquals(0.75f, BonePoseChannelMapper.read(clip, HumanoidRig.LEFT_ARM, Axis.Y), 1.0e-5f);
    }

    @Test
    void rejectsUnavailableLegacyChannels() {
        ActorPoseClip clip = new ActorPoseClip("pose", 0f, 1f);

        assertFalse(BonePoseChannelMapper.isEditable(HumanoidRig.HEAD, Axis.Z));
        assertFalse(BonePoseChannelMapper.isEditable(HumanoidRig.LEFT_LEG, Axis.Y));
        BonePoseChannelMapper.write(clip, HumanoidRig.LEFT_LEG, Axis.Y, 1f);
        assertEquals(0f, clip.getLeftLegPitch(), 1.0e-5f);
    }

    @Test
    void appliesSameChannelToLivePose() {
        Pose pose = new Pose();

        BonePoseChannelMapper.write(pose, HumanoidRig.RIGHT_ARM, Axis.Z, -0.4f);

        assertEquals(-0.4f, pose.rightArmRoll, 1.0e-5f);
    }
}
