package com.director.actor.runtime;

import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import com.director.actor.action.ActorActionType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorRouteStopTurnTest {
    private ActorRouteClip route() {
        var route=new ActorRouteClip("Turn",0,10);route.setLoopMode(ActorRouteClip.LoopMode.PING_PONG);
        route.setWaypoints(List.of(new Vector3f(),new Vector3f(0,0,4)));route.setSpeed(2);route.setEndpointTurnDuration(.4f);
        return route;
    }
    @Test void endpointTurnHoldsPositionAndTravelThenReturnsAlongCurve() {
        var route=route();var mid=ActorRouteMotion.stopTurn(route,1);
        assertEquals(2,mid.curveDistance(),1e-5f);assertEquals(3,mid.speed(),1e-5f);
        for(float time:new float[]{2,2.1f,2.2f,2.3f}) {
            var turn=ActorRouteMotion.stopTurn(route,time);
            assertEquals(4,turn.curveDistance(),1e-5f);assertEquals(4,turn.travelledDistance(),1e-5f);assertEquals(0,turn.speed());
        }
        var back=ActorRouteMotion.stopTurn(route,3.4f);assertTrue(back.reversed());
        assertEquals(2,back.curveDistance(),1e-5f);assertEquals(6,back.travelledDistance(),1e-5f);
    }
    @Test void headingProgressesThroughHalfTurnAndMatchesBothTravelLegs() {
        var route=route();
        assertTrue(ActorRouteMotion.stopTurnFacing(route,ActorRouteMotion.stopTurn(route,2)).equals(new Vector3f(0,0,1),1e-5f));
        assertTrue(ActorRouteMotion.stopTurnFacing(route,ActorRouteMotion.stopTurn(route,2.2f)).equals(new Vector3f(-1,0,0),1e-4f));
        assertTrue(ActorRouteMotion.stopTurnFacing(route,ActorRouteMotion.stopTurn(route,2.4f)).equals(new Vector3f(0,0,-1),1e-4f));
        for(float time:new float[]{1.999f,2,2.001f,2.399f,2.4f,2.401f,4.8f}) {
            var a=ActorRouteMotion.stopTurnFacing(route,ActorRouteMotion.stopTurn(route,time));
            var b=ActorRouteMotion.stopTurnFacing(route,ActorRouteMotion.stopTurn(route,time+.0001f));
            assertTrue(a.isFinite());assertTrue(a.angle(b)<.01f);
        }
    }
    @Test void gaitPhasePausesDuringTurnAndReplayDoesNotAccumulateState() {
        var route=route();var sample=new EntitySample();sample.routeClip=route;sample.action=ActorActionType.WALK;
        var profile=new com.director.actor.rig.HumanoidRigProfile();
        sample.routeLocalTime=2.1f;float phase=ActorRouteGait.phaseCycles(sample,profile);
        sample.routeLocalTime=2.3f;assertEquals(phase,ActorRouteGait.phaseCycles(sample,profile));
        assertEquals(0,ActorRouteGait.motionWeight(sample));
        var before=ActorRouteMotion.stopTurn(route,3.4f);ActorRouteMotion.stopTurn(route,8);
        assertEquals(before,ActorRouteMotion.stopTurn(route,3.4f));
        route.setEndpointTurnDuration(0);assertEquals(6.8f,ActorRouteMotion.distanceTravelled(route,3.4f),1e-5f);
    }
    @Test void samplerUsesTurnMotionAndRespectsUserRotationPriority() {
        var payload=new ActorTrack("Actor");var spawn=new EntitySpawnClip("Actor",0,10);spawn.setYawDeg(13);payload.addClip(spawn);
        var route=route();payload.addClip(route);var sampler=new EntityClipSampler(payload);
        var sample=sampler.sampleSnapshot(2.2f);assertEquals(4,sample.position.z,1e-5f);assertEquals(0,sample.speed);
        assertEquals(90,sample.rotation.x,1e-3f);
        route.setAlignRotationToRoute(false);assertEquals(13,sampler.sampleSnapshot(2.2f).rotation.x);
        route.setAlignRotationToRoute(true);
        var transform=new EntityTransformClip("Manual",0,10);transform.setFromRot(37,0,0);transform.setToRot(37,0,0);payload.addClip(transform);
        assertEquals(37,sampler.sampleSnapshot(2.2f).rotation.x);
    }
    @Test void turnSettingPersistsAndRejectsNonFiniteValues() {
        var route=route();var dto=com.director.timeline.persistence.TimelineModelPersistence.entityClipToDTO(route);
        var loaded=(ActorRouteClip)com.director.timeline.persistence.TimelineModelPersistence.entityClipFromDTO(dto);
        assertEquals(.4f,loaded.getEndpointTurnDuration());assertTrue(loaded.hasEndpointTurns());
        assertEquals(ActorRouteMotion.stopTurn(route,2.2f),ActorRouteMotion.stopTurn(loaded,2.2f));
        assertThrows(IllegalArgumentException.class,()->route.setEndpointTurnDuration(Float.NaN));
        assertThrows(IllegalArgumentException.class,()->route.setEndpointTurnDuration(-1));
        dto.routeEndpointTurnDuration=Float.NaN;
        var recovered=(ActorRouteClip)com.director.timeline.persistence.TimelineModelPersistence.entityClipFromDTO(dto);
        assertEquals(0,recovered.getEndpointTurnDuration());assertEquals(route.getWaypoints(),recovered.getWaypoints());
        route.setLoopMode(ActorRouteClip.LoopMode.ONCE);assertFalse(route.hasEndpointTurns());
    }
}
