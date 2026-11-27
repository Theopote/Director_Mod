package com.director.timeline.model.entity;

import com.director.actor.ActorRef;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActorSamplingRevisionTest {
    @Test void transformEndpointsAndNestedEventParamsCannotBypassRevision() {
        var track = new ActorTrack("actor");
        var transform = new EntityTransformClip("Transform", 0, 1);
        var event = new EntityActionClip("Event", 0, 1);
        track.addClip(transform);
        track.addClip(event);
        long revision = track.samplingRevision();
        transform.getToPos().set(8, 9, 10);
        assertEquals(new Vector3f(), transform.getToPos());
        assertEquals(revision, track.samplingRevision());
        transform.setToPos(8, 9, 10);
        assertTrue(track.samplingRevision() > revision);

        revision = track.samplingRevision();
        var list = new ArrayList<>(List.of("first"));
        event.setParams(Map.of("meta", Map.of("list", list)));
        assertTrue(track.samplingRevision() > revision);
        revision = track.samplingRevision();
        list.add("second");
        var stored = (List<?>) ((Map<?, ?>) event.getParam("meta")).get("list");
        assertEquals(List.of("first"), stored);
        assertThrows(UnsupportedOperationException.class, stored::clear);
        assertThrows(UnsupportedOperationException.class, () -> event.getParams().clear());
        assertEquals(revision, track.samplingRevision());
    }
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
