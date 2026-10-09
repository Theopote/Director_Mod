package com.director.actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorDisplayNameTest {
    @Test void removesNulTerminatedBufferTailFromAllActorNames() {
        String value="007\0r";
        var actor=new Actor(value,ActorType.PLAYER);assertEquals("007",actor.getName());
        actor.setName(" 新演员\0Old");assertEquals("新演员",actor.getName());
        var track=new TimelineTrack(value,TrackType.ACTOR);assertEquals("007",track.getName());
        track.setName("Renamed\0Actor");assertEquals("Renamed",track.getName());
        var payload=new ActorTrack();payload.setDisplayName(value);assertEquals("007",payload.getDisplayName());
    }
    @Test void reopenedTrackRetainsIdentityAndEditableTransformData() {
        ActorRegistryManager.setRegistry(new ActorRegistry());
        try {
            var actor=ActorRegistryManager.getInstance().createActor("A\0r",ActorType.PLAYER);
            var viewport=new TimelineViewportState();var model=new TimelineModel(viewport);model.setSuppressPersistenceWrites(true);
            var track=new TimelineTrack(actor.getName(),TrackType.ACTOR);var payload=track.getOrCreateActorTrackPayload();
            payload.setActorRef(ActorRef.of(actor));model.addTrack(track);
            var spawn=new EntitySpawnClip("Actor",0,5);spawn.setScale(2,3,4);payload.addClip(spawn);track.addClip(new EntityTimelineClip(spawn,0));
            var dto=com.director.timeline.persistence.TimelineModelPersistence.toDTO(model,viewport);
            var reopened=new TimelineModel(viewport);reopened.setSuppressPersistenceWrites(true);
            com.director.timeline.persistence.TimelineModelPersistence.fromDTO(dto,reopened,viewport);
            var owner=com.director.ui.actor.selection.ActorSelectionTarget.resolve(reopened,actor.getActorId());
            assertNotNull(owner);assertNotSame(track,owner);
            var loaded=(EntitySpawnClip)owner.getActorTrackPayload().getClips().getFirst();
            assertEquals(new org.joml.Vector3f(2,3,4),loaded.getScale());
            assertTrue(com.director.ui.actor.pose.ActorRootKeyframeEditing.editable(reopened,owner,loaded));
            assertEquals("A",owner.getName());
        } finally {ActorRegistryManager.setRegistry(null);}
    }
}
