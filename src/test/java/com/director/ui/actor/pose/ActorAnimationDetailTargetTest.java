package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.director.ui.actor.pose.ActorAnimationDetailTarget.Status.*;

class ActorAnimationDetailTargetTest {
    TimelineModel model; TimelineTrack track; Actor actor; AnimationAsset asset;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        actor=new Actor("Player",ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model=new TimelineModel(new TimelineViewportState()); model.setCurrentTime(1);
        track=track("Actor");
        asset=new AnimationAsset("test:detail","Wave",HumanoidRig.DEFINITION,5,AnimationLoopMode.ONCE);
        AnimationAssetLibrary.getInstance().register(asset);
    }
    @AfterEach void cleanup() {
        ActorRegistryManager.setRegistry(null); AnimationAssetLibrary.getInstance().remove(asset.id());
    }
    TimelineTrack track(String name) {
        var t=new TimelineTrack(name,TrackType.ACTOR);
        t.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor)); model.addTrack(t); return t;
    }
    EntityTimelineClip clip(TimelineTrack owner,float start,float end,String id) {
        var c=new EntityAnimationClip("Animation",start,end); c.setAnimationId(id);
        owner.getActorTrackPayload().addClip(c); var wrapper=new EntityTimelineClip(c,0);
        owner.addClip(wrapper); return wrapper;
    }
    @Test void usesExplicitOwnerAndRejectsAmbiguousOrNonActorSelection() {
        var other=track("Other"); clip(track,0,5,asset.id()); clip(other,0,5,asset.id());
        assertEquals(SELECT,ActorAnimationDetailTarget.resolve(model).status());
        model.selectItem(other,false); assertSame(other,ActorAnimationDetailTarget.resolve(model).track());
        model.selectItem(track,true); assertEquals(AMBIGUOUS,ActorAnimationDetailTarget.resolve(model).status());
        var camera=new TimelineTrack("Camera",TrackType.CAMERA); model.addTrack(camera); model.selectItem(camera,false);
        assertEquals(SELECT,ActorAnimationDetailTarget.resolve(model).status());
    }
    @Test void explicitReferenceDoesNotFallBackToOverlappingAnimation() {
        var selected=clip(track,3,5,"missing:asset"); clip(track,0,10,asset.id());
        model.setSelectedClip(selected);
        assertSame(selected.getEntityClip(),ActorAnimationDetailTarget.resolve(model).clip());
        assertEquals(MISSING_ASSET,ActorAnimationDetailTarget.resolve(model).status());
        ((EntityAnimationClip)selected.getEntityClip()).setAnimationId(asset.id());
        assertEquals(OUTSIDE,ActorAnimationDetailTarget.resolve(model).status());
        model.setCurrentTime(4); assertEquals(READY,ActorAnimationDetailTarget.resolve(model).status());
        selected.getEntityClip().setMuted(true); assertEquals(READ_ONLY,ActorAnimationDetailTarget.resolve(model).status());
    }
    @Test void reportsBindingCapabilityAndReadOnlyWithoutCreatingData() {
        model.selectItem(track,false); assertEquals(NO_ANIMATION,ActorAnimationDetailTarget.resolve(model).status());
        assertTrue(track.getClips().isEmpty());
        clip(track,0,5,asset.id()); track.setLocked(true);
        assertEquals(READ_ONLY,ActorAnimationDetailTarget.resolve(model).status());
        track.getActorTrackPayload().setActorRef(null);
        assertEquals(UNBOUND,ActorAnimationDetailTarget.resolve(model).status());
        var cow=new Actor("Cow",ActorType.COW); ActorRegistryManager.getInstance().registerActor(cow);
        track.getActorTrackPayload().setActorRef(ActorRef.of(cow));
        assertEquals(UNSUPPORTED_RIG,ActorAnimationDetailTarget.resolve(model).status());
    }
}
