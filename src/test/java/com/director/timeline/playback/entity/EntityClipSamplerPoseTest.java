package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.ActorPoseClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntitySpawnClip;
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

    @Test void firstPoseDoesNotBlendFromAPreviousPerformance() {
        var track = new ActorTrack("Actor");
        var earlier = new EntitySpawnClip("Earlier", 0, 3);
        var current = new EntitySpawnClip("Current", 3, 6);
        track.addClip(earlier);
        track.addClip(current);
        var outgoing = headPose("Outgoing", 1, 3, 0);
        var incoming = headPose("Incoming", 3.2f, 4.8f, 1);
        earlier.addPoseInterval(outgoing);
        current.addPoseInterval(incoming);
        var sampler = new EntityClipSampler(track);
        assertEquals(1f, sampler.sampleSnapshot(3.5f).poseHeadYaw);
        earlier.removePoseInterval(outgoing);
        track.addClip(outgoing);
        assertEquals(1f, sampler.sampleSnapshot(3.5f).poseHeadYaw);
    }

    @Test void fullyTrimmedPoseStopsContributingHistoryUntilRevealed() {
        var track = new ActorTrack("Actor");
        var spawn = new EntitySpawnClip("Performance", 0, 8);
        spawn.addPoseInterval(headPose("Previous", 0, 1, 0));
        spawn.addPoseInterval(headPose("Active", 3, 5, 1));
        track.addClip(spawn);
        var sampler = new EntityClipSampler(track);
        assertEquals(.5f, sampler.sampleSnapshot(4).poseHeadYaw);
        spawn.setTimeRange(2, 8);
        assertEquals(1f, sampler.sampleSnapshot(4).poseHeadYaw);
        spawn.setTimeRange(0, 8);
        assertEquals(.5f, sampler.sampleSnapshot(4).poseHeadYaw);
    }

    private static ActorPoseClip headPose(String name, float start, float end, float yaw) {
        var pose = new ActorPoseClip(name, start, end);
        pose.setOverrideHead(true);
        pose.setHeadYaw(yaw);
        return pose;
    }
}
