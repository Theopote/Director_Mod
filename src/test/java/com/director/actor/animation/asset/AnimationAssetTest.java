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
    void scaleOnlyAnimationPreservesLowerLayerRotationAndTranslation() {
        var asset = new AnimationAsset("scale", "Scale", HumanoidRig.DEFINITION, 2f, AnimationLoopMode.ONCE);
        asset.getOrCreateBoneTrack(HumanoidRig.RIGHT_ARM).scale()
                .put(0f, new Vector3f(2f), InterpolationMode.LINEAR);
        int arm = HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.RIGHT_ARM);
        var base = new RigPose(HumanoidRig.DEFINITION);
        var rotation = new Quaternionf().rotateX(0.7f);
        base.setLocalTransform(arm, new com.director.actor.rig.skeleton.BoneTransform(
                new Vector3f(1f, 2f, 3f), rotation, new Vector3f(1f)));
        var sampled = asset.sample(1f).copy();
        assertEquals(RigPose.SCALE, sampled.authoredChannels(arm));
        var composed = new RigPose(HumanoidRig.DEFINITION).overlay(sampled);
        assertEquals(RigPose.SCALE, composed.authoredChannels(arm));
        var result = base.overlay(composed).localTransform(arm);
        assertEquals(1f, Math.abs(rotation.dot(result.rotation())), 1e-5f);
        assertEquals(new Vector3f(1f, 2f, 3f), result.translation());
        assertEquals(new Vector3f(2f), result.scale());
        assertEquals(new Vector3f(1f), base.localTransform(arm).scale());
    }

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
