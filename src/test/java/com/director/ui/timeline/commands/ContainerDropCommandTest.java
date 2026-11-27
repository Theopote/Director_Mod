package com.director.ui.timeline.commands;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;
import com.director.core.container.ContainerType;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.audio.TimelineAudioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerDropCommandTest {
    private ContainerManager manager;
    private TimelineModel model;
    private TimelineCommandHistory history;
    private ContainerTimelineService timelineService;
    private TimelineAudioService audioService;
    private TimelineModel previousTimelineModel;
    private TimelineModel previousAudioModel;
    private final List<Container> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        DirectorServices.initialize();
        ContainerTimelineService.initialize();
        manager = DirectorServices.get().containers();
        timelineService = DirectorServices.get().containerTimeline();
        audioService = TimelineAudioService.getInstance();
        previousTimelineModel = timelineService.getTimelineModel();
        previousAudioModel = audioService.getTimelineModel();
        model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        model.getSnapSettings().setEnabled(false);
        timelineService.setTimelineModel(model);
        audioService.setTimelineModel(model);
        history = new TimelineCommandHistory();
        history.setModel(model);
        TimelineCommandBridge.bind(history);
    }

    @AfterEach
    void tearDown() {
        TimelineCommandBridge.bind(null);
        timelineService.setTimelineModel(previousTimelineModel);
        audioService.setTimelineModel(previousAudioModel);
        for (Container container : created) {
            container.setLocked(false);
            manager.removeContainer(container);
        }
    }

    @Test
    void creationUndoAndRedoRestoreContainerStateAndClipIdentity() {
        Container container = container("DropA");
        container.setIntakeEnabled(true);
        container.setState(ContainerState.VISIBLE, false);
        TimelineTrack track = track("Target");

        ContainerDropCommand command = new ContainerDropCommand(model, track, List.of(container), 1f, () -> {});
        history.executeCommand(command);

        TimelineClip clip = track.getClips().getFirst();
        assertSame(container, manager.getContainer(container.getId()));
        assertSame(clip, model.getSelectedClip());
        assertEquals(ContainerState.HIDDEN, container.getState());
        assertFalse(container.isIntakeEnabled());

        assertTrue(history.undo());
        assertTrue(track.getClips().isEmpty());
        assertEquals(ContainerState.VISIBLE, container.getState());
        assertTrue(container.isIntakeEnabled());

        assertTrue(history.redo());
        assertSame(clip, track.getClips().getFirst());
        assertEquals(ContainerState.HIDDEN, container.getState());
        assertFalse(container.isIntakeEnabled());
    }

    @Test
    void movingAndRippleUseOffsetsSoUndoRestoresGlobalKeyframes() {
        Container container = container("Moving");
        TimelineTrack source = track("Source");
        TimelineTrack target = track("Target");
        TimelineClip moving = new TimelineClip("Moving", 1f, 3f, 0);
        moving.bindToContainer(container.getId(), container.getName());
        moving.addKeyframe(1.5f);
        source.addClip(moving);
        TimelineClip blocker = new TimelineClip("Blocker", 2f, 4f, 0);
        target.addClip(blocker);

        ContainerDropCommand command = new ContainerDropCommand(model, target, List.of(container), 2.5f, () -> {});
        history.executeCommand(command);

        assertSame(target, moving.getParentTrack());
        assertEquals(2.5f, moving.getStartTime(), 0.0001f);
        assertEquals(3.0f, moving.getClipKeyframes().getFirst().getTime(), 0.0001f);
        assertEquals(4.5f, blocker.getStartTime(), 0.0001f);
        assertEquals(6.5f, blocker.getEndTime(), 0.0001f);

        assertTrue(history.undo());
        assertSame(source, moving.getParentTrack());
        assertEquals(1.0f, moving.getStartTime(), 0.0001f);
        assertEquals(1.5f, moving.getClipKeyframes().getFirst().getTime(), 0.0001f);
        assertEquals(2.0f, blocker.getStartTime(), 0.0001f);
        assertEquals(4.0f, blocker.getEndTime(), 0.0001f);

        assertTrue(history.redo());
        assertSame(target, moving.getParentTrack());
        assertEquals(3.0f, moving.getClipKeyframes().getFirst().getTime(), 0.0001f);
    }

    @Test
    void batchFailureRollsBackEarlierClipsAndContainerSideEffects() {
        Container first = container("First");
        Container second = container("Second");
        first.setIntakeEnabled(true);
        second.setIntakeEnabled(true);
        FailingTrack target = new FailingTrack("Target", "Second");
        model.addTrack(target);

        ContainerDropCommand command = new ContainerDropCommand(
                model, target, List.of(first, second), 1f, () -> {});
        assertThrows(IllegalStateException.class, () -> history.executeCommand(command));

        assertTrue(target.getClips().isEmpty());
        assertEquals(ContainerState.VISIBLE, first.getState());
        assertEquals(ContainerState.VISIBLE, second.getState());
        assertTrue(first.isIntakeEnabled());
        assertTrue(second.isIntakeEnabled());
        assertEquals(0, history.getUndoSize());
        assertFalse(history.canUndo());
    }

    @Test
    void lockedOrPlayingStateIsRejectedBeforeAnyMutation() {
        Container container = container("Locked");
        TimelineTrack target = track("Target");
        target.setLocked(true);
        assertThrows(IllegalStateException.class,
                () -> new ContainerDropCommand(model, target, List.of(container), 1f, () -> {}));
        assertTrue(target.getClips().isEmpty());

        target.setLocked(false);
        container.setLocked(true);
        assertThrows(IllegalStateException.class,
                () -> new ContainerDropCommand(model, target, List.of(container), 1f, () -> {}));
        assertEquals(ContainerState.VISIBLE, container.getState());

        container.setLocked(false);
        model.setPlaying(true);
        assertThrows(IllegalStateException.class,
                () -> new ContainerDropCommand(model, target, List.of(container), 1f, () -> {}));
        assertTrue(target.getClips().isEmpty());
    }

    @Test
    void lockedSourceTrackRejectsExistingClipMove() {
        Container container = container("MoveLocked");
        TimelineTrack source = track("Source");
        source.setLocked(true);
        TimelineTrack target = track("Target");
        TimelineClip clip = new TimelineClip("MoveLocked", 0f, 2f, 0);
        clip.bindToContainer(container.getId(), container.getName());
        source.addClip(clip);

        assertThrows(IllegalStateException.class,
                () -> new ContainerDropCommand(model, target, List.of(container), 3f, () -> {}));
        assertSame(source, clip.getParentTrack());
        assertTrue(target.getClips().isEmpty());
    }

    private Container container(String name) {
        Container container = manager.createContainer(name, ContainerType.GENERAL);
        created.add(container);
        return container;
    }

    private TimelineTrack track(String name) {
        TimelineTrack track = new TimelineTrack(name, TrackType.CONTAINER);
        model.addTrack(track);
        return track;
    }

    private static final class FailingTrack extends TimelineTrack {
        private final String rejectedLabel;
        private boolean reject = true;

        private FailingTrack(String name, String rejectedLabel) {
            super(name, TrackType.CONTAINER);
            this.rejectedLabel = rejectedLabel;
        }

        @Override
        public boolean addClip(TimelineClip clip) {
            if (reject && clip != null && rejectedLabel.equals(clip.getLabel())) {
                return false;
            }
            return super.addClip(clip);
        }
    }
}
