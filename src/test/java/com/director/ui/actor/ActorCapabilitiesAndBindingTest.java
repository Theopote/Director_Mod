package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.*;
import com.director.ui.actor.selection.ActorSelectionTarget;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorCapabilitiesAndBindingTest {
    TimelineModel model; Actor actor; TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        actor=new Actor("Player",ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model=new TimelineModel(new TimelineViewportState()); history=new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    @Test void capabilitiesDistinguishProceduralActionsFromEditableBonesAndSockets() {
        var player=ActorCapabilities.of(actor);
        assertTrue(player.selectable()); assertTrue(player.movable()); assertTrue(player.editableBones());
        assertFalse(player.propSlots().isEmpty()); assertFalse(player.supports(null));
        var cow=new Actor("Cow",ActorType.COW); var c=ActorCapabilities.of(cow);
        assertFalse(c.editableBones()); assertTrue(c.propSlots().isEmpty());
        for(var action:ActorActionType.values()) assertEquals(cow.getRigProfile().supports(action),c.supports(action));
        assertFalse(ActorCapabilities.of(new Actor("Custom",ActorType.CUSTOM)).editableBones());
        assertFalse(ActorCapabilities.of(null).selectable());
        assertThrows(UnsupportedOperationException.class,()->player.actions().clear());
    }
    @Test void bindingIsExplicitEmptyUndoableAndCannotBeDuplicated() {
        assertNull(ActorSelectionTarget.resolve(model,actor.getActorId())); assertTrue(model.getAllTracks().isEmpty());
        var track=ActorTrackBindingEditing.create(model,actor);
        assertSame(track,ActorSelectionTarget.resolve(model,actor.getActorId()));
        assertTrue(track.getClips().isEmpty()); assertTrue(track.getActorTrackPayload().getClips().isEmpty());
        assertThrows(IllegalArgumentException.class,()->ActorTrackBindingEditing.create(model,actor));
        assertTrue(history.undo()); assertNull(ActorSelectionTarget.resolve(model,actor.getActorId()));
        assertTrue(history.redo()); assertSame(track,ActorSelectionTarget.resolve(model,actor.getActorId()));
    }
    @Test void duplicateTracksRequireAnExplicitOwner() {
        var first=ActorTrackBindingEditing.create(model,actor);
        var second=new TimelineTrack("Other",TrackType.ACTOR);
        second.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor)); model.addTrack(second);
        assertNull(ActorSelectionTarget.resolve(model,actor.getActorId()));
        model.selectItem(second,false); assertSame(second,ActorSelectionTarget.resolve(model,actor.getActorId()));
        model.selectItem(first,true); assertNull(ActorSelectionTarget.resolve(model,actor.getActorId()));
    }
    @Test void refusesStaleActorAndUndoOfExternallyLockedTrack() {
        var track=ActorTrackBindingEditing.create(model,actor); track.setLocked(true);
        assertThrows(IllegalStateException.class,()->history.undo());
        assertTrue(model.getAllTracks().contains(track));
        track.setLocked(false); assertTrue(history.undo()); assertTrue(history.redo());
        assertThrows(IllegalStateException.class,()->ActorTrackBindingEditing.create(model,new Actor("Missing",ActorType.PLAYER)));
    }
}
