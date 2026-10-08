package com.director.ui.timeline.commands;

import com.director.actor.*;
import com.director.timeline.model.*;
import org.junit.jupiter.api.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class DeleteActorTrackLifecycleTest {
    @Test void deletingLastActorClipPreservesActorAndRestoresBothClipRepresentations() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor=new Actor("Player",ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        var model=new TimelineModel(new TimelineViewportState()); var track=new TimelineTrack("Player",TrackType.ACTOR);
        var payload=track.getOrCreateActorTrackPayload(); payload.setActorRef(ActorRef.of(actor)); model.addTrack(track);
        var spawn=new com.director.timeline.model.entity.EntitySpawnClip("Spawn",0,5);
        payload.addClip(spawn); var wrapper=new com.director.timeline.model.entity.EntityTimelineClip(spawn,0); track.addClip(wrapper);
        var clips=new ArrayList<>(track.getClips()); var command=new DeleteClipCommand(model,wrapper,clips);
        command.execute(); assertTrue(payload.getClips().isEmpty()); assertTrue(track.getClips().isEmpty());
        assertSame(actor,ActorRegistryManager.getInstance().getActor(actor.getActorId()).orElseThrow());
        command.undo(); assertTrue(payload.getClips().contains(spawn)); assertTrue(track.getClips().contains(wrapper));
        command.execute(); assertSame(actor,ActorRegistryManager.getInstance().getActor(actor.getActorId()).orElseThrow());
    }
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
