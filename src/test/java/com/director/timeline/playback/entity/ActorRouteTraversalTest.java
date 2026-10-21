package com.director.timeline.playback.entity;
import com.director.timeline.model.entity.ActorRouteClip;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ActorRouteTraversalTest {
    private ActorRouteClip route(ActorRouteClip.LoopMode mode) {
        var route=new ActorRouteClip("Path",0,20);route.setLoopMode(mode);
        route.setWaypoints(List.of(new Vector3f(),new Vector3f(0,0,4)));return route;
    }
    @Test void cumulativeDistanceMapsToLoopAndPingPongWithoutClampingAtFirstEndpoint() {
        var loop=route(ActorRouteClip.LoopMode.LOOP);var ping=route(ActorRouteClip.LoopMode.PING_PONG);
        assertEquals(1,ActorRouteMotion.traversalAt(loop,5).curveDistance());
        assertEquals(3,ActorRouteMotion.traversalAt(ping,5).curveDistance());
        assertTrue(ActorRouteMotion.traversalAt(ping,5).reversed());
        assertEquals(1,ActorRouteMotion.traversalAt(ping,9).curveDistance());
        assertFalse(ActorRouteMotion.traversalAt(ping,9).reversed());
        assertEquals(4,ActorRouteMotion.traversalAt(ping,4).curveDistance());
        assertTrue(ActorRouteMotion.traversalAt(ping,4).reversed());
        assertEquals(0,ActorRouteMotion.traversalAt(loop,4).curveDistance());
    }
    @Test void traversalMappingMatchesPlaybackPositionAndHeadingAcrossRepeatedSeeks() {
        for(var mode:List.of(ActorRouteClip.LoopMode.LOOP,ActorRouteClip.LoopMode.PING_PONG)) {
            var route=route(mode);route.setSpeed(2);
            var track=new com.director.timeline.model.entity.ActorTrack();track.addClip(new com.director.timeline.model.entity.EntitySpawnClip("Spawn",0,20));track.addClip(route);
            var sampler=new EntityClipSampler(track);
            for(float time:new float[]{0,1.9f,2,2.1f,4,6.3f,1.9f}) {
                var at=ActorRouteMotion.traversalAt(route,ActorRouteMotion.distanceTravelled(route,time));
                var played=sampler.sampleSnapshot(time);
                assertEquals(at.curveDistance(),played.position.z,1e-5f);
                if(at.reversed()) assertEquals(180,Math.abs(played.rotation.x),1e-5f);
                else assertEquals(0,played.rotation.x,1e-5f);
            }
        }
    }
}
