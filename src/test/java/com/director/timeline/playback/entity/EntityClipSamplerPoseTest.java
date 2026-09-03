package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.ActorPoseClip;
import com.director.timeline.model.entity.ActorTrack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityClipSamplerPoseTest {
    @Test
    void poseClipInterpolatesFromPreviousTargetAcrossItsDuration() {
        ActorTrack track = new ActorTrack("actor");
        ActorPoseClip first = new ActorPoseClip("first", 0f, 2f);
        first.setOverrideArms(true);
        first.setLeftArmPitch(0f);
        first.setLeftArmYaw(-1f);
        ActorPoseClip second = new ActorPoseClip("second", 1f, 3f);
        second.setOverrideArms(true);
        second.setLeftArmPitch(1f);
        second.setLeftArmYaw(1f);
        track.addClip(first);
        track.addClip(second);

        EntitySample sample = new EntityClipSampler(track).sampleAt(1.5f);

        assertTrue(sample.poseOverrideArms);
        assertEquals(0.25f, sample.poseLeftArmPitch, 1.0e-5f);
        assertEquals(-0.5f, sample.poseLeftArmYaw, 1.0e-5f);
    }

    @Test
    void adjacentPoseClipsTransitionWithoutManualOverlap() {
        ActorTrack track = new ActorTrack("actor");
        ActorPoseClip first = new ActorPoseClip("first", 0f, 1f);
        first.setOverrideHead(true);
        first.setHeadYaw(0f);
        ActorPoseClip second = new ActorPoseClip("second", 1f, 2f);
        second.setOverrideHead(true);
        second.setHeadYaw(1f);
        track.addClip(first);
        track.addClip(second);

        EntitySample sample = new EntityClipSampler(track).sampleAt(1.5f);

        assertEquals(0.5f, sample.poseHeadYaw, 1.0e-5f);
    }

    @Test
    void isolatedPoseClipKeepsExistingHeldPoseSemantics() {
        ActorTrack track = new ActorTrack("actor");
        ActorPoseClip pose = new ActorPoseClip("pose", 1f, 2f);
        pose.setOverrideHead(true);
        pose.setHeadYaw(0.75f);
        track.addClip(pose);

        EntitySample sample = new EntityClipSampler(track).sampleAt(1.1f);

        assertEquals(0.75f, sample.poseHeadYaw, 1.0e-5f);
    }
}
