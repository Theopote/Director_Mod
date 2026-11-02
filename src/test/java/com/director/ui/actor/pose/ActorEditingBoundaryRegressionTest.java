package com.director.ui.actor.pose;

import com.director.actor.Actor;
import com.director.actor.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import org.joml.Vector3f;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorEditingBoundaryRegressionTest {
    TimelineModel model;TimelineTrack owner;Actor actor;EntitySpawnClip spawn;String id;TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(new ActorRegistry());actor=new Actor("Audit",ActorType.PLAYER);ActorRegistryManager.getInstance().registerActor(actor);
        model=new TimelineModel(new TimelineViewportState());model.setCurrentTime(1);
        owner=new TimelineTrack("Audit",TrackType.ACTOR);var payload=new ActorTrack();payload.setActorRef(new ActorRef(actor.getActorId()));
        owner.setActorTrackPayload(payload);model.addTrack(owner);model.selectItem(owner,false);
        spawn=new EntitySpawnClip("Spawn",0,5);payload.addClip(spawn);owner.addClip(new EntityTimelineClip(spawn,0));
        history=new TimelineCommandHistory();TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() {if(id!=null) AnimationAssetLibrary.getInstance().remove(id);TimelineCommandBridge.bind(null);ActorRegistryManager.setRegistry(null);}
    EntityClipKeyframe scaleKey() {
        var key=new EntityClipKeyframe(0,TimelineInterpolation.LINEAR);key.setChannels(EntityClipKeyframe.SCALE);key.setScale(2,2,2);spawn.addKeyframe(key);return key;
    }
    @Test void embeddedSelectionSupportsKeyCommandsWithoutDeletingSpawn() {
        id="audit:embedded-commands";
        var asset=new AnimationAsset(id,"Manual",ArticulatedPlayerRig.DEFINITION,5,AnimationLoopMode.ONCE);
        AnimationAssetLibrary.getInstance().register(asset);spawn.setBoneAnimationId(id);
        asset.getOrCreateBoneTrack("head").rotation().put(1,new org.joml.Quaternionf(),InterpolationMode.LINEAR);
        var clip=spawn.getBoneAnimation();
        model.setSelectedClip(spawn.getTimelineClip());
        assertSame(clip,ActorEmbeddedAnimation.selectedReference(model,owner));
        var key=new ActorAnimationAuthoringService.Key("head",ActorAnimationAuthoringService.Channel.ROTATION,1);
        var selection=ActorKeyframeSelection.getInstance();
        selection.replace(model,owner,clip,asset,List.of(key),true);
        assertNotNull(selection.snapshot(model));
        var rows=new com.director.ui.timeline.render.ActorBoneTimelineRows();
        assertTrue(rows.handleKeyCommand(model,"delete"));
        assertTrue(asset.findBoneTrack("head").orElseThrow().rotation().findAt(1).isEmpty());
        assertTrue(owner.getActorTrackPayload().getClips().contains(spawn));
        assertTrue(owner.getClips().contains(spawn.getTimelineClip()));
        assertTrue(history.undo());
        assertTrue(asset.findBoneTrack("head").orElseThrow().rotation().findAt(1).isPresent());
        selection.replace(model,owner,clip,asset,List.of(key),true);
        owner.setLocked(true);
        assertTrue(rows.handleKeyCommand(model,"delete"));
        assertTrue(asset.findBoneTrack("head").orElseThrow().rotation().findAt(1).isPresent());
        owner.setLocked(false);
        owner.getClips().remove(spawn.getTimelineClip());
        assertNull(ActorEmbeddedAnimation.selectedReference(model,owner));
        assertNull(selection.snapshot(model));
    }
    @Test void selectedReferenceDoesNotFallBackToAnUnselectedActiveAnimation() {
        var animation=new EntityAnimationClip("Action",0,5);
        owner.getActorTrackPayload().addClip(animation);
        owner.addClip(new EntityTimelineClip(animation,0));
        model.setSelectedClip(spawn.getTimelineClip());
        assertNull(ActorEmbeddedAnimation.selectedReference(model,owner));
        model.setSelectedClip(animation.getTimelineClip());
        assertSame(animation,ActorEmbeddedAnimation.selectedReference(model,owner));
        assertSame(animation.getTimelineClip(),ActorEmbeddedAnimation.visibleOwner(owner,animation));
    }
    @Test void assetBrowserShouldRejectUsingManualAssetAsItsOwnPreset() {
        id="audit:self-preset";var a=new AnimationAsset(id,"Manual",ArticulatedPlayerRig.DEFINITION,5,AnimationLoopMode.ONCE);
        AnimationAssetLibrary.getInstance().register(a);spawn.setBoneAnimationId(id);
        assertThrows(IllegalArgumentException.class,()->ActorAnimationAssetEditing.applyPreset(model,AnimationAssetLibrary.getInstance(),id,owner));
    }
    @Test void routeShouldStillAllowPastingScaleOnlyRootKeys() {
        var source=scaleKey();var route=new ActorRouteClip("Route",0,5);route.setWaypoints(List.of(new Vector3f(),new Vector3f(8,0,0)));
        owner.getActorTrackPayload().addClip(route);owner.addClip(new EntityTimelineClip(route,0));
        assertNotNull(ActorRootKeyframeEditing.writeScale(model,owner,spawn,new Vector3f(3)));
        var clipboard=new ActorRootKeyframeClipboard();assertTrue(clipboard.copyChannels(spawn,List.of(source),EntityClipKeyframe.SCALE));
        model.setCurrentTime(2);assertEquals(1,clipboard.pasteAll(model,owner,spawn).size());
    }
    @Test void channelPasteShouldMergeAtTimeWithAnUnrelatedChannel() {
        var source=scaleKey();var position=new EntityClipKeyframe(2,TimelineInterpolation.LINEAR);position.setChannels(EntityClipKeyframe.POSITION);position.setPosition(3,4,5);spawn.addKeyframe(position);
        var clipboard=new ActorRootKeyframeClipboard();assertTrue(clipboard.copyChannels(spawn,List.of(source),EntityClipKeyframe.SCALE));
        model.setCurrentTime(2);assertEquals(1,clipboard.pasteAll(model,owner,spawn).size());
        assertEquals(5,position.getChannels());assertEquals(new Vector3f(3,4,5),position.getPosition());
        assertTrue(history.undo());assertEquals(1,position.getChannels());assertEquals(new Vector3f(3,4,5),position.getPosition());
        assertTrue(history.redo());assertEquals(5,position.getChannels());assertEquals(new Vector3f(2),position.getScale());
    }

    @Test void selectedJointMirrorWritesOnlyTheOppositeAuthoredChannel() {
        id="audit:joint-mirror";var a=new AnimationAsset(id,"Manual",ArticulatedPlayerRig.DEFINITION,5,AnimationLoopMode.ONCE);
        AnimationAssetLibrary.getInstance().register(a);spawn.setBoneAnimationId(id);
        a.getOrCreateBoneTrack(ArticulatedPlayerRig.RIGHT_FOREARM).rotation().put(1,new org.joml.Quaternionf().rotateX(-.8f),InterpolationMode.LINEAR);
        var key=new ActorAnimationAuthoringService.Key(ArticulatedPlayerRig.RIGHT_FOREARM,ActorAnimationAuthoringService.Channel.ROTATION,1);
        assertTrue(ActorAnimationAuthoringService.mirrorSelection(ActorAnimationEditContext.resolve(model,actor.getActorId()),List.of(key)));
        assertEquals(2,a.boneTracks().size());assertTrue(a.findBoneTrack(HumanoidRig.HEAD).isEmpty());
        assertTrue(a.findBoneTrack(ArticulatedPlayerRig.LEFT_FOREARM).orElseThrow().rotation().findAt(1).isPresent());
        assertTrue(history.undo());assertTrue(a.findBoneTrack(ArticulatedPlayerRig.LEFT_FOREARM).isEmpty());
    }
}
