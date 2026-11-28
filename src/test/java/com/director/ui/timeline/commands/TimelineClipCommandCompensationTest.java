package com.director.ui.timeline.commands;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.audio.AudioTrack;
import com.director.ui.timeline.audio.TimelineAudioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineClipCommandCompensationTest {
    private ContainerManager manager;
    private ContainerTimelineService timelineService;
    private TimelineAudioService audioService;
    private TimelineModel previousTimelineModel;
    private TimelineModel previousAudioModel;
    private TimelineModel model;
    private TimelineCommandHistory history;
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
    }

    @AfterEach
    void tearDown() {
        timelineService.setTimelineModel(previousTimelineModel);
        audioService.setTimelineModel(previousAudioModel);
        for (Container container : created) {
            container.setLocked(false);
            manager.removeContainer(container);
        }
    }

    @Test
    void insertFailureRemovesPartialAudioAndRemainsRetryable() {
        TimelineTrack target = track("Target");
        AudioTrack audioTrack = new AudioTrack("Audio");
        model.addTrack(audioTrack);
        TimelineClip clip = new TimelineClip("Insert", 0f, 2f, 0);
        AtomicBoolean fail = new AtomicBoolean(true);
        InsertClipCommand command = new InsertClipCommand(model, target, clip, destination -> {
            audioService.insertClipEvents(destination, audioTrack,
                    List.of(new AudioEvent("Place", "block.stone.place", 0.5f)), 0f);
            if (fail.get()) {
                throw new IllegalStateException("Audio initialization failed");
            }
        });

        assertThrows(IllegalStateException.class, () -> history.executeCommand(command));
        assertTrue(target.getClips().isEmpty());
        assertTrue(audioTrack.getEvents().isEmpty());
        assertEquals(0, history.getUndoSize());

        fail.set(false);
        history.executeCommand(command);
        assertSame(clip, target.getClips().getFirst());
        assertEquals(1, audioTrack.getEvents().size());

        assertTrue(history.undo());
        assertTrue(target.getClips().isEmpty());
        assertTrue(audioTrack.getEvents().isEmpty());
        assertTrue(history.redo());
        assertSame(clip, target.getClips().getFirst());
        assertEquals(1, audioTrack.getEvents().size());
    }

    @Test
    void blockedDeleteUndoDoesNotRestoreAudioAndCanRetryWithoutDuplicates() {
        Container container = manager.createContainer("Bound", ContainerType.GENERAL);
        created.add(container);
        TimelineTrack target = track("Target");
        AudioTrack audioTrack = new AudioTrack("Audio");
        model.addTrack(audioTrack);
        TimelineClip clip = new TimelineClip("Bound", 0f, 2f, 0);
        clip.bindToContainer(container.getId(), container.getName());
        target.addClip(clip);
        audioService.insertClipEvents(clip, audioTrack,
                List.of(new AudioEvent("Place", "block.stone.place", 0.5f)), 0f);
        DeleteClipCommand command = new DeleteClipCommand(model, clip, target.getClips());

        history.executeCommand(command);
        assertTrue(target.getClips().isEmpty());
        assertTrue(audioTrack.getEvents().isEmpty());

        TimelineClip occupant = new TimelineClip("Occupant", 3f, 5f, 0);
        occupant.bindToContainer(container.getId(), container.getName());
        boolean previous = audioService.isSuppressAutoCreate();
        audioService.setSuppressAutoCreate(true);
        try {
            target.addClip(occupant);
        } finally {
            audioService.setSuppressAutoCreate(previous);
        }

        assertThrows(IllegalStateException.class, history::undo);
        assertFalse(target.getClips().contains(clip));
        assertTrue(target.getClips().contains(occupant));
        assertTrue(audioTrack.getEvents().isEmpty());
        assertEquals(1, history.getUndoSize());
        assertEquals(0, history.getRedoSize());

        target.removeClip(occupant);
        assertTrue(history.undo());
        assertSame(clip, target.getClips().getFirst());
        assertEquals(1, audioTrack.getEvents().size());

        assertTrue(history.redo());
        assertTrue(target.getClips().isEmpty());
        assertTrue(audioTrack.getEvents().isEmpty());
        assertTrue(history.undo());
        assertSame(clip, target.getClips().getFirst());
        assertEquals(1, audioTrack.getEvents().size());
    }

    @Test
    void equalLengthMoveAndUndoPreserveGlobalKeyframes() {
        TimelineTrack source = track("Source");
        TimelineTrack target = track("Target");
        TimelineClip clip = new TimelineClip("Move", 1f, 3f, 0);
        clip.addKeyframe(1.5f);
        source.addClip(clip);
        var keyframeId = clip.getClipKeyframes().getFirst().getId();
        MoveClipCommand command = new MoveClipCommand(
                model, clip, source, target, 1f, 3f, 4f, 6f);

        history.executeCommand(command);
        assertSame(target, clip.getParentTrack());
        assertEquals(4.5f, clip.getClipKeyframes().getFirst().getTime(), 0.0001f);
        assertTrue(history.undo());
        assertSame(source, clip.getParentTrack());
        assertEquals(1.5f, clip.getClipKeyframes().getFirst().getTime(), 0.0001f);
        assertEquals(keyframeId, clip.getClipKeyframes().getFirst().getId());
        assertTrue(history.redo());
        assertEquals(4.5f, clip.getClipKeyframes().getFirst().getTime(), 0.0001f);
        assertEquals(keyframeId, clip.getClipKeyframes().getFirst().getId());
    }

    @Test
    void resizeUndoRestoresPrunedKeyframesAndRedoKeepsFinalState() {
        TimelineTrack target = track("Target");
        TimelineClip clip = new TimelineClip("Resize", 0f, 4f, 0);
        clip.addKeyframe(3f);
        clip.addVisibilityKeyframe(new com.director.timeline.model.VisibilityKeyframe(
                2.5f, 0.4f, com.director.timeline.model.VisibilityKeyframe.VisibilityMode.GHOST));
        target.addClip(clip);
        TimelineClip gestureStart = clip.snapshot();
        var keyframeId = clip.getClipKeyframes().getFirst().getId();

        clip.setTimeRange(0f, 2f);
        TimelineClip preview = clip.snapshot();
        ResizeClipCommand command = new ResizeClipCommand(
                model, clip, 0f, 4f, 0f, 2f, gestureStart);

        history.executeCommand(command);
        assertTrue(clip.hasSameStateAs(preview));
        assertEquals(preview.getVisibilityKeyframes(), clip.getVisibilityKeyframes());
        assertTrue(history.undo());
        assertTrue(clip.hasSameStateAs(gestureStart));
        assertEquals(keyframeId, clip.getClipKeyframes().getFirst().getId());
        assertEquals(1, clip.getVisibilityKeyframes().size());
        assertTrue(history.redo());
        assertTrue(clip.hasSameStateAs(preview));
    }

    @Test
    void baseCommandInvokesCompensationAndKeepsFailedExecutionOutOfHistory() {
        AtomicBoolean value = new AtomicBoolean(false);
        BaseTimelineCommand command = new BaseTimelineCommand(model, "test") {
            @Override
            protected void doExecute() {
                value.set(true);
                throw new IllegalStateException("execute failed");
            }

            @Override
            protected void doUndo() {
            }

            @Override
            protected void compensateExecute() {
                value.set(false);
            }

            @Override
            public String getDescription() {
                return "test";
            }
        };

        assertThrows(IllegalStateException.class, () -> history.executeCommand(command));
        assertFalse(value.get());
        assertEquals(0, history.getUndoSize());
    }

    private TimelineTrack track(String name) {
        TimelineTrack track = new TimelineTrack(name, TrackType.CONTAINER);
        model.addTrack(track);
        return track;
    }
}
