package com.director.actor.animation.asset;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.RigPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationAssetTest {
    @Test
    void samplesOnlyAuthoredBonesAndChannels() {
        AnimationAsset asset = new AnimationAsset("project:wave", "Wave", HumanoidRig.DEFINITION,
                2f, AnimationLoopMode.ONCE);
        BoneTrack arm = asset.getOrCreateBoneTrack(HumanoidRig.RIGHT_ARM);
        arm.rotation().put(0f, new Quaternionf(), InterpolationMode.LINEAR);
        arm.rotation().put(2f, new Quaternionf().rotateX(1f), InterpolationMode.LINEAR);

        RigPose sampled = asset.sample(1f);
        Vector3f angles = sampled.localTransform(HumanoidRig.RIGHT_ARM).rotation().getEulerAnglesXYZ(new Vector3f());

        assertTrue(sampled.isAuthored(HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.RIGHT_ARM)));
        assertFalse(sampled.isAuthored(HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.HEAD)));
        assertEquals(0.5f, angles.x, 1.0e-4f);
        assertEquals(HumanoidRig.DEFINITION.bone(HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.RIGHT_ARM))
                .restLocal().translation().x, sampled.localTransform(HumanoidRig.RIGHT_ARM).translation().x, 1.0e-5f);
    }

    @Test
    void normalizesLoopAndPingPongTime() {
        AnimationAsset asset = new AnimationAsset("project:test", "Test", HumanoidRig.DEFINITION,
                2f, AnimationLoopMode.LOOP);
        assertEquals(0.5f, asset.normalizeTime(2.5f), 1.0e-5f);
        asset.setLoopMode(AnimationLoopMode.PING_PONG);
        assertEquals(1.5f, asset.normalizeTime(2.5f), 1.0e-5f);
        assertEquals(0.5f, asset.normalizeTime(3.5f), 1.0e-5f);
    }

    @Test
    void rejectsUnknownBonesAndOutOfRangeMarkers() {
        AnimationAsset asset = new AnimationAsset("project:test", "Test", HumanoidRig.DEFINITION,
                1f, AnimationLoopMode.ONCE);
        assertThrows(IllegalArgumentException.class, () -> asset.getOrCreateBoneTrack("tail"));
        assertThrows(IllegalArgumentException.class, () -> asset.addMarker(new AnimationMarker(2f, "hit")));
    }
}
