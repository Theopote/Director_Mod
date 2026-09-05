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
