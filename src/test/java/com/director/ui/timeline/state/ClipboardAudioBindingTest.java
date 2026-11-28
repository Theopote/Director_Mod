package com.director.ui.timeline.state;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.timeline.model.audio.AudioTrack;
import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardAudioBindingTest {
    private ClipboardManager clipboard;
    private TimelineAudioService audioService;
    private TimelineModel previousAudioModel;
    private TimelineModel model;
    private TimelineCommandHistory history;
    private ContainerManager manager;
    private final List<Container> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        DirectorServices.initialize();
        manager = DirectorServices.get().containers();
        clipboard = ClipboardManager.getInstance();
        clipboard.clear();
        audioService = TimelineAudioService.getInstance();
        previousAudioModel = audioService.getTimelineModel();
        model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        history = new TimelineCommandHistory();
        history.setModel(model);
        TimelineCommandBridge.bind(history);
    }

    @AfterEach
    void tearDown() {
        clipboard.clear();
        TimelineCommandBridge.bind(null);
        audioService.setTimelineModel(previousAudioModel);
        for (Container container : created) {
            container.setLocked(false);
            manager.removeContainer(container);
        }
    }

    @Test
    void cutAndPasteBoundAudioRestoresContainerLinkAndEvents() {
        Fixture fixture = fixture();
        clipboard.cut(fixture.audioClip, fixture.audioTrack, model, history);
        assertTrue(fixture.audioTrack.getClips().isEmpty());
        assertTrue(fixture.audioTrack.getEvents().isEmpty());

        model.setCurrentTime(4f);
        clipboard.pasteToTrack(fixture.audioTrack, model);
        TimelineClip pasted = model.getSelectedClip();
        assertTrue(pasted instanceof AudioTimelineClip);
        assertEquals(fixture.containerId, pasted.getBoundContainerId());
        assertTrue(audioService.isAudioClipBound(pasted));
        assertSame(fixture.containerClip, audioService.getContainerClipForAudioClip(pasted).orElseThrow());
        assertEquals(1, fixture.audioTrack.getEvents().size());

        assertTrue(history.undo());
        assertFalse(fixture.audioTrack.getClips().contains(pasted));
        assertTrue(fixture.audioTrack.getEvents().isEmpty());
        assertTrue(history.redo());
        assertTrue(fixture.audioTrack.getClips().contains(pasted));
        assertTrue(audioService.isAudioClipBound(pasted));
        assertEquals(1, fixture.audioTrack.getEvents().size());
    }

    @Test
    void undoingCutPreventsPastingASecondBoundAudioClip() {
        Fixture fixture = fixture();
        clipboard.cut(fixture.audioClip, fixture.audioTrack, model, history);
        assertTrue(history.undo());
        assertSame(fixture.audioClip, fixture.audioTrack.getClips().getFirst());
        assertEquals(1, fixture.audioTrack.getEvents().size());

        model.setCurrentTime(4f);
        clipboard.pasteToTrack(fixture.audioTrack, model);
        assertEquals(1, fixture.audioTrack.getClips().size());
        assertSame(fixture.audioClip, fixture.audioTrack.getClips().getFirst());
        assertEquals(1, fixture.audioTrack.getEvents().size());
    }

    @Test
    void ordinaryCopyPastesDetachedAudioWithoutContainerLink() {
        Fixture fixture = fixture();
        clipboard.copy(fixture.audioClip, model);
        model.setCurrentTime(4f);
        clipboard.pasteToTrack(fixture.audioTrack, model);

        TimelineClip pasted = model.getSelectedClip();
        assertTrue(pasted instanceof AudioTimelineClip);
        assertNull(pasted.getBoundContainerId());
        assertFalse(audioService.isAudioClipBound(pasted));
        assertSame(fixture.audioClip, fixture.audioTrack.getClips().getFirst());
        assertEquals(2, fixture.audioTrack.getClips().size());
        assertEquals(2, fixture.audioTrack.getEvents().size());
    }

    @Test
    void crossModelPasteFailsWithoutLeavingClipsOrEvents() {
        Fixture fixture = fixture();
        clipboard.copy(fixture.audioClip, model);
        TimelineModel other = new TimelineModel(new TimelineViewportState());
        other.setSuppressPersistenceWrites(true);
        AudioTrack otherAudio = new AudioTrack("Other Audio");
        other.addTrack(otherAudio);
        audioService.setTimelineModel(other);

        assertThrows(IllegalStateException.class,
                () -> clipboard.pasteToTrack(otherAudio, other));
        assertTrue(otherAudio.getClips().isEmpty());
        assertTrue(otherAudio.getEvents().isEmpty());
        assertSame(fixture.audioClip, fixture.audioTrack.getClips().getFirst());
        assertEquals(1, fixture.audioTrack.getEvents().size());
    }

    private Fixture fixture() {
        Container container = manager.createContainer("Audio Owner", ContainerType.GENERAL);
        created.add(container);
        UUID containerId = container.getId();
        TimelineTrack containerTrack = new TimelineTrack("Container", TrackType.CONTAINER);
        AudioTrack audioTrack = new AudioTrack("Audio");
        model.addTrack(containerTrack);
        model.addTrack(audioTrack);
        TimelineClip containerClip = new TimelineClip("Audio Owner", 0f, 2f, 0);
        containerClip.bindToContainer(containerId, container.getName());
        containerTrack.addClip(containerClip);
        AudioTimelineClip audioClip = new AudioTimelineClip("Audio Owner", 0f, 2f, 0);
        audioClip.bindToContainer(containerId, container.getName());
        audioTrack.addClip(audioClip);
        audioService.setTimelineModel(model);
        audioService.onAudioClipBindingChanged(audioClip, null, containerId);
        audioService.insertClipEvents(audioClip, audioTrack,
                List.of(new AudioEvent("Place", "block.stone.place", 0.5f)), 0f);
        assertTrue(audioService.isAudioClipBound(audioClip));
        return new Fixture(containerId, containerClip, audioClip, audioTrack);
    }

    private record Fixture(UUID containerId, TimelineClip containerClip,
                           AudioTimelineClip audioClip, AudioTrack audioTrack) {
    }
}
