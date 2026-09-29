package com.director.actor.path;

import com.director.actor.ActorRef;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RouteJumpPersistenceTest {
    @Test void roundTripAndLegacyDefaults() {
        var viewport = new TimelineViewportState();
        var model = new TimelineModel(viewport);
        model.setSuppressPersistenceWrites(true);
        var payload = new ActorTrack("actor");
        payload.setActorRef(new ActorRef(UUID.randomUUID()));
        var clip = new ActorRouteClip("ground route", 0, 10);
        clip.addWaypoint(0, 64, 0);
        clip.addWaypoint(10, 64, 0);
        clip.setJumpStart(2);
        clip.setJumpDuration(0.6f);
        clip.setJumpHeight(0.9f);
        payload.addClip(clip);
        var track = new TimelineTrack("actor", TrackType.ACTOR);
        track.setActorTrackPayload(payload);
        model.addTrack(track);
        var dto = TimelineModelPersistence.toDTO(model, viewport);
        var restored = new TimelineModel(new TimelineViewportState());
        TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
        var loaded = (ActorRouteClip) restored.getTracks().getFirst().getActorTrackPayload().getClips().getFirst();
        assertEquals(2, loaded.getJumpStart());
        assertEquals(0.6f, loaded.getJumpDuration());
        assertEquals(0.9f, loaded.getJumpHeight());
        var old = dto.tracks.getFirst().actorTrackPayload.clips.getFirst();
        old.routeJumpStart = null;
        old.routeJumpDuration = null;
        old.routeJumpHeight = null;
        TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
        loaded = (ActorRouteClip) restored.getTracks().getFirst().getActorTrackPayload().getClips().getFirst();
        assertEquals(0, loaded.getJumpHeight());
        assertEquals(0.5f, loaded.getJumpDuration());
    }
}
