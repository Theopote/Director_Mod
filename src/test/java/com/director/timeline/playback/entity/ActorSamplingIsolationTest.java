package com.director.timeline.playback.entity;
import com.director.timeline.model.entity.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ActorSamplingIsolationTest {
    @Test void snapshotQueriesNeverEmitOrConsumePlaybackEvents() {
        var track=new ActorTrack("actor");track.addClip(new EntitySpawnClip("Spawn",0,10));
        track.addClip(new EntityActionClip("event",0,10));
        var animation=new EntityAnimationClip("animation",0,10);
        animation.setAnimationId("wave");
        animation.addEvent(new AnimationEvent(.2f,new EntityActionEvent(EntityActionClip.ActionType.ATTACK,Map.of())));
        track.addClip(animation);
        track.addClip(new EntityAnimationClip("Unbound",1,10));
        var sampler=new EntityClipSampler(track);
        assertEquals("wave", sampler.sampleSnapshot(3).animationId);
        assertTrue(sampler.sampleSnapshot(3).actionEvents.isEmpty());
        assertEquals(2,sampler.sampleAt(3).actionEvents.size());
        for(float time:new float[]{7,0,3}) assertTrue(sampler.sampleSnapshot(time).actionEvents.isEmpty());
        assertTrue(sampler.sampleAt(3).actionEvents.isEmpty());
        sampler.clearAllCaches();assertEquals(2,sampler.sampleAt(3).actionEvents.size());
    }
    @Test void curveCacheInvalidatesOnCoordinatesModeAndMutationWithoutReturningMutableData() {
        var clip=new ActorRouteClip("Curve",0,10);
        clip.setWaypoints(List.of(new Vector3f(),new Vector3f(2,0,3),new Vector3f(5,0,0)));
        var first=ActorRouteSampler.curveOf(clip);assertSame(first,ActorRouteSampler.curveOf(clip));
        assertThrows(UnsupportedOperationException.class,()->first.clear());
        clip.setInterpolationMode(ActorRouteClip.InterpolationMode.CATMULL_ROM);
        var curved=ActorRouteSampler.curveOf(clip);assertNotSame(first,curved);
        assertEquals(ActorRouteSampler.computeCurveLength(clip.getWaypoints(),clip.getInterpolationMode()),ActorRouteSampler.lengthOf(clip),1e-5f);
        clip.getWaypoints().get(1).x=4;
        assertNotSame(curved,ActorRouteSampler.curveOf(clip));
        var expected=ActorRouteSampler.sampleAtDistance(clip.getWaypoints(),2,clip.getInterpolationMode());
        assertTrue(expected.position().equals(ActorRouteSampler.sampleAtDistance(clip,2).position(),1e-5f));
    }
    @Test void actionsCannotExtendActorPresenceBeyondSpawnInterval() {
        var track=new ActorTrack("actor");track.addClip(new EntitySpawnClip("Spawn",0,2));
        track.addClip(new ActorActionClip("Run",0,10));var sampler=new EntityClipSampler(track);
        assertTrue(sampler.sampleSnapshot(1).active);assertFalse(sampler.sampleSnapshot(3).active);
    }

    @Test void eventFiresOncePerVisitAndSnapshotsDoNotEndTheVisit() {
        var track = new ActorTrack("Actor");
        track.addClip(new EntitySpawnClip("Spawn", 0, 8));
        var event = new EntityActionClip("Event", 2, 4);
        track.addClip(event);
        var sampler = new EntityClipSampler(track);
        assertEquals(1, sampler.sampleAt(2).actionEvents.size());
        sampler.sampleSnapshot(5);
        assertTrue(sampler.sampleAt(3).actionEvents.isEmpty());
        assertTrue(sampler.sampleAt(2.5f).actionEvents.isEmpty());
        assertTrue(sampler.sampleAt(5).actionEvents.isEmpty());
        assertEquals(1, sampler.sampleAt(2).actionEvents.size());
        assertTrue(sampler.sampleAt(20).actionEvents.isEmpty());
        assertEquals(1, sampler.sampleAt(2).actionEvents.size());
        event.setMuted(true);
        assertTrue(sampler.sampleAt(2).actionEvents.isEmpty());
        event.setMuted(false);
        assertEquals(1, sampler.sampleAt(2).actionEvents.size());
    }

    @Test void angleInterpolationWrapsAndTerminatesForExtremeInputs() {
        assertEquals(0f, EntitySample.lerpAngle(350, 10, .5f));
        assertEquals(0f, EntitySample.lerpAngle(10, 350, .5f));
        assertEquals(10f, EntitySample.lerpAngle(Float.NaN, 20, .5f));
        assertEquals(10f, EntitySample.lerpAngle(20, Float.POSITIVE_INFINITY, .5f));
        float extreme = EntitySample.lerpAngle(Float.MAX_VALUE, -Float.MAX_VALUE, .5f);
        assertTrue(Float.isFinite(extreme) && extreme >= 0f && extreme < 360f);
        var key = new EntityClipKeyframe(1);
        key.setRotation(10, 20, 30);
        key.setRotation(Float.NaN, 0, 0);
        assertEquals(new Vector3f(10, 20, 30), key.getRotation());
    }

    @Test void clearingActorKeysInvalidatesTrackSampling() {
        var track = new ActorTrack("Actor");
        var spawn = new EntitySpawnClip("Spawn", 0, 4);
        track.addClip(spawn);
        var key = new EntityClipKeyframe(2);
        key.setPosition(3, 0, 0);
        spawn.addKeyframe(key);
        var sampler = new EntityClipSampler(track);
        assertEquals(3f, sampler.sampleSnapshot(2).position.x);
        long revision = track.samplingRevision();
        spawn.clearKeyframes();
        assertTrue(track.samplingRevision() > revision);
        assertEquals(0f, sampler.sampleSnapshot(2).position.x);
    }
}
