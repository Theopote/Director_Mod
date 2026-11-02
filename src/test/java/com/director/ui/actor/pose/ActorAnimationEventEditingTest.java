package com.director.ui.actor.pose;
import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntityActionEvent;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationEventEditingTest {
    TimelineModel model;TimelineTrack owner;EntityAnimationClip clip;TimelineCommandHistory history;
    @BeforeEach void setup() {
        var registry=new ActorRegistry();ActorRegistryManager.setRegistry(registry);var actor=registry.createActor("A",ActorType.PLAYER);
        model=new TimelineModel(new TimelineViewportState());owner=new TimelineTrack("A",TrackType.ACTOR);
        owner.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor));model.addTrack(owner);
        clip=new EntityAnimationClip("Animation",0,5);owner.getActorTrackPayload().addClip(clip);owner.addClip(new EntityTimelineClip(clip,0));
        history=new TimelineCommandHistory();TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup(){TimelineCommandBridge.bind(null);ActorRegistryManager.setRegistry(null);}
    AnimationEvent event(){return new AnimationEvent(.5f,new EntityActionEvent(EntityActionClip.ActionType.DETACH_PROP,new LinkedHashMap<>(Map.of("slot","MAIN_HAND"))),AnimationEvent.Timing.ANIMATION);}
    @Test void eventListsAreDetachedAndUndoRedoPreserveTiming() {
        var event=event();ActorAnimationEventEditing.replace(model,owner,clip,List.of(),List.of(event));
        event.getAction().params().put("slot","OFF_HAND");
        assertEquals("MAIN_HAND",clip.getEvents().getFirst().getAction().params().get("slot"));
        assertTrue(history.undo());assertTrue(clip.getEvents().isEmpty());assertTrue(history.redo());
        assertEquals(AnimationEvent.Timing.ANIMATION,clip.getEvents().getFirst().getTiming());
        owner.setLocked(true);assertThrows(IllegalArgumentException.class,()->ActorAnimationEventEditing.replace(model,owner,clip,clip.getEvents(),List.of()));
    }
    @Test void staleListAndInvalidEventParamsCannotOverwriteSource() {
        clip.addEvent(event());assertThrows(IllegalArgumentException.class,()->ActorAnimationEventEditing.replace(model,owner,clip,List.of(),List.of()));
        var invalid=new AnimationEvent(.5f,new EntityActionEvent(EntityActionClip.ActionType.THROW_PROP,Map.of("slot","MAIN_HAND")));
        assertThrows(IllegalArgumentException.class,()->ActorAnimationEventEditing.replace(model,owner,clip,clip.getEvents(),List.of(invalid)));
        assertEquals(1,clip.getEvents().size());assertEquals(0,history.getUndoSize());
    }
}
