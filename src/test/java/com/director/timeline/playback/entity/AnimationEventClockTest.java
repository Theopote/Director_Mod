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

    private ActorRouteClip distanceRoute() {
        ActorRegistryManager.setRegistry(new ActorRegistry());var actor=new Actor("Player",ActorType.PLAYER);
        ActorRegistryManager.getInstance().registerActor(actor);track.setActorRef(new ActorRef(actor.getActorId()));
        asset.setPurpose(AnimationData.Purpose.LOCOMOTION);
        var route=new ActorRouteClip("Route",0,4);route.setLoopMode(ActorRouteClip.LoopMode.LOOP);
        route.setWaypoints(List.of(new Vector3f(),new Vector3f(16,0,0)));
        route.setSpeed(actor.getRigProfile().locomotionStride()*.5f);track.addClip(route);
        return route;
    }

    @Test void batchReusesOnePureSamplerAndExactSamplesAcrossKeys() {
        distanceRoute();
        var creations=new java.util.concurrent.atomic.AtomicInteger();
        var samples=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Function<ActorTrack,EntityClipSampler> factory=owner->{
            creations.incrementAndGet();
            return new EntityClipSampler(owner) {
                @Override public EntitySample sampleSnapshot(float time) {
                    samples.incrementAndGet();return super.sampleSnapshot(time);
                }
            };
        };
        var batch=new AnimationEventClock.Projection(track,clip,0,3,factory);
        var first=batch.times(.25f);assertEquals(List.of(.5f,2.5f),first);
        int beforeRepeat=samples.get();assertSame(first,batch.times(.25f));assertEquals(beforeRepeat,samples.get());
        assertEquals(List.of(1f,3f),batch.times(.5f));assertEquals(1,creations.get());
        int batchedSamples=samples.get();samples.set(0);
        new AnimationEventClock.Projection(track,clip,0,3,factory).times(.25f);
        new AnimationEventClock.Projection(track,clip,0,3,factory).times(.5f);
        assertTrue(batchedSamples<samples.get(),"A batch must do less sampling than independent key projections");
    }

    @Test void clipTimedEventsNeverConstructAnActorSampler() {
        distanceRoute();
        var batch=new AnimationEventClock.Projection(track,clip,0,3,owner->{throw new AssertionError("Clip events need no sampler");});
        assertEquals(List.of(2f),batch.times(new AnimationEvent(.5f,event.getAction(),AnimationEvent.Timing.CLIP)));
    }

    @Test void freshBatchObservesRouteEditsAndDoesNotRetainThePreviousTrack() {
        var route=distanceRoute();
        assertEquals(List.of(.5f,2.5f),AnimationEventClock.projection(track,clip,0,3).times(.25f));
        route.setSpeed(route.getSpeed()*2);
        assertEquals(List.of(.25f,1.25f,2.25f),AnimationEventClock.projection(track,clip,0,3).times(.25f));
        var other=new ActorTrack();other.addClip(clip);
        assertEquals(List.of(.25f,1.25f,2.25f),AnimationEventClock.projection(other,clip,0,3).times(.25f));
        clip.setSpeed(2);
        assertEquals(List.of(.125f,.625f,1.125f,1.625f,2.125f,2.625f),AnimationEventClock.projection(other,clip,0,3).times(.25f));
    }

    @Test void batchPreservesSubFramePrecisionAndEveryPingPongCrossing() {
        clip.setSpeed(1.37f);asset.setLoopMode(AnimationLoopMode.PING_PONG);
        var batch=AnimationEventClock.projection(track,clip,0,4);
        float normalized=.49317f;
        var actual=batch.times(normalized);
        assertEquals(5,actual.size());
        for(int i=0;i<actual.size();i++) {
            double cycle=i/2;
            double phase=i%2==0?normalized:2d-normalized;
            assertEquals((cycle*2+phase)/clip.getSpeed(),actual.get(i),1e-6);
        }
        assertSame(actual,batch.times(normalized));
    }

    @Test void batchedQueriesDoNotConsumePlaybackEvents() {
        distanceRoute();var sampler=new EntityClipSampler(track);
        sampler.sampleAt(.1f);
        var batch=AnimationEventClock.projection(track,clip,0,3);batch.times(.25f);batch.times(.5f);
        assertEquals(2,sampler.sampleAt(2.6f).actionEvents.size());
        assertTrue(sampler.sampleAt(2.6f).actionEvents.isEmpty());
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
