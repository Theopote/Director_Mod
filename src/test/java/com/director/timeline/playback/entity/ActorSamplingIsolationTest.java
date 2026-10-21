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
        animation.addEvent(new AnimationEvent(.2f,new EntityActionEvent(EntityActionClip.ActionType.ATTACK,Map.of())));
        track.addClip(animation);var sampler=new EntityClipSampler(track);
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
}
