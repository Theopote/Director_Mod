package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorLifecycleDeletionTest {
    @Test void remainingPoseCannotKeepDeletedActorAliveAndUndoRestoresIt() {
        ActorTrack track = new ActorTrack("actor");
        EntitySpawnClip spawn = new EntitySpawnClip("actor", 0f, 5f);
        track.addClip(spawn);
        track.addClip(new ActorPoseClip("pose", 0f, 5f));
        EntityClipSampler sampler = new EntityClipSampler(track);
        assertTrue(sampler.sampleAt(2f).active);
        track.removeClip(spawn.getId());
        assertFalse(sampler.sampleAt(2f).active);
        track.addClip(spawn);
        assertTrue(sampler.sampleAt(2f).active);
        spawn.setMuted(true);
        assertFalse(sampler.sampleAt(2f).active);
    }

    @Test void outsideLifecycleIsInactiveEvenWithLongerActionClip() {
        ActorTrack track = new ActorTrack("actor");
        track.addClip(new EntitySpawnClip("actor", 1f, 3f));
        track.addClip(new ActorPoseClip("pose", 0f, 5f));
        EntityClipSampler sampler = new EntityClipSampler(track);
        assertFalse(sampler.sampleAt(.5f).active);
        assertTrue(sampler.sampleAt(2f).active);
        assertFalse(sampler.sampleAt(4f).active);
    }
}
