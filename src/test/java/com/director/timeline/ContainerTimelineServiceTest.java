package com.director.timeline;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioTimelineClip;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        service.clearContainerRecoveryMapping();
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

    @Test
    void remapsContainerAudioAndTrackBindingsByUniqueRecoveredName() {
        UUID originalId = UUID.randomUUID();
        UUID firstId = originalId;
        UUID secondId = UUID.randomUUID();
        UUID unrelatedId = UUID.randomUUID();
        TimelineModel model = offlineModel();
        service.setTimelineModel(model);

        TimelineClip retainedClip = boundClip(originalId, "First");
        addTrack(model, "retained", TrackType.CONTAINER, retainedClip);
        TimelineClip repairedClip = boundClip(originalId, "Second");
        addTrack(model, "repaired", TrackType.CONTAINER, repairedClip);
        AudioTimelineClip audioClip = new AudioTimelineClip("audio", 1, 3, 0);
        audioClip.setBoundContainerId(originalId);
        audioClip.setBoundContainerName("Second");
        addTrack(model, "audio", TrackType.AUDIO, audioClip);
        TimelineClip unrelatedClip = boundClip(unrelatedId, "Unrelated");
        addTrack(model, "unrelated", TrackType.CONTAINER, unrelatedClip);
        TimelineTrack boundTrack = new TimelineTrack("track binding", TrackType.CONTAINER);
        boundTrack.setBoundContainerId(originalId.toString());
        boundTrack.setBoundContainerName("Second");
        model.addTrack(boundTrack);

        service.setContainerRecoveryMapping(Map.of(
            originalId, Map.of(firstId, "First", secondId, "Second")));

        assertEquals(originalId, retainedClip.getBoundContainerId());
        assertEquals("First", retainedClip.getBoundContainerName());
        assertEquals(secondId, repairedClip.getBoundContainerId());
        assertEquals("Second", repairedClip.getBoundContainerName());
        assertEquals(secondId, audioClip.getBoundContainerId());
        assertEquals("Second", audioClip.getBoundContainerName());
        assertEquals(unrelatedId, unrelatedClip.getBoundContainerId());
        assertEquals("Unrelated", unrelatedClip.getBoundContainerName());
        assertEquals(secondId.toString(), boundTrack.getBoundContainerId());
        assertEquals("Second", boundTrack.getBoundContainerName());
        assertEquals(0, service.remapContainerReferences(), "remapping must be idempotent");
    }

    @Test
    void ambiguousMissingAndStaleNamesDetachInsteadOfBindingWrongContainer() {
        UUID originalId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        TimelineModel model = offlineModel();
        service.setTimelineModel(model);

        TimelineClip missingName = boundClip(originalId, null);
        TimelineClip staleName = boundClip(originalId, "No longer exists");
        TimelineClip ambiguousName = boundClip(originalId, "Same name");
        addTrack(model, "missing", TrackType.CONTAINER, missingName);
        addTrack(model, "stale", TrackType.CONTAINER, staleName);
        addTrack(model, "ambiguous", TrackType.CONTAINER, ambiguousName);
        TimelineTrack boundTrack = new TimelineTrack("ambiguous track", TrackType.CONTAINER);
        boundTrack.setBoundContainerId(originalId.toString());
        model.addTrack(boundTrack);

        service.setContainerRecoveryMapping(Map.of(
            originalId, Map.of(firstId, "Same name", secondId, "Same name")));

        for (TimelineClip clip : List.of(missingName, staleName, ambiguousName)) {
            assertNull(clip.getBoundContainerId());
            assertNull(clip.getBoundContainerName());
        }
        assertNull(boundTrack.getBoundContainerId());
        assertNull(boundTrack.getBoundContainerName());
    }

    @Test
    void applyingContainerLoadMappingDoesNotMarkTimelineDirty() {
        UUID originalId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        CountingTimelineModel model = new CountingTimelineModel();
        model.setSuppressPersistenceWrites(true);
        TimelineClip clip = boundClip(originalId, "Second");
        addTrack(model, "containers", TrackType.CONTAINER, clip);
        service.setTimelineModel(model);
        model.dirtyCalls = 0;

        service.setContainerRecoveryMapping(Map.of(
            originalId, Map.of(firstId, "First", secondId, "Second")));

        assertEquals(secondId, clip.getBoundContainerId());
        assertEquals(0, model.dirtyCalls);
    }

    @Test
    void managerPublishesBothDuplicateOccurrencesAndRepairsCurrentModel() {
        UUID originalId = UUID.randomUUID();
        Container first = new Container(originalId, "Manager first", ContainerType.GENERAL);
        Container second = new Container(originalId, "Manager second", ContainerType.GENERAL);
        TimelineModel model = offlineModel();
        TimelineClip clip = boundClip(originalId, "Manager second");
        addTrack(model, "containers", TrackType.CONTAINER, clip);
        service.setTimelineModel(model);
        var manager = DirectorServices.get().containers();
        int countBefore = manager.getContainerCount();

        try {
            manager.addContainers(List.of(first, second));

            assertEquals(originalId, first.getId());
            assertNotEquals(originalId, second.getId());
            assertEquals(countBefore + 2, manager.getContainerCount());
            assertEquals(second.getId(), clip.getBoundContainerId());
            assertEquals("Manager second", clip.getBoundContainerName());
        } finally {
            manager.removeContainer(first);
            manager.removeContainer(second);
        }
        assertEquals(countBefore, manager.getContainerCount());
    }

    private static TimelineModel offlineModel() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        return model;
    }

    private static void addTrack(TimelineModel model, String name, TrackType type, TimelineClip clip) {
        TimelineTrack track = new TimelineTrack(name, type);
        // Corrupt legacy timelines can contain several clips with the same original UUID.
        // Attach first and restore the binding to model that persisted state without the live add guard.
        UUID boundId = clip.getBoundContainerId();
        String boundName = clip.getBoundContainerName();
        clip.setBoundContainerId(null);
        clip.setBoundContainerName(null);
        boolean added = track.addClip(clip);
        clip.setBoundContainerId(boundId);
        clip.setBoundContainerName(boundName);
        assertTrue(added);
        model.addTrack(track);
    }

    private static TimelineClip boundClip(UUID containerId, String containerName) {
        TimelineClip clip = new TimelineClip("clip", 0, 4, 0);
        clip.setBoundContainerId(containerId);
        clip.setBoundContainerName(containerName);
        return clip;
    }

    private static final class CountingTimelineModel extends TimelineModel {
        private int dirtyCalls;

        private CountingTimelineModel() {
            super(new TimelineViewportState());
        }

        @Override
        public void markDirty() {
            dirtyCalls++;
        }
    }

    private static TimelineClip clip(UUID containerId) {
        TimelineClip clip = new TimelineClip("clip", 0, 4, 0);
        clip.setBoundContainerId(containerId);
        return clip;
    }
}
