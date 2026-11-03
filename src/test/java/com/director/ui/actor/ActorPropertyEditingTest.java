package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.rig.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.ActorTrack;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPropertyEditingTest {
    @BeforeEach void setup() { ActorRegistryManager.setRegistry(new ActorRegistry()); }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    @Test void typeChangeRefreshesProfileAndUndoRestoresStyle() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        var actor = new Actor("A", ActorType.PLAYER);
        var profile = actor.getRigProfile();
        profile.getStyle().tempo = 1.3f;
        int[] refreshes = {0};
        ActorRegistryManager.getInstance().registerActor(actor);
        ActorPropertyEditing.changeType(model, actor, ActorType.COW, () -> refreshes[0]++);
        assertInstanceOf(QuadrupedRigProfile.class, actor.getRigProfile());
        assertTrue(history.undo());
        assertEquals(ActorType.PLAYER, actor.getType());
        assertSame(profile, actor.getRigProfile());
        assertEquals(1.3f, actor.getRigProfile().getStyle().tempo);
        assertTrue(history.redo());
        assertInstanceOf(QuadrupedRigProfile.class, actor.getRigProfile());
        assertEquals(3, refreshes[0]);
    }
    @Test void renameUndoRestoresIndependentTrackNames() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        var actor = new Actor("A", ActorType.PLAYER);
        var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId()));
        payload.setDisplayName("Performance");
        var track = new TimelineTrack("Track", TrackType.ACTOR);
        track.setActorTrackPayload(payload);
        model.addTrack(track);
        ActorPropertyEditing.rename(model, actor, "  B  ");
        assertEquals("B", actor.getName());
        assertEquals("Track", track.getName());
        assertTrue(history.undo());
        assertEquals("A", actor.getName());
        assertEquals("Track", track.getName());
        assertEquals("Performance", payload.getDisplayName());
        assertTrue(history.redo());
        assertEquals("Performance", payload.getDisplayName());
    }
    @Test void renameKeepsDefaultTrackNamesInSyncAcrossUndoRedo() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        var actor = new Actor("A", ActorType.PLAYER);
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId())); payload.setDisplayName("A");
        var track = new TimelineTrack("A", TrackType.ACTOR); track.setActorTrackPayload(payload); model.addTrack(track);
        ActorPropertyEditing.rename(model, actor, "B");
        assertEquals("B", track.getName()); assertEquals("B", payload.getDisplayName());
        assertTrue(history.undo()); assertEquals("A", track.getName()); assertEquals("A", payload.getDisplayName());
        assertTrue(history.redo()); assertEquals("B", track.getName()); assertEquals("B", payload.getDisplayName());
    }
    @Test void typeChangeRejectedDuringPlayback() {
        var model = new TimelineModel(new TimelineViewportState());
        var actor = new Actor("A", ActorType.PLAYER);
        model.setPlaying(true);
        assertThrows(IllegalArgumentException.class, () -> ActorPropertyEditing.changeType(model, actor, ActorType.COW, () -> {}));
        assertEquals(ActorType.PLAYER, actor.getType());
    }
    @Test void typeUndoRejectsReplacedActorAndPreservesHistory() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        var actor = new Actor("A", ActorType.PLAYER);
        var registry = ActorRegistryManager.getInstance(); registry.registerActor(actor);
        ActorPropertyEditing.changeType(model, actor, ActorType.COW, () -> {});
        registry.removeActor(actor.getActorId());
        var replacement = new Actor(actor.getActorId(), "Replacement", ActorType.PLAYER); registry.registerActor(replacement);
        assertThrows(IllegalArgumentException.class, history::undo);
        assertEquals(ActorType.PLAYER, replacement.getType()); assertEquals(ActorType.COW, actor.getType());
        registry.removeActor(actor.getActorId()); registry.registerActor(actor);
        assertTrue(history.undo()); assertEquals(ActorType.PLAYER, actor.getType());
    }
    @Test void typeRedoRejectsPlaybackWithoutConsumingCommand() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        var actor = new Actor("A", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        ActorPropertyEditing.changeType(model, actor, ActorType.COW, () -> {}); assertTrue(history.undo());
        model.setPlaying(true); assertThrows(IllegalArgumentException.class, history::redo);
        assertEquals(ActorType.PLAYER, actor.getType());
        model.setPlaying(false); assertTrue(history.redo()); assertEquals(ActorType.COW, actor.getType());
    }
    @Test void sameTypePreservesStyle() {
        var actor = new Actor("A", ActorType.COW);
        var profile = actor.getRigProfile();
        actor.setType(ActorType.COW);
        actor.setType(null);
        assertSame(profile, actor.getRigProfile());
    }
}
