package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationAssetEditingTest {
    private final AnimationAssetLibrary library = new AnimationAssetLibrary();
    private final TimelineModel model = new TimelineModel(new TimelineViewportState());
    private final TimelineCommandHistory history = new TimelineCommandHistory();

    ActorAnimationAssetEditingTest() { TimelineCommandBridge.bind(history); }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); }

    @Test void embeddedIndependenceRebindsOnlyOwnerAndUndoRedoPreserveVisibleWrapper() {
        var registry=new com.director.actor.ActorRegistry();com.director.actor.ActorRegistryManager.setRegistry(registry);
        var actor=registry.createActor("A",com.director.actor.ActorType.PLAYER);
        var track=new TimelineTrack("Actor",TrackType.ACTOR);var payload=track.getOrCreateActorTrackPayload();
        payload.setActorRef(com.director.actor.ActorRef.of(actor));model.addTrack(track);
        var source=ActorAnimationAssetEditing.create(model,library,"project:shared","Shared",3);
        source.getOrCreateBoneTrack("head").scale().put(1,new Vector3f(2),InterpolationMode.LINEAR);
        var spawn=new com.director.timeline.model.entity.EntitySpawnClip("Actor",0,3);spawn.setBoneAnimationId(source.id());
        spawn.setPreset(new com.director.timeline.model.entity.ActorPresetBinding(source.id(),false,1,
                com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT));
        payload.addClip(spawn);var wrapper=new com.director.timeline.model.entity.EntityTimelineClip(spawn,0);track.addClip(wrapper);
        var child=spawn.getBoneAnimation();var childWrapper=child.getTimelineClip();
        var copy=ActorAnimationAssetEditing.makeIndependent(model,library,track,child,"project:copy","Copy");
        assertEquals(copy.id(),spawn.getBoneAnimationId());assertNotSame(child,spawn.getBoneAnimation());
        assertNotSame(childWrapper,spawn.getBoneAnimation().getTimelineClip());assertSame(wrapper,spawn.getTimelineClip());
        assertEquals(source.id(),spawn.getPreset().animationId());assertEquals(1,track.getClips().size());
        copy.getOrCreateBoneTrack("head").scale().put(1,new Vector3f(3),InterpolationMode.LINEAR);
        assertEquals(2,source.sample(1).localTransform("head").scale().x);
        assertThrows(IllegalArgumentException.class,()->ActorAnimationAssetEditing.delete(model,library,source.id()));
        assertTrue(history.undo());assertEquals(source.id(),spawn.getBoneAnimationId());assertFalse(library.contains(copy.id()));
        assertTrue(history.redo());assertEquals(copy.id(),spawn.getBoneAnimationId());assertNotSame(child,spawn.getBoneAnimation());
        com.director.actor.ActorRegistryManager.setRegistry(null);
    }

    @Test void duplicateIsIndependentAndCannotOverwriteOriginal() {
        var original = ActorAnimationAssetEditing.create(model, library, "project:a", "A", 3f);
        original.getOrCreateBoneTrack(HumanoidRig.HEAD).scale().put(1f, new Vector3f(2f), InterpolationMode.SMOOTH);
        original.addMarker(new AnimationMarker(1f, "accent"));
        assertThrows(IllegalArgumentException.class, () ->
                ActorAnimationAssetEditing.duplicate(model, library, original.id(), original.id(), "replacement"));
        assertSame(original, library.find(original.id()).orElseThrow());
        var copy = ActorAnimationAssetEditing.duplicate(model, library, original.id(), "project:b", "B");
        copy.getOrCreateBoneTrack(HumanoidRig.HEAD).scale().put(1f, new Vector3f(3f), InterpolationMode.STEP);
        assertEquals(2f, original.getOrCreateBoneTrack(HumanoidRig.HEAD).scale().findAt(1f).orElseThrow().value().x);
        assertEquals(original.markers(), copy.markers());
        assertTrue(history.undo());
        assertFalse(library.contains(copy.id()));
        assertTrue(history.redo());
        assertSame(copy, library.find(copy.id()).orElseThrow());
    }

    @Test void referencedAssetsAreProtectedAndAttachmentUndoUpdatesBothRepresentations() {
        var asset = ActorAnimationAssetEditing.create(model, library, "project:a", "A", 3f);
        var track = new TimelineTrack("Actor", TrackType.ACTOR);
        var payload = track.getOrCreateActorTrackPayload();
        var registry = new com.director.actor.ActorRegistry();
        com.director.actor.ActorRegistryManager.setRegistry(registry);
        var actor = registry.createActor("Player", com.director.actor.ActorType.PLAYER);
        payload.setActorRef(com.director.actor.ActorRef.of(actor));
        model.addTrack(track);
        ActorAnimationAssetEditing.attach(model, library, asset.id(), track);
        payload.getClips().getFirst().setMuted(true);
        assertEquals(1, ActorAnimationAssetEditing.referenceCount(model, asset.id()));
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.delete(model, library, asset.id()));
        ActorAnimationAssetEditing.rename(model, library, asset.id(), "Renamed");
        assertEquals(1, ActorAnimationAssetEditing.referenceCount(model, asset.id()));
        assertTrue(history.undo());
        assertEquals("A", asset.name());
        assertTrue(history.undo());
        assertTrue(payload.getClips().isEmpty());
        assertTrue(track.getClips().isEmpty());
        ActorAnimationAssetEditing.delete(model, library, asset.id());
        assertFalse(library.contains(asset.id()));
        assertTrue(history.undo());
        assertSame(asset, library.find(asset.id()).orElseThrow());
    }
}
