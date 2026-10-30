package com.director.ui.actor.pose;

import com.director.actor.Actor;
import com.director.actor.*;
import com.director.actor.animation.AnimationData;
import com.director.actor.animation.asset.*;
import com.director.actor.runtime.ActorPresetClock;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.*;
import org.junit.jupiter.api.*;
import org.joml.Vector3f;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationClockRegressionTest {
    private final List<String> ids=new ArrayList<>();
    private EntityActionEvent action(EntityActionClip.ActionType type) {return new EntityActionEvent(type,Map.of());}
    private AnimationAsset asset(String suffix,float duration) {
        var a=new AnimationAsset("audit:"+suffix,"Audit",ArticulatedPlayerRig.DEFINITION,duration,AnimationLoopMode.LOOP);
        AnimationAssetLibrary.getInstance().register(a);ids.add(a.id());return a;
    }
    @AfterEach void cleanup() {ids.forEach(AnimationAssetLibrary.getInstance()::remove);ActorRegistryManager.setRegistry(null);}
    @Test void simultaneousDistinctAnimationEventsShouldBothBeCollected() {
        var track=new ActorTrack();var clip=new EntityAnimationClip("Two events",0,2);
        clip.addEvent(new AnimationEvent(.5f,action(EntityActionClip.ActionType.PLAY_SOUND)));
        clip.addEvent(new AnimationEvent(.5f,action(EntityActionClip.ActionType.THROW_PROP)));
        track.addClip(clip);var sampler=new EntityClipSampler(track);sampler.sampleAt(0);
        assertEquals(2,sampler.sampleAt(1).actionEvents.size());
    }
    @Test void animationHalfwayEventShouldFollowAssetSpeedRatherThanClipLength() {
        var a=asset("event-clock",1);var track=new ActorTrack();var clip=new EntityAnimationClip("Fast animation",0,10);
        clip.setAnimationId(a.id());clip.setSpeed(2);clip.setLoop(false);
        clip.addEvent(new AnimationEvent(.5f,action(EntityActionClip.ActionType.THROW_PROP),AnimationEvent.Timing.ANIMATION));
        track.addClip(clip);var sampler=new EntityClipSampler(track);sampler.sampleAt(0);
        assertEquals(1,sampler.sampleAt(.26f).actionEvents.size());
    }
    @Test void lateSpawnWithoutPresetShouldNotInheritOlderSpawnPreset() {
        var track=new ActorTrack();var older=new EntitySpawnClip("Older",0,5);older.setPreset(ActorPresetBinding.select("wave",null));
        var newer=new EntitySpawnClip("Newer",1,4);track.addClip(older);track.addClip(newer);
        assertNull(new EntityClipSampler(track).sampleSnapshot(2).animationId);
    }
    @Test void locomotionAuthoringTimeShouldMatchVisibleDistancePhase() {
        ActorRegistryManager.setRegistry(new ActorRegistry());var actor=new Actor("Audit",ActorType.PLAYER);
        ActorRegistryManager.getInstance().registerActor(actor);var a=asset("locomotion-clock",2);a.setPurpose(AnimationData.Purpose.LOCOMOTION);
        var model=new TimelineModel(new TimelineViewportState());model.setCurrentTime(1);
        var owner=new TimelineTrack("Audit",TrackType.ACTOR);var track=new ActorTrack();track.setActorRef(new ActorRef(actor.getActorId()));
        owner.setActorTrackPayload(track);model.addTrack(owner);model.selectItem(owner,false);
        var spawn=new EntitySpawnClip("Spawn",0,4);track.addClip(spawn);owner.addClip(new EntityTimelineClip(spawn,0));
        var clip=new EntityAnimationClip("Walk",0,4);clip.setAnimationId(a.id());track.addClip(clip);owner.addClip(new EntityTimelineClip(clip,0));
        var route=new ActorRouteClip("Route",0,4);route.setLoopMode(ActorRouteClip.LoopMode.LOOP);
        route.setWaypoints(List.of(new Vector3f(),new Vector3f(16,0,0)));route.setSpeed(actor.getRigProfile().locomotionStride()*.25f);
        track.addClip(route);owner.addClip(new EntityTimelineClip(route,0));
        var sample=new EntityClipSampler(track).sampleSnapshot(1);
        assertTrue(ActorPresetClock.followsDistance(a.id(),sample));
        var visible=ActorPresetClock.resolve(ActorAnimationLayerSample.from(sample),sample,actor.getRigProfile());
        var editing=ActorAnimationEditContext.resolve(model,actor.getActorId());assertNotNull(editing);
        assertEquals(visible.localTime()*visible.speed(),editing.assetTime(),1e-5f);
    }

    @Test void gizmoAndNumericEditorShouldUseSameCorrectionLayer() throws Exception {
        ActorRegistryManager.setRegistry(new ActorRegistry());var actor=new Actor("Audit",ActorType.PLAYER);
        ActorRegistryManager.getInstance().registerActor(actor);
        var manual=asset("manual-layer",4);var later=asset("later-action",4);
        var model=new TimelineModel(new TimelineViewportState());model.setCurrentTime(2);
        var owner=new TimelineTrack("Audit",TrackType.ACTOR);var track=new ActorTrack();track.setActorRef(new ActorRef(actor.getActorId()));
        owner.setActorTrackPayload(track);model.addTrack(owner);model.selectItem(owner,false);
        var spawn=new EntitySpawnClip("Spawn",0,4);spawn.setBoneAnimationId(manual.id());track.addClip(spawn);owner.addClip(new EntityTimelineClip(spawn,0));
        var action=new EntityAnimationClip("Later action",1,4);action.setAnimationId(later.id());track.addClip(action);owner.addClip(new EntityTimelineClip(action,0));
        var numeric=ActorAnimationEditContext.resolve(model,actor.getActorId());assertNotNull(numeric);assertSame(manual,numeric.asset());
        assertSame(numeric.clip(),ActorAnimationTarget.resolve(model,owner));
        model.selectItem(action.getTimelineClip(),false);
        assertSame(action,ActorAnimationTarget.resolve(model,owner));
        assertSame(action,ActorAnimationEditContext.resolve(model,actor.getActorId()).clip());

    }
}
