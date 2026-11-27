package com.director.timeline;

import com.director.core.DirectorServices;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerTimelineServiceTest {
    private ContainerTimelineService service;
    private TimelineModel previousModel;

    @BeforeEach
    void setUp() {
        DirectorServices.initialize();
        ContainerTimelineService.initialize();
        service = ContainerTimelineService.requireInitialized();
        previousModel = service.getTimelineModel();
    }

    @AfterEach
    void tearDown() {
        service.setTimelineModel(previousModel);
    }

    @Test
    void collectsUniqueBoundContainersFromContainerTracksOnly() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID ignored = UUID.randomUUID();

        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        TimelineTrack containerTrack = new TimelineTrack("containers", TrackType.CONTAINER);
        containerTrack.addClip(clip(first));
        containerTrack.addClip(clip(first));
        containerTrack.addClip(clip(second));
        containerTrack.addClip(clip(null));
        model.addTrack(containerTrack);

        TimelineTrack cameraTrack = new TimelineTrack("camera", TrackType.CAMERA);
        cameraTrack.addClip(clip(ignored));
        model.addTrack(cameraTrack);
        service.setTimelineModel(model);

        assertEquals(Set.of(first, second), service.getContainersOnTimeline());
        assertTrue(service.isContainerOnTimeline(first));
        assertFalse(service.isContainerOnTimeline(ignored));
        assertFalse(service.isContainerOnTimeline(null));
    }

    @Test
    void returnsEmptySetWithoutTimelineModel() {
        service.setTimelineModel(null);

        assertTrue(service.getContainersOnTimeline().isEmpty());
        assertFalse(service.isContainerOnTimeline(UUID.randomUUID()));
    }

    private static TimelineClip clip(UUID containerId) {
        TimelineClip clip = new TimelineClip("clip", 0, 4, 0);
        clip.setBoundContainerId(containerId);
        return clip;
    }
}
