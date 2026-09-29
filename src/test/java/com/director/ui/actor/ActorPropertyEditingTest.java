package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.rig.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.ActorTrack;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPropertyEditingTest {
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); }
    @Test void typeChangeRefreshesProfileAndUndoRestoresStyle() {
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        var actor = new Actor("A", ActorType.PLAYER);
        var profile = actor.getRigProfile();
        profile.getStyle().tempo = 1.3f;
        int[] refreshes = {0};
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
        assertEquals("B", track.getName());
        assertTrue(history.undo());
        assertEquals("A", actor.getName());
        assertEquals("Track", track.getName());
        assertEquals("Performance", payload.getDisplayName());
        assertTrue(history.redo());
        assertEquals("B", payload.getDisplayName());
    }
    @Test void sameTypePreservesStyle() {
        var actor = new Actor("A", ActorType.COW);
        var profile = actor.getRigProfile();
        actor.setType(ActorType.COW);
        actor.setType(null);
        assertSame(profile, actor.getRigProfile());
    }
}
