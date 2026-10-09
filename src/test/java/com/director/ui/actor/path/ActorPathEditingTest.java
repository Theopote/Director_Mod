package com.director.ui.actor.path;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.path.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ActorPathEditingTest {
    TimelineModel model; TimelineTrack track; ActorPathLibrary library; TimelineCommandHistory history;
    AtomicInteger saves;
    List<Vector3f> points = List.of(new Vector3f(0,0,0), new Vector3f(4,0,0));
    ActorRouteClip.InterpolationMode mode = ActorRouteClip.InterpolationMode.LINEAR;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = new TimelineTrack("Actor", TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId())); track.setActorTrackPayload(payload); model.addTrack(track);
        library = new ActorPathLibrary(); saves = new AtomicInteger();
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    ActorPathDefinition path() {
        var path = new ActorPathDefinition("Path"); path.setWaypoints(points); path.setInterpolationMode(mode); library.add(path); return path;
    }
    ActorRouteClip route() { return (ActorRouteClip) ActorPathEditing.attach(model, track, points, mode, "Route").getEntityClip(); }
    @Test void boundRouteFollowsLibraryGeometryWithoutChangingDurationAndUndoKeepsIdentity() {
        var path = new ActorPathDefinition("Linked test"); path.setWaypoints(points); path.setInterpolationMode(mode);
        var global = ActorPathLibraryManager.getInstance(); global.add(path);
        try {
            var wrapper = ActorPathEditing.attach(model, track, points, mode, path.getName(), path.getId());
            var route = (ActorRouteClip)wrapper.getEntityClip(); float end = route.getEndTime();
            path.setWaypoints(List.of(new Vector3f(), new Vector3f(8,0,0)));
            assertEquals(8, route.getWaypoints().getLast().x); assertEquals(end, route.getEndTime());
            assertEquals(4, new com.director.timeline.playback.entity.EntityClipSampler(track.getActorTrackPayload()).sampleAt(3).position.x, 1e-4f);
            assertTrue(history.undo()); assertTrue(history.redo()); assertEquals(path.getId(), route.getSourcePathId());
            global.remove(path.getId()); assertEquals(4, route.getWaypoints().getLast().x);
        } finally { global.remove(path.getId()); }
    }
    @Test void onceDurationControlsSpeedAndGeometryEditingKeepsDuration() {
        var route = route(); route.setTimeRange(2, 6);
        var sampler = new com.director.timeline.playback.entity.EntityClipSampler(track.getActorTrackPayload());
        assertEquals(2, sampler.sampleAt(4).position.x, 1e-4f); assertEquals(1, sampler.sampleAt(4).speed, 1e-4f);
        route.setTimeRange(2, 4);
        assertEquals(2, sampler.sampleAt(3).position.x, 1e-4f); assertEquals(2, sampler.sampleAt(3).speed, 1e-4f);
        assertEquals(4, sampler.sampleAt(4).position.x, 1e-4f); assertEquals(0, sampler.sampleAt(4).speed);
        var target = ActorPathEditing.route(model, track, route);
        assertTrue(target.commit(List.of(new Vector3f(), new Vector3f(8,0,0)), mode));
        assertEquals(4, route.getEndTime()); assertEquals(4, sampler.sampleAt(3).position.x, 1e-4f);
    }
    @Test void verticalRouteUsesThreeDimensionalLengthAndNamesAreUnique() {
        var vertical = List.of(new Vector3f(0,10,0), new Vector3f(0,16,0));
        var route = (ActorRouteClip) ActorPathEditing.attach(model, track, vertical, mode, "Vertical").getEntityClip();
        assertEquals(3, route.getLength(), 1e-4f);
        var sampled = new com.director.timeline.playback.entity.EntityClipSampler(track.getActorTrackPayload()).sampleAt(3);
        assertEquals(12, sampled.position.y, 1e-4f); assertEquals(2, sampled.speed, 1e-4f);
        var a = ActorPathEditing.saveNew(model, library, vertical, mode, saves::incrementAndGet);
        var b = ActorPathEditing.saveNew(model, library, vertical, mode, saves::incrementAndGet);
        assertNotEquals(a.getName(), b.getName()); assertNotEquals(a.getId(), b.getId());
    }
    @Test void libraryGeometryIsCopiedAndCommittedOnceWithUndo() {
        var path = path(); var target = ActorPathEditing.library(model, library, path, saves::incrementAndGet);
        var draft = List.of(new Vector3f(1,2,3), new Vector3f(8,2,3));
        assertEquals(points, path.getWaypoints()); assertEquals(0, saves.get());
        assertTrue(target.commit(draft, mode)); draft.getFirst().set(99);
        assertEquals(new Vector3f(1,2,3), path.getWaypoints().getFirst()); assertEquals(1, saves.get());
        assertEquals(1, history.getUndoSize()); history.undo(); assertEquals(points, path.getWaypoints());
        history.redo(); assertEquals(new Vector3f(1,2,3), path.getWaypoints().getFirst()); assertEquals(3, saves.get());
    }
    @Test void noOpAndInvalidGeometryDoNotSaveOrCreateHistory() {
        var path = path(); var target = ActorPathEditing.library(model, library, path, saves::incrementAndGet);
        assertTrue(target.commit(points, mode)); assertEquals(0, history.getUndoSize()); assertEquals(0, saves.get());
        assertThrows(IllegalArgumentException.class, () -> target.commit(List.of(new Vector3f()), mode));
        assertThrows(IllegalArgumentException.class, () -> target.commit(List.of(new Vector3f(Float.NaN), new Vector3f()), mode));
        assertEquals(points, path.getWaypoints()); assertEquals(0, history.getUndoSize());
    }
    @Test void libraryLockRemovalReplacementTimeAndSourceChangesInvalidateTarget() {
        var path = path(); var target = ActorPathEditing.library(model, library, path, saves::incrementAndGet);
        path.setLocked(true); assertFalse(target.commit(points, mode)); path.setLocked(false);
        target = ActorPathEditing.library(model, library, path, saves::incrementAndGet);
        model.setCurrentTime(3); assertFalse(target.isCurrent()); model.setCurrentTime(2);
        target = ActorPathEditing.library(model, library, path, saves::incrementAndGet);
        path.getWaypoints().getFirst().set(2); assertFalse(target.isCurrent());
        path.setWaypoints(points); target = ActorPathEditing.library(model, library, path, saves::incrementAndGet);
        library.add(path.copy()); assertFalse(target.isCurrent()); library.remove(path.getId()); assertFalse(target.isCurrent());
    }
    @Test void failedLibrarySaveRestoresGeometryAndKeepsHistoryEmpty() {
        var path = path(); var target = ActorPathEditing.library(model, library, path, () -> { throw new IllegalStateException("save failed"); });
        assertThrows(IllegalStateException.class, () -> target.commit(List.of(new Vector3f(), new Vector3f(8,0,0)), mode));
        assertEquals(points, path.getWaypoints()); assertEquals(0, history.getUndoSize());
    }
    @Test void routeApplicationCopiesGeometryAndUndoRestoresBothLists() {
        var path = path(); var route = route(); points.getLast().set(99);
        assertEquals(new Vector3f(4,0,0), route.getWaypoints().getLast()); assertEquals(0, saves.get());
        assertEquals(1, library.size()); assertSame(path, library.get(path.getId()));
        history.undo(); assertTrue(track.getClips().isEmpty()); assertTrue(track.getActorTrackPayload().getClips().isEmpty());
        history.redo(); assertSame(route, track.getActorTrackPayload().getClips().getFirst());
    }
    @Test void lockedMutedPlayingMissingActorAndOverlapRejectApplicationWithoutSideEffects() {
        track.setLocked(true); assertThrows(IllegalArgumentException.class, this::route); track.setLocked(false);
        track.setMuted(true); assertThrows(IllegalArgumentException.class, this::route); track.setMuted(false);
        model.setPlaying(true); assertThrows(IllegalArgumentException.class, this::route); model.setPlaying(false);
        route(); assertThrows(IllegalArgumentException.class, this::route); assertEquals(1, track.getClips().size());
        history.undo(); var transform = new EntityTransformClip("Move", 1, 3); track.getActorTrackPayload().addClip(transform);
        assertThrows(IllegalArgumentException.class, this::route); assertTrue(track.getClips().isEmpty());
        transform.setMuted(true); ActorRegistryManager.setRegistry(new ActorRegistry());
        assertThrows(IllegalArgumentException.class, this::route); assertTrue(library.isEmpty());
    }
    @Test void routeGeometryUpdatesDurationAndUndoPreservesOtherBehavior() {
        var route = route(); route.setJumpHeight(.8f); route.setJumpStart(.2f); route.setLoopMode(ActorRouteClip.LoopMode.PING_PONG);
        var wrapper = route.getTimelineClip(); float originalEnd = route.getEndTime();
        var target = ActorPathEditing.route(model, track, route);
        assertTrue(target.commit(List.of(new Vector3f(), new Vector3f(8,0,0)), mode));
        assertEquals(6, route.getEndTime()); assertEquals(6, wrapper.getEndTime()); assertEquals(.8f, route.getJumpHeight());
        assertEquals(ActorRouteClip.LoopMode.PING_PONG, route.getLoopMode()); history.undo();
        assertEquals(originalEnd, route.getEndTime()); assertEquals(points, route.getWaypoints()); assertSame(wrapper, route.getTimelineClip());
        history.redo(); assertEquals(6, route.getEndTime());
    }
    @Test void routeLockRebindPlaybackAndBehaviorChangesInvalidateGesture() {
        var route = route(); var target = ActorPathEditing.route(model, track, route);
        route.setSpeed(3); assertFalse(target.isCurrent()); route.setSpeed(2);
        target = ActorPathEditing.route(model, track, route); route.setJumpHeight(.5f); assertFalse(target.isCurrent());
        target = ActorPathEditing.route(model, track, route); track.setLocked(true); assertFalse(target.isCurrent()); track.setLocked(false);
        target = ActorPathEditing.route(model, track, route); model.setPlaying(true); assertFalse(target.isCurrent()); model.setPlaying(false);
        target = ActorPathEditing.route(model, track, route);
        track.getActorTrackPayload().setActorRef(new ActorRef(java.util.UUID.randomUUID())); assertFalse(target.isCurrent());
    }
    @Test void saveNewAndMetadataOperationsAreUndoableAndRespectLocks() {
        var path = ActorPathEditing.saveNew(model, library, points, mode, saves::incrementAndGet);
        ActorPathEditing.rename(model, library, path, "Renamed", saves::incrementAndGet);
        ActorPathEditing.setVisible(model, library, path, false, saves::incrementAndGet);
        ActorPathEditing.setLocked(model, library, path, true, saves::incrementAndGet);
        assertThrows(IllegalArgumentException.class, () -> ActorPathEditing.remove(model, library, path, saves::incrementAndGet));
        history.undo(); assertFalse(path.isLocked()); history.undo(); assertTrue(path.isVisible());
        history.undo(); assertEquals("Path", path.getName()); history.undo(); assertTrue(library.isEmpty());
        history.redo(); assertSame(path, library.get(path.getId()));
        ActorPathEditing.remove(model, library, path, saves::incrementAndGet); assertTrue(library.isEmpty());
        history.undo(); assertSame(path, library.get(path.getId()));
    }
    @Test void failedAttachmentRollsBackPartialInsertion() {
        var payload = track.getActorTrackPayload(); model.removeTrack(track);
        track = new TimelineTrack("Fail", TrackType.ACTOR) {
            @Override public boolean addClip(TimelineClip clip) { super.addClip(clip); throw new IllegalStateException("injected"); }
        };
        track.setActorTrackPayload(payload); model.addTrack(track);
        assertThrows(IllegalStateException.class, this::route); assertTrue(payload.getClips().isEmpty()); assertTrue(track.getClips().isEmpty());
        assertEquals(0, history.getUndoSize()); assertTrue(library.isEmpty());
    }
    @Test void invalidatedUndoKeepsHistoryAndDoesNotEditReplacementPath() {
        var path = path(); var target = ActorPathEditing.library(model, library, path, saves::incrementAndGet);
        target.commit(List.of(new Vector3f(), new Vector3f(8,0,0)), mode);
        var replacement = path.copy(); library.add(replacement);
        assertThrows(IllegalArgumentException.class, history::undo);
        assertEquals(1, history.getUndoSize()); assertEquals(new Vector3f(8,0,0), replacement.getWaypoints().getLast());
    }
    @Test void changedRouteAndNewRedoOverlapPreserveHistoryWithoutOverwritingData() {
        var route = route(); var target = ActorPathEditing.route(model, track, route);
        target.commit(List.of(new Vector3f(), new Vector3f(8,0,0)), mode);
        route.setSpeed(3); assertThrows(IllegalArgumentException.class, history::undo); assertEquals(2, history.getUndoSize());
        assertEquals(new Vector3f(8,0,0), route.getWaypoints().getLast());
        route.setSpeed(2); history.undo(); history.undo();
        var external = new EntityTransformClip("Other", 2, 3); track.getActorTrackPayload().addClip(external);
        assertThrows(IllegalArgumentException.class, history::redo); assertEquals(2, history.getRedoSize());
        assertTrue(track.getClips().isEmpty()); assertEquals(1, track.getActorTrackPayload().getClips().size());
    }
    @Test void failedMetadataAndCreationPersistenceRollBackAndDoNotAddUndo() {
        var path = path(); Runnable failure = () -> { throw new IllegalStateException("save failed"); };
        assertThrows(IllegalStateException.class, () -> ActorPathEditing.rename(model, library, path, "Other", failure));
        assertEquals("Path", path.getName());
        assertThrows(IllegalStateException.class, () -> ActorPathEditing.setVisible(model, library, path, false, failure));
        assertTrue(path.isVisible());
        assertThrows(IllegalStateException.class, () -> ActorPathEditing.remove(model, library, path, failure));
        assertSame(path, library.get(path.getId()));
        assertThrows(IllegalStateException.class, () -> ActorPathEditing.saveNew(model, library, points, mode, failure));
        assertEquals(1, library.size()); assertEquals(0, history.getUndoSize());
    }
    @Test void libraryTimeRoundTripRejectsDraftWithoutSavingOrUndoEntry() {
        var path=path(); var target=ActorPathEditing.library(model,library,path,saves::incrementAndGet);
        model.setCurrentTime(3); model.setCurrentTime(2);
        assertFalse(target.commit(List.of(new Vector3f(),new Vector3f(8,0,0)),mode));
        assertEquals(points,path.getWaypoints()); assertEquals(0,saves.get()); assertEquals(0,history.getUndoSize());
    }
    @Test void routeSelectionRoundTripRejectsDraftButCommittedUndoIgnoresSelectionVersion() {
        var route=route(); history.clear(); var target=ActorPathEditing.route(model,track,route);
        model.selectItem(track,false); model.clearSelection(); assertFalse(target.commit(List.of(new Vector3f(),new Vector3f(8,0,0)),mode));
        assertEquals(points,route.getWaypoints()); assertEquals(0,history.getUndoSize());
        target=ActorPathEditing.route(model,track,route); assertTrue(target.commit(List.of(new Vector3f(),new Vector3f(8,0,0)),mode));
        model.selectItem(track,false); history.undo(); assertEquals(points,route.getWaypoints());
        history.redo(); assertEquals(8,route.getWaypoints().get(1).x);
    }
}
