package com.director.ui.timeline.commands;

import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeleteActorCommandTest {
    private TimelineModel model() {
        var model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        return model;
    }
    private TimelineTrack track(TimelineModel model, Actor actor) {
        var track = new TimelineTrack(actor.getName(), TrackType.ACTOR);
        track.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor));
        var spawn = new EntitySpawnClip("Spawn", 0, 5);
        track.getActorTrackPayload().addClip(spawn);
        track.addClip(new EntityTimelineClip(spawn, 0));
        model.addTrack(track);
        return track;
    }
    @Test void repeatedUndoRedoRestoresIdentitiesOrderingGroupsAndAppearance() {
        var registry = new ActorRegistry();
        var first = registry.createActor("First", ActorType.COW);
        var actor = registry.createActor("Player", ActorType.PLAYER);
        var last = registry.createActor("Last", ActorType.VILLAGER);
        actor.setSkinFileName("skins/player.png"); actor.setPlayerModel(Actor.PlayerModel.SLIM);
        var group = registry.createGroup("Cast");
        group.addActor(first.getActorId()); group.addActor(actor.getActorId()); group.addActor(last.getActorId());
        var members = group.getActorIds();
        var model = model();
        var a = track(model, actor); var unrelated = track(model, first); var b = track(model, actor);
        model.selectItem(a.getClips().getFirst(), false);
        var command = new DeleteActorCommand(model, registry, actor.getActorId());
        for (int i = 0; i < 3; i++) {
            command.execute();
            assertFalse(registry.contains(actor.getActorId()));
            assertTrue(model.getSelectedItems().isEmpty());
            assertEquals(List.of(unrelated), model.getAllTracks());
            assertEquals(List.of(first.getActorId(), last.getActorId()), group.getActorIds());
            command.undo();
            assertEquals(List.of(first, actor, last), registry.getAllActors());
            assertEquals(List.of(a, unrelated, b), model.getAllTracks());
            assertEquals(members, group.getActorIds());
            assertSame(a.getClips().getFirst(), model.getSelectedClip());
            assertSame(actor, registry.getActor(actor.getActorId()).orElseThrow());
            assertSame(a.getActorTrackPayload().getClips().getFirst(),
                ((EntityTimelineClip)a.getClips().getFirst()).getEntityClip());
            assertEquals("skins/player.png", actor.getSkinFileName());
            assertEquals(Actor.PlayerModel.SLIM, actor.getPlayerModel());
        }
    }
    @Test void unusedActorDeletionAlsoSupportsUndo() {
        var registry = new ActorRegistry(); var actor = registry.createActor("Unused", ActorType.SHEEP);
        var model = model();
        var command = new DeleteActorCommand(model, registry, actor.getActorId());
        command.execute(); assertTrue(registry.isEmpty());
        command.undo(); assertSame(actor, registry.getActor(actor.getActorId()).orElseThrow());
        assertTrue(model.getAllTracks().isEmpty());
    }
    @Test void conflictingActorIdentityIsRejectedBeforeTracksChange() {
        var registry = new ActorRegistry(); var actor = registry.createActor("A", ActorType.PLAYER);
        var model = model(); var track = track(model, actor);
        var command = new DeleteActorCommand(model, registry, actor.getActorId());
        registry.registerActor(new Actor(actor.getActorId(), "Replacement", ActorType.COW));
        assertThrows(IllegalStateException.class, command::execute);
        assertEquals(List.of(track), model.getAllTracks());
    }
}
