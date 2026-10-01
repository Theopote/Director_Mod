package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationAssetLifecycleTest {
    private TimelineModel model;
    private AnimationAssetLibrary library;
    private TimelineCommandHistory history;
    private Actor actor;
    private TimelineTrack track;
    private AnimationAsset source;
    @TempDir Path temp;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        library = new AnimationAssetLibrary(); history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        track = track(actor.getActorId());
        source = new AnimationAsset("project:source", "Source", HumanoidRig.DEFINITION, 4, AnimationLoopMode.LOOP);
        source.getOrCreateBoneTrack("head").rotation().put(1, new Quaternionf().rotateY(.5f), InterpolationMode.STEP);
        source.getOrCreateBoneTrack("root").translation().put(1, new Vector3f(1,2,3), InterpolationMode.LINEAR);
        source.addMarker(new AnimationMarker(1, "accent")); library.register(source);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    private TimelineTrack track(UUID actorId) {
        var target = new TimelineTrack("Actor", TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actorId)); target.setActorTrackPayload(payload); model.addTrack(target); return target;
    }
    private EntityAnimationClip bind(TimelineTrack target, String id, float start) {
        var clip = new EntityAnimationClip("Clip", start, start + 8); clip.setAnimationId(id);
        target.getActorTrackPayload().addClip(clip);
        target.addClip(new EntityTimelineClip(clip, target.getActorTrackPayload().getColorRgba())); return clip;
    }
    @Test void independentCopyRebindsOnlyOneClipAndUndoRestoresBothAssetAndBinding() {
        var clip = bind(track, source.id(), 0); clip.setSpeed(1.5f); clip.setLoop(false);
        var wrapper = clip.getTimelineClip(); var other = bind(track(actor.getActorId()), source.id(), 0);
        var copy = ActorAnimationAssetEditing.makeIndependent(model, library, track, clip, "project:private", "Private");
        assertEquals(copy.id(), clip.getAnimationId()); assertEquals(source.id(), other.getAnimationId());
        assertSame(wrapper, clip.getTimelineClip()); assertEquals(1.5f, clip.getSpeed()); assertFalse(clip.isLoop());
        assertEquals(0, clip.getStartTime()); assertEquals(8, clip.getEndTime());
        assertEquals(source.markers(), copy.markers()); assertEquals(source.loopMode(), copy.loopMode());
        copy.getOrCreateBoneTrack("root").translation().put(1, new Vector3f(9), InterpolationMode.SMOOTH);
        assertEquals(new Vector3f(1,2,3), source.findBoneTrack("root").orElseThrow().translation().findAt(1).orElseThrow().value());
        assertTrue(history.undo()); assertFalse(library.contains(copy.id())); assertEquals(source.id(), clip.getAnimationId());
        assertEquals(EntityAnimationClip.AnimationSource.BUILT_IN, clip.getSource()); assertFalse(history.undo());
        assertTrue(history.redo()); assertSame(copy, library.find(copy.id()).orElseThrow());
        assertSame(wrapper, clip.getTimelineClip()); assertEquals(copy.id(), clip.getAnimationId());
    }
    @Test void failedIndependentUndoPreservesDataAndHistoryUntilExternalReferenceIsRemoved() {
        var clip = bind(track, source.id(), 0);
        var copy = ActorAnimationAssetEditing.makeIndependent(model, library, track, clip, "project:private", "Private");
        var otherTrack = track(actor.getActorId()); var external = bind(otherTrack, copy.id(), 0); external.setMuted(true);
        assertThrows(IllegalArgumentException.class, history::undo);
        assertEquals(1, history.getUndoSize()); assertEquals(0, history.getRedoSize());
        assertSame(copy, library.find(copy.id()).orElseThrow()); assertEquals(copy.id(), clip.getAnimationId());
        otherTrack.getActorTrackPayload().removeClip(external.getId()); otherTrack.removeClip(external.getTimelineClip());
        assertTrue(history.undo()); assertEquals(source.id(), clip.getAnimationId()); assertFalse(library.contains(copy.id()));
    }
    @Test void failedRedoDoesNotConsumeHistoryOrOverwriteConflictingAsset() {
        var clip = bind(track, source.id(), 0);
        var copy = ActorAnimationAssetEditing.makeIndependent(model, library, track, clip, "project:private", "Private");
        assertTrue(history.undo());
        var conflict = new AnimationAsset(copy.id(), "Other", HumanoidRig.DEFINITION, 1, AnimationLoopMode.ONCE); library.register(conflict);
        assertThrows(IllegalArgumentException.class, history::redo); assertSame(conflict, library.find(copy.id()).orElseThrow());
        assertEquals(source.id(), clip.getAnimationId()); assertEquals(1, history.getRedoSize()); assertEquals(0, history.getUndoSize());
        library.remove(copy.id()); assertTrue(history.redo()); assertSame(copy, library.find(copy.id()).orElseThrow());
    }
    @Test void duplicateAttachIsRejectedAndOverlapPreflightNeverMutatesTracks() {
        var other = new AnimationAsset("project:other", "Other", HumanoidRig.DEFINITION, 4, AnimationLoopMode.ONCE); library.register(other);
        var existing = bind(track, other.id(), 0); existing.setMuted(false);
        var preview = ActorAnimationAssetEditing.previewAttachment(model, library, source.id(), track);
        assertEquals(1, preview.overlappingClips()); assertEquals(1, track.getClips().size()); assertFalse(history.canUndo());
        var wrapper = ActorAnimationAssetEditing.attach(model, library, source.id(), track);
        assertEquals(2, track.getClips().size()); assertTrue(track.getClips().contains(existing.getTimelineClip()));
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.attach(model, library, source.id(), track));
        assertEquals(1, history.getUndoSize()); assertTrue(history.undo()); assertEquals(List.of(existing), track.getActorTrackPayload().getClips());
        assertFalse(track.getClips().contains(wrapper)); assertTrue(history.redo()); assertSame(track, wrapper.getParentTrack());
    }
    @Test void attachmentFailureRollsBackEvenPartiallyInsertedWrapper() {
        var failing = new TimelineTrack("Fail", TrackType.ACTOR) {
            @Override public boolean addClip(TimelineClip clip) {
                super.addClip(clip); throw new IllegalStateException("failure after insert");
            }
        };
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId())); failing.setActorTrackPayload(payload); model.addTrack(failing);
        assertThrows(IllegalStateException.class, () -> ActorAnimationAssetEditing.attach(model, library, source.id(), failing));
        assertTrue(payload.getClips().isEmpty()); assertTrue(failing.getClips().isEmpty()); assertEquals(0, history.getUndoSize());
        assertSame(source, library.find(source.id()).orElseThrow());
    }
    @Test void lockedMutedPlayingAndIncompatibleOwnersRejectWithoutMutation() {
        track.setLocked(true); assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.attach(model, library, source.id(), track));
        track.setLocked(false); track.setMuted(true); assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.attach(model, library, source.id(), track));
        track.setMuted(false); model.setPlaying(true);
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.duplicate(model, library, source.id(), "project:new", "New"));
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.importFile(model, library, temp.resolve("missing.json"), "project:new"));
        model.setPlaying(false);
        var cow = new Actor("Cow", ActorType.COW); ActorRegistryManager.getInstance().registerActor(cow); track.getActorTrackPayload().setActorRef(new ActorRef(cow.getActorId()));
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.attach(model, library, source.id(), track));
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.createForActor(model, library, "project:new", "New", 2, cow.getActorId()));
        assertEquals(1, library.size()); assertTrue(track.getClips().isEmpty()); assertFalse(history.canUndo());
    }
    @Test void redoRefusesReboundOwnerAndCanRetryWhenBindingIsRestored() {
        ActorAnimationAssetEditing.attach(model, library, source.id(), track); assertTrue(history.undo());
        track.getActorTrackPayload().setActorRef(new ActorRef(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, history::redo); assertTrue(track.getClips().isEmpty()); assertEquals(1, history.getRedoSize());
        track.getActorTrackPayload().setActorRef(new ActorRef(actor.getActorId())); assertTrue(history.redo());
    }
    @Test void explicitTrackOrClipSelectionResolvesActorsWithMultipleTracks() {
        var second = track(actor.getActorId()); assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.resolveActorTrack(model, actor.getActorId()));
        model.selectItem(track, false); assertSame(track, ActorAnimationAssetEditing.resolveActorTrack(model, actor.getActorId()));
        var clip = bind(second, source.id(), 0); model.selectItem(clip.getTimelineClip(), false);
        assertSame(second, ActorAnimationAssetEditing.resolveActorTrack(model, actor.getActorId()));
    }
    @Test void renamedPortableImportIsIndependentAndUndoableWithoutOverwritingOriginal() throws Exception {
        var file = temp.resolve("source.json"); AnimationAssetPersistence.exportAsset(file, source);
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.importFile(model, library, file, ""));
        assertEquals(1, library.size()); assertFalse(history.canUndo());
        var imported = ActorAnimationAssetEditing.importFile(model, library, file, " project:imported ");
        assertEquals("project:imported", imported.id()); assertEquals(source.markers(), imported.markers());
        imported.getOrCreateBoneTrack("head").rotation().put(1, new Quaternionf(), InterpolationMode.LINEAR);
        assertEquals(InterpolationMode.STEP, source.findBoneTrack("head").orElseThrow().rotation().findAt(1).orElseThrow().interpolation());
        assertTrue(history.undo()); assertEquals(1, library.size()); assertTrue(history.redo()); assertSame(imported, library.find(imported.id()).orElseThrow());
    }
    @Test void deletingReferencedMutedClipsOrUndoingExternallyReferencedCreationIsProtected() {
        var clip = bind(track, source.id(), 0); clip.setMuted(true); track.setMuted(true); track.setLocked(true);
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.delete(model, library, source.id()));
        var created = ActorAnimationAssetEditing.create(model, library, "project:new", "New", 2);
        bind(track, created.id(), 10); assertThrows(IllegalArgumentException.class, history::undo);
        assertSame(created, library.find(created.id()).orElseThrow()); assertEquals(1, history.getUndoSize());
    }
}
