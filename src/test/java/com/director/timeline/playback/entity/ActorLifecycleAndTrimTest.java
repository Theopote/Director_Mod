package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityClipKeyframe;
import com.director.timeline.model.entity.EntityDespawnClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActorLifecycleAndTrimTest {
    @Test
    void hiddenDepartureSurvivesSkippedEndFrameUntilRespawn() {
        var track = new ActorTrack();
        track.addClip(new EntitySpawnClip("Spawn", 0, 10));
        var departure = new EntityDespawnClip("Hide", 2, 4);
        departure.setMode(EntityDespawnClip.DespawnMode.HIDE);
        track.addClip(departure);
        track.addClip(new EntitySpawnClip("Respawn", 6, 10));

        var sampler = new EntityClipSampler(track);
        assertEquals(1f, sampler.sampleSnapshot(3.95f).visibility);
        var afterDeparture = sampler.sampleSnapshot(4.05f);
        assertTrue(afterDeparture.active);
        assertEquals(0f, afterDeparture.visibility);
        assertEquals(0f, sampler.sampleSnapshot(4f).visibility);
        assertEquals(0f, sampler.sampleSnapshot(5.99f).visibility);
        assertEquals(1f, sampler.sampleSnapshot(6f).visibility);
        assertEquals(1f, sampler.sampleSnapshot(3f).visibility);
    }

    @Test
    void trimmedRootCurveRetainsItsInterpolationAndKeysAfterReload() {
        var spawn = new EntitySpawnClip("Spawn", 0, 10);
        var first = new EntityClipKeyframe(1f);
        first.setPosition(0f, 0f, 0f);
        var second = new EntityClipKeyframe(8f);
        second.setPosition(7f, 0f, 0f);
        spawn.addKeyframe(first);
        spawn.addKeyframe(second);
        spawn.setTimeRange(0, 5);

        var originalTrack = new ActorTrack();
        originalTrack.addClip(spawn);
        var original = new EntityClipSampler(originalTrack).sampleSnapshot(4f).position;
        assertTrue(original.x > 0f && original.x < 7f);

        var gson = new Gson();
        String json = gson.toJson(TimelineModelPersistence.entityClipToDTO(spawn));
        var dto = gson.fromJson(json, TimelineModelPersistence.EntityClipDTO.class);
        var restored = TimelineModelPersistence.entityClipFromDTO(dto);
        assertEquals(spawn.getId(), restored.getId());
        var restoredTrack = new ActorTrack();
        restoredTrack.addClip(restored);
        var sampler = new EntityClipSampler(restoredTrack);
        assertEquals(original, sampler.sampleSnapshot(4f).position);
        assertEquals(2, restored.getKeyframes().size());
        assertEquals(first.getId(), restored.getKeyframes().getFirst().getId());
        assertEquals(second.getId(), restored.getKeyframes().getLast().getId());

        restored.setTimeRange(0, 10);
        assertEquals(7f, sampler.sampleSnapshot(8f).position.x);
    }
}
