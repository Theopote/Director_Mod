package com.director.timeline.model.entity;

import com.director.actor.ActorRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorSamplingRevisionTest {
    @Test void sampledEditsBumpAndLabelsOrTerrainWarningsDoNot() {
        var track = new ActorTrack("actor");
        long created = track.samplingRevision();
        track.setDisplayName("Renamed");
        track.setColorRgba(0xFF336699);
        assertEquals(created, track.samplingRevision());

        var spawn = new EntitySpawnClip("Spawn", 0, 10);
        track.addClip(spawn);
        long revision = track.samplingRevision();
        assertTrue(revision > created);
        spawn.setPosition(1, 2, 3);
        assertTrue(track.samplingRevision() > revision);

        var pose = new ActorPoseClip("Pose", 0, 10);
        track.addClip(pose);
        revision = track.samplingRevision();
        pose.setHeadPitch(0.4f);
        assertTrue(track.samplingRevision() > revision);

        var route = new ActorRouteClip("Route", 0, 10);
        track.addClip(route);
        revision = track.samplingRevision();
        route.setSpeed(3f);
        assertTrue(track.samplingRevision() > revision);
        revision = track.samplingRevision();
        route.setTerrainWarning("ground");
        route.refreshTerrain();
        assertEquals(revision, track.samplingRevision());

        var animation = new EntityAnimationClip("Animation", 0, 10);
        track.addClip(animation);
        revision = track.samplingRevision();
        animation.setSpeed(1.5f);
        assertTrue(track.samplingRevision() > revision);

        var key = new EntityClipKeyframe(1f);
        spawn.addKeyframe(key);
        revision = track.samplingRevision();
        key.setPosition(4, 5, 6);
        assertTrue(track.samplingRevision() > revision);

        revision = track.samplingRevision();
        track.setActorRef(new ActorRef(UUID.randomUUID()));
        assertTrue(track.samplingRevision() > revision);
    }

    @Test void anotherActorsClipEditBumpsTheGlobalRevisionOnly() {
        var track = new ActorTrack("actor");
        long local = track.samplingRevision();
        long global = ActorTrack.globalSamplingRevision();
        var other = new ActorTrack("other");
        other.addClip(new EntitySpawnClip("Spawn", 0, 1));
        assertEquals(local, track.samplingRevision());
        assertTrue(ActorTrack.globalSamplingRevision() > global);
    }
}
