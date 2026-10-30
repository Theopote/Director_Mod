package com.director.timeline.playback.entity;

import com.director.actor.Actor;
import com.director.actor.*;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.google.gson.Gson;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnimationEventClockTest {
    private final String id="test:event-clock";
    private AnimationAsset asset;
    private EntityAnimationClip clip;
    private ActorTrack track;
    private AnimationEvent event;
    @BeforeEach void setup() {
        asset=new AnimationAsset(id,"Cycle",ArticulatedPlayerRig.DEFINITION,1,AnimationLoopMode.LOOP);
        AnimationAssetLibrary.getInstance().register(asset);
        track=new ActorTrack();clip=new EntityAnimationClip("Animation",0,4);clip.setAnimationId(id);track.addClip(clip);
        event=new AnimationEvent(.25f,new EntityActionEvent(EntityActionClip.ActionType.ATTACK,Map.of()),AnimationEvent.Timing.ANIMATION);clip.addEvent(event);
    }
    @AfterEach void cleanup() {AnimationAssetLibrary.getInstance().remove(id);ActorRegistryManager.setRegistry(null);}
    @Test void sourceLoopCollectsEveryCrossingWithoutRepeatingSameSample() {
        assertEquals(List.of(.25f,1.25f,2.25f,3.25f),AnimationEventClock.times(track,clip,event,0,4));
        var sampler=new EntityClipSampler(track);assertTrue(sampler.sampleAt(.1f).actionEvents.isEmpty());
        assertEquals(3,sampler.sampleAt(2.3f).actionEvents.size());assertTrue(sampler.sampleAt(2.3f).actionEvents.isEmpty());
        sampler.sampleAt(.1f);assertEquals(1,sampler.sampleAt(.3f).actionEvents.size());
    }
    @Test void pingPongUsesBothForwardAndReverseCrossings() {
        asset.setLoopMode(AnimationLoopMode.PING_PONG);
        assertEquals(List.of(.25f,1.75f,2.25f,3.75f),AnimationEventClock.times(track,clip,event,0,4));
    }
    @Test void legacyClipProgressDoesNotChangeWithAssetSpeed() {
        clip.setSpeed(2);
        var old=new AnimationEvent(.5f,event.getAction());
        assertEquals(List.of(2f),AnimationEventClock.times(track,clip,old,0,4));
        clip.setLoop(false);
        assertEquals(List.of(.125f),AnimationEventClock.times(track,clip,event,0,4));
    }
    @Test void sourceEventsFollowTheSameDistanceClockAsLocomotion() {
        ActorRegistryManager.setRegistry(new ActorRegistry());var actor=new Actor("Player",ActorType.PLAYER);
        ActorRegistryManager.getInstance().registerActor(actor);track.setActorRef(new ActorRef(actor.getActorId()));
        asset.setPurpose(AnimationData.Purpose.LOCOMOTION);
        var route=new ActorRouteClip("Route",0,4);route.setLoopMode(ActorRouteClip.LoopMode.LOOP);
        route.setWaypoints(List.of(new Vector3f(),new Vector3f(16,0,0)));route.setSpeed(actor.getRigProfile().locomotionStride()*.5f);track.addClip(route);
        assertEquals(List.of(.5f,2.5f),AnimationEventClock.times(track,clip,event,0,3));
    }
    @Test void bothTimingModesSurviveTimelinePersistence() {
        clip.addEvent(new AnimationEvent(.5f,event.getAction(),AnimationEvent.Timing.CLIP));
        var gson=new Gson();var dto=gson.fromJson(gson.toJson(TimelineModelPersistence.entityClipToDTO(clip)),TimelineModelPersistence.EntityClipDTO.class);
        var restored=(EntityAnimationClip)TimelineModelPersistence.entityClipFromDTO(dto);
        assertEquals(clip.getEvents(),restored.getEvents());
        assertNotSame(clip.getEvents().getFirst(),restored.getEvents().getFirst());
        var legacy=gson.fromJson("{\"normalizedTime\":0.5,\"action\":{\"type\":\"ATTACK\",\"params\":{}}}",AnimationEvent.class);
        assertEquals(AnimationEvent.Timing.CLIP,legacy.getTiming());
    }
}
