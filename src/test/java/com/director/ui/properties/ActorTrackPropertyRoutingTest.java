package com.director.ui.properties;

import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.properties.adapters.ActorTrackPropertyAdapter;
import com.director.ui.properties.adapters.CameraTrackPropertyAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ActorTrackPropertyRoutingTest {
    @Test
    void timelineRoutesUnboundActorTracksToTheirOwnInspector() {
        PropertyAdapterBootstrap.initialize();
        var actor = new TimelineTrack("Actor", TrackType.ACTOR);
        var camera = new TimelineTrack("Camera", TrackType.CAMERA);
        assertInstanceOf(ActorTrackPropertyAdapter.class, PropertyAdapterRegistry.getAdapterFor(PropertyContext.TIMELINE, actor));
        assertInstanceOf(CameraTrackPropertyAdapter.class, PropertyAdapterRegistry.getAdapterFor(PropertyContext.TIMELINE, camera));
    }
}
