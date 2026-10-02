package com.director.ui.actor.edit;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorStateDespawnPropertyEditSessionTest {
    TimelineModel model; TimelineTrack track; TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player",ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = new TimelineTrack("Actor",TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId())); track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    void attach(EntityClip c) { track.getActorTrackPayload().addClip(c); track.addClip(new EntityTimelineClip(c,0)); }
    EntityStateClip state() { var c = new EntityStateClip("State",1,3); attach(c); return c; }
    @Test void continuousVisibilityEditsAreDetachedAndOneUndo() {
        var c=state(); c.setSneaking(true); c.setInvulnerable(true);
        var edit=new ActorClipPropertyEditSession(model,c); var d=(EntityStateClip)edit.draft();
        for(int i=0;i<50;i++) d.setVisibility(i/100f); d.setGlowing(true);
        assertEquals(1,c.getVisibility()); assertFalse(c.isGlowing()); assertTrue(edit.commit());
        assertEquals(1,history.getUndoSize()); assertEquals(.49f,c.getVisibility());
        history.undo(); assertEquals(1,c.getVisibility()); assertFalse(c.isGlowing());
        history.redo(); assertTrue(c.isGlowing()); assertTrue(c.isSneaking()); assertTrue(c.isInvulnerable());
    }
    @Test void invalidVisibilityIsAtomic() {
        var c=state(); var edit=new ActorClipPropertyEditSession(model,c);
        ((EntityStateClip)edit.draft()).setVisibility(Float.NaN);
        assertThrows(IllegalArgumentException.class,edit::commit); assertEquals(1,c.getVisibility()); assertEquals(0,history.getUndoSize());
    }
    @Test void contextChangeAndCancelDiscardDraft() {
        var c=state(); var edit=new ActorClipPropertyEditSession(model,c); ((EntityStateClip)edit.draft()).setGlowing(true);
        model.setCurrentTime(3); assertFalse(edit.commit()); assertFalse(c.isGlowing());
        model.setCurrentTime(2); edit=new ActorClipPropertyEditSession(model,c); ((EntityStateClip)edit.draft()).setVisibility(0);
        edit.cancel(); assertFalse(edit.commit()); assertEquals(1,c.getVisibility());
    }
    @Test void sourceChangeBlocksUndoAndRetainsHistory() {
        var c=state(); var edit=new ActorClipPropertyEditSession(model,c); ((EntityStateClip)edit.draft()).setGlowing(true); edit.commit();
        c.setSprinting(true); assertThrows(IllegalArgumentException.class,history::undo); assertEquals(1,history.getUndoSize());
        c.setSprinting(false); history.undo(); assertFalse(c.isGlowing());
    }
    @Test void despawnPreservesWrapperTimeKeysAndSupportsUndoRedo() {
        var c=new EntityDespawnClip("Exit",1,3); attach(c); var wrapper=c.getTimelineClip();
        var key=new EntityClipKeyframe(2); c.addKeyframe(key);
        var edit=new ActorClipPropertyEditSession(model,c); ((EntityDespawnClip)edit.draft()).setMode(EntityDespawnClip.DespawnMode.HIDE);
        assertEquals(EntityDespawnClip.DespawnMode.REMOVE,c.getMode()); assertTrue(edit.commit()); history.undo();
        assertEquals(EntityDespawnClip.DespawnMode.REMOVE,c.getMode()); history.redo();
        assertEquals(EntityDespawnClip.DespawnMode.HIDE,c.getMode()); assertSame(wrapper,c.getTimelineClip());
        assertSame(key,c.getKeyframes().getFirst()); assertEquals(3,c.getEndTime()); assertEquals(1,history.getUndoSize());
    }
    @Test void noOpAndLockedDespawnDoNotCreateCommands() {
        var c=new EntityDespawnClip("Exit",1,3); attach(c); assertTrue(new ActorClipPropertyEditSession(model,c).commit());
        assertEquals(0,history.getUndoSize()); var edit=new ActorClipPropertyEditSession(model,c);
        ((EntityDespawnClip)edit.draft()).setMode(EntityDespawnClip.DespawnMode.HIDE); track.setLocked(true);
        assertFalse(edit.commit()); assertEquals(EntityDespawnClip.DespawnMode.REMOVE,c.getMode());
    }
}
