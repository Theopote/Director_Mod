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
    @Test void blankLaneChoosesItsClockAndRejectsUnreachableSourceTimes() {
        var asset = new com.director.actor.animation.asset.AnimationAsset(
                "test:event-draft", "Event draft", com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION,
                1f, com.director.actor.animation.asset.AnimationLoopMode.PING_PONG);
        var library = com.director.actor.animation.asset.AnimationAssetLibrary.getInstance();
        library.register(asset);
        try {
            clip.setAnimationId(asset.id());
            clip.setLoop(true);
            var clock = com.director.timeline.playback.entity.AnimationEventClock.projection(
                    owner.getActorTrackPayload(), clip, 0, 5);
            var clipSeed = ActorAnimationEventTimeline.draftAt(clock, 1.75f, 29).orElseThrow();
            assertEquals(AnimationEvent.Timing.CLIP, clipSeed.timing());
            assertEquals(.35f, clipSeed.phase());
            var sourceSeed = ActorAnimationEventTimeline.draftAt(clock, 1.75f, 47).orElseThrow();
            assertEquals(AnimationEvent.Timing.ANIMATION, sourceSeed.timing());
            assertEquals(.25f, sourceSeed.phase());
            assertTrue(ActorAnimationEventTimeline.draftAt(clock, 1.75f, 10).isEmpty());
            clip.setLoop(false);
            var once = com.director.timeline.playback.entity.AnimationEventClock.projection(
                    owner.getActorTrackPayload(), clip, 0, 5);
            assertTrue(ActorAnimationEventTimeline.draftAt(once, 1.75f, 47).isEmpty());
            assertTrue(clip.getEvents().isEmpty());
            assertEquals(0, history.getUndoSize());
        } finally {
            library.remove(asset.id());
        }
    }

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

    @Test void movingAnEventPreservesItsActionAndIsOneUndoableEdit() {
        var original = event();
        clip.addEvent(original);
        var before = ActorAnimationEventEditing.copy(clip.getEvents());
        assertTrue(ActorAnimationEventEditing.move(model, owner, clip, before, 0, .25f));
        var moved = clip.getEvents().getFirst();
        assertEquals(.25f, moved.getNormalizedTime());
        assertEquals(original.getAction(), moved.getAction());
        assertEquals(original.getTiming(), moved.getTiming());
        assertFalse(ActorAnimationEventEditing.move(model, owner, clip, before, 0, .75f));
        assertEquals(1, history.getUndoSize());
        assertTrue(history.undo());
        assertEquals(before, clip.getEvents());
        assertTrue(history.redo());
        assertEquals(.25f, clip.getEvents().getFirst().getNormalizedTime());
    }
}
