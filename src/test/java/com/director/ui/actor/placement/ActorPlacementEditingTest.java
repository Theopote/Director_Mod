package com.director.ui.actor.placement;

import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPlacementEditingTest {
    private ActorRegistry registry;private TimelineModel model;private TimelineCommandHistory history;
    @BeforeEach void setup() {
        registry=new ActorRegistry();ActorRegistryManager.setRegistry(registry);
        model=new TimelineModel(new TimelineViewportState());model.setCurrentTime(1);
        history=new TimelineCommandHistory();TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() {TimelineCommandBridge.bind(null);ActorRegistryManager.setRegistry(null);}
    @Test void creationAndItsTrackUndoTogetherAndRedoRetainsActorIdentity() {
        var actor=new Actor("Player",ActorType.PLAYER);actor.setSkinFileName("custom.png");
        var track=ActorPlacementEditing.create(model,registry,actor,new Vector3f(1,2,3),new Vector3f(30,20,10));
        assertSame(actor,registry.getActor(actor.getActorId()).orElseThrow());assertEquals(1,model.getAllTracks().size());
        assertTrue(history.undo());assertTrue(model.getAllTracks().isEmpty());assertFalse(registry.contains(actor.getActorId()));assertFalse(history.undo());
        assertTrue(history.redo());assertSame(actor,registry.getActor(actor.getActorId()).orElseThrow());
        assertEquals("custom.png",actor.getSkinFileName());assertSame(track,model.getAllTracks().getFirst());
    }
    @Test void relocationWritesCurrentRootKeysAndPreservesActorAndScale() {
        var actor=new Actor("Player",ActorType.PLAYER);
        var track=ActorPlacementEditing.create(model,registry,actor,new Vector3f(),new Vector3f());
        var spawn=(EntitySpawnClip)((EntityTimelineClip)track.getClips().getFirst()).getEntityClip();spawn.setScale(2,3,4);
        model.setCurrentTime(2);ActorPlacementEditing.relocate(model,registry,actor,new Vector3f(4,5,6),new Vector3f(10,20,30));
        assertEquals(1,registry.size());assertEquals(1,spawn.getKeyframes().size());
        assertEquals(3,spawn.getKeyframes().getFirst().getChannels());assertEquals(new Vector3f(2,3,4),spawn.getScale());
        assertTrue(history.undo());assertTrue(spawn.getKeyframes().isEmpty());assertTrue(registry.contains(actor.getActorId()));
    }
    @Test void existingUnboundActorGetsATrackWithoutBeingDuplicatedOrDeletedOnUndo() {
        var actor=new Actor("Existing",ActorType.PLAYER);registry.registerActor(actor);
        ActorPlacementEditing.relocate(model,registry,actor,new Vector3f(1),new Vector3f());
        assertEquals(1,registry.size());assertTrue(history.undo());assertEquals(1,registry.size());assertTrue(model.getAllTracks().isEmpty());
    }
    @Test void playbackOrStaleRegistryCannotCreateData() {
        model.setPlaying(true);
        assertThrows(IllegalArgumentException.class,()->ActorPlacementEditing.create(model,registry,new Actor("P",ActorType.PLAYER),new Vector3f(),new Vector3f()));
        assertTrue(registry.isEmpty());assertTrue(model.getAllTracks().isEmpty());assertFalse(history.undo());
    }
}
