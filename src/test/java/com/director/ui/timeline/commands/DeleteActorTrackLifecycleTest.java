package com.director.ui.timeline.commands;

import com.director.actor.*;
import com.director.timeline.model.*;
import org.junit.jupiter.api.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class DeleteActorTrackLifecycleTest {
    @AfterEach void cleanup() { ActorRegistryManager.setRegistry(null); }
    @Test void removingLastTrackPreservesProjectActorThroughUndoAndRedo() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor=new Actor("Villager",ActorType.VILLAGER);
        ActorRegistryManager.getInstance().registerActor(actor);
        var model=new TimelineModel(new TimelineViewportState());
        var track=new TimelineTrack("Villager",TrackType.ACTOR);
        track.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor)); model.addTrack(track);
        var tracks=new ArrayList<>(model.getTracks());
        var command=new DeleteTrackCommand(model,track,tracks);
        command.execute(); assertTrue(tracks.isEmpty());
        assertSame(actor,ActorRegistryManager.getInstance().getActor(actor.getActorId()).orElseThrow());
        command.undo(); assertEquals(java.util.List.of(track),tracks);
        command.execute(); assertTrue(tracks.isEmpty());
        assertSame(actor,ActorRegistryManager.getInstance().getActor(actor.getActorId()).orElseThrow());
    }
}
