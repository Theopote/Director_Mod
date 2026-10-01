package com.director.ui.actor.path;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorRoutePropertyEditSessionTest {
    TimelineModel model;
    TimelineTrack track;
    ActorRouteClip route;
    TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = new TimelineTrack("Actor", TrackType.ACTOR);
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        track.setActorTrackPayload(payload); model.addTrack(track);
        route = (ActorRouteClip) ActorPathEditing.attach(model, track,
                List.of(new Vector3f(), new Vector3f(4,0,0)), ActorRouteClip.InterpolationMode.LINEAR, "Route").getEntityClip();
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    ActorRoutePropertyEditSession session() { return new ActorRoutePropertyEditSession(model, track, route); }
    @Test void manyDraftUpdatesProduceOneUndoAndPreserveIndependentDuration() {
        var edit = session();
        for (int i = 1; i <= 10; i++) edit.draft().setSpeed(i);
        edit.draft().setLoopMode(ActorRouteClip.LoopMode.PING_PONG);
        edit.draft().setTimeRange(2, 12); edit.draft().setJumpHeight(.75f);
        assertEquals(2, route.getSpeed()); assertEquals(4, route.getEndTime()); assertEquals(0, history.getUndoSize());
        assertTrue(edit.commit()); assertEquals(1, history.getUndoSize());
        assertEquals(12, route.getTimelineClip().getEndTime()); assertEquals(10, route.getSpeed());
        history.undo(); assertEquals(4, route.getEndTime()); assertEquals(2, route.getSpeed()); assertEquals(0, route.getJumpHeight());
        history.redo(); assertEquals(12, route.getEndTime()); assertEquals(.75f, route.getJumpHeight());
    }
    @Test void noOpAndCancellationDoNotCreateCommands() {
        assertTrue(session().commit()); var edit = session(); edit.draft().setSpeed(4); edit.cancel();
        assertFalse(edit.commit()); assertEquals(0, history.getUndoSize()); assertEquals(2, route.getSpeed());
    }
    @Test void timePlaybackLockAndSourceChangesRejectPendingEdits() {
        var edit = session(); edit.draft().setSpeed(3); model.setCurrentTime(3); assertFalse(edit.commit());
        model.setCurrentTime(2); edit = session(); model.setPlaying(true); assertFalse(edit.commit());
        model.setPlaying(false); edit = session(); track.setLocked(true); assertFalse(edit.commit());
        track.setLocked(false); edit = session(); route.setJumpHeight(.5f); assertFalse(edit.commit());
        assertEquals(0, history.getUndoSize()); assertEquals(2, route.getSpeed());
    }
    @Test void tooFewPointsAndOverlappingDurationAreAtomicFailures() {
        var edit = session(); edit.draft().removeWaypoint(0);
        assertThrows(IllegalArgumentException.class, edit::commit);
        assertEquals(2, route.getWaypoints().size());
        var other = new EntityTransformClip("Move", 5, 8); track.getActorTrackPayload().addClip(other);
        edit = session(); edit.draft().setTimeRange(2, 6); edit.draft().setSpeed(8);
        assertThrows(IllegalArgumentException.class, edit::commit);
        assertEquals(4, route.getEndTime()); assertEquals(2, route.getSpeed()); assertEquals(0, history.getUndoSize());
    }
    @Test void undoRejectsForeignSourceMutationWithoutLosingHistory() {
        var edit = session(); edit.draft().setSpeed(3); assertTrue(edit.commit());
        route.setSpeed(4); assertThrows(IllegalArgumentException.class, history::undo);
        assertEquals(1, history.getUndoSize()); assertEquals(4, route.getSpeed());
        route.setSpeed(3); history.undo(); assertEquals(2, route.getSpeed());
    }
    @Test void geometryIsDetachedAndWrapperRemainsStableAcrossUndo() {
        var wrapper = route.getTimelineClip(); var edit = session();
        edit.draft().getWaypoints().getLast().set(8,0,0);
        assertEquals(4, route.getWaypoints().getLast().x); assertTrue(edit.commit());
        assertEquals(8, route.getWaypoints().getLast().x); assertEquals(4, route.getEndTime());
        history.undo(); assertEquals(4, route.getWaypoints().getLast().x); assertSame(wrapper, route.getTimelineClip());
    }
    @Test void invalidDurationDoesNotWriteSourceOrHistory() {
        var edit = session(); edit.draft().setTimeRange(2, 2.01f);
        assertThrows(IllegalArgumentException.class, edit::commit);
        assertEquals(4, route.getEndTime()); assertEquals(0, history.getUndoSize());
    }
    @Test void redoChecksNewMovementConflictsBeforeApplyingAnyProperty() {
        var edit = session(); edit.draft().setTimeRange(2, 7); edit.draft().setSpeed(5);
        assertTrue(edit.commit()); history.undo();
        track.getActorTrackPayload().addClip(new EntityTransformClip("Move", 5, 8));
        assertThrows(IllegalArgumentException.class, history::redo);
        assertEquals(4, route.getEndTime()); assertEquals(2, route.getSpeed()); assertEquals(1, history.getRedoSize());
    }
}
