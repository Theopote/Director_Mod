package com.director.ui.actor.path;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorPathVisibilityTest {
    @Test void showsOnlyToolPanelSelectionOrCurrentActorBinding() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertFalse(ActorPathVisibility.shouldShow(a, false, null, null));
        assertTrue(ActorPathVisibility.shouldShow(a, true, null, null));
        assertTrue(ActorPathVisibility.shouldShow(a, false, a, null));
        assertTrue(ActorPathVisibility.shouldShow(a, false, null, a));
        assertFalse(ActorPathVisibility.shouldShow(b, false, a, a));
        assertFalse(ActorPathVisibility.shouldShow(null, true, null, null));
    }
    @Test void selectedActorUsesActiveRouteAndDeletedSelectionDoesNotShowIt() {
        var model = new com.director.timeline.model.TimelineModel(new com.director.timeline.model.TimelineViewportState());
        var owner = new com.director.timeline.model.TimelineTrack("actor",
                com.director.timeline.model.TrackType.ACTOR);
        var payload = new com.director.timeline.model.entity.ActorTrack("actor");
        owner.setActorTrackPayload(payload);
        var spawn = new com.director.timeline.model.entity.EntitySpawnClip("actor", 0f, 5f);
        var wrapper = new com.director.timeline.model.entity.EntityTimelineClip(spawn, 0);
        owner.addClip(wrapper);
        var route = new com.director.timeline.model.entity.ActorRouteClip("route", 0f, 5f);
        UUID id = UUID.randomUUID();
        route.setSourcePathId(id);
        payload.addClip(route);
        model.addTrack(owner);
        model.setSelectedClip(wrapper);
        assertEquals(id, ActorPathVisibility.boundPath(model));
        route.setMuted(true);
        assertNull(ActorPathVisibility.boundPath(model));
        route.setMuted(false);
        model.removeTrack(owner);
        assertNull(ActorPathVisibility.boundPath(model));
    }
}
