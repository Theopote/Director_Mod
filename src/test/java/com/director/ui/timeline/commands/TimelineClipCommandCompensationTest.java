package com.director.ui.timeline.commands;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.TimelineMarker;
import com.director.timeline.model.audio.AudioClipKeyframe;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.timeline.model.audio.AudioTrack;
import com.director.timeline.model.clip.ClipKeyframe;
import com.director.timeline.model.weather.WeatherKeyframe;
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

    @Test
    void equalLengthAudioMovePreservesKeyframeIdentityAndAutomation() {
        AudioTrack audioTrack = new AudioTrack("Audio");
        model.addTrack(audioTrack);
        AudioTimelineClip clip = new AudioTimelineClip("Volume", 1f, 3f, 0);
        AudioClipKeyframe keyframe = new AudioClipKeyframe(
                1.5f, TimelineInterpolation.SMOOTH, 0.35f);
        clip.addKeyframe(keyframe);
        audioTrack.addClip(clip);
        var id = keyframe.getId();

        history.executeCommand(new MoveClipCommand(
                model, clip, audioTrack, audioTrack, 1f, 3f, 4f, 6f));

        AudioClipKeyframe moved = (AudioClipKeyframe) clip.getClipKeyframes().getFirst();
        assertEquals(id, moved.getId());
        assertEquals(4.5f, moved.getTime(), 0.0001f);
        assertEquals(TimelineInterpolation.SMOOTH, moved.getInterpolation());
        assertEquals(0.35f, moved.getVolume(), 0.0001f);
        assertTrue(history.undo());
        AudioClipKeyframe restored = (AudioClipKeyframe) clip.getClipKeyframes().getFirst();
        assertEquals(id, restored.getId());
        assertEquals(1.5f, restored.getTime(), 0.0001f);
        assertEquals(0.35f, restored.getVolume(), 0.0001f);
        assertTrue(history.redo());
        assertEquals(id, clip.getClipKeyframes().getFirst().getId());
        assertEquals(4.5f, clip.getClipKeyframes().getFirst().getTime(), 0.0001f);
    }

    @Test
    void audioLengthChangeRemapsKeyframesAndUndoRedoPreserveAutomation() {
        AudioTrack audioTrack = new AudioTrack("Audio");
        model.addTrack(audioTrack);
        AudioTimelineClip clip = new AudioTimelineClip("Volume", 0f, 4f, 0);
        AudioClipKeyframe keyframe = new AudioClipKeyframe(
                3f, TimelineInterpolation.SMOOTH, 0.6f);
        clip.addKeyframe(keyframe);
        audioTrack.addClip(clip);
        var id = keyframe.getId();

        history.executeCommand(new SetClipLengthCommand(model, clip, 2f));
        AudioClipKeyframe remapped = (AudioClipKeyframe) clip.getClipKeyframes().getFirst();
        assertEquals(id, remapped.getId());
        assertEquals(1.5f, remapped.getTime(), 0.0001f);
        assertEquals(0.6f, remapped.getVolume(), 0.0001f);

        assertTrue(history.undo());
        AudioClipKeyframe restored = (AudioClipKeyframe) clip.getClipKeyframes().getFirst();
        assertEquals(id, restored.getId());
        assertEquals(3f, restored.getTime(), 0.0001f);
        assertEquals(TimelineInterpolation.SMOOTH, restored.getInterpolation());
        assertEquals(0.6f, restored.getVolume(), 0.0001f);
        assertTrue(history.redo());
        assertEquals(1.5f, clip.getClipKeyframes().getFirst().getTime(), 0.0001f);
        assertEquals(2f, clip.getLength(), 0.0001f);
    }

    @Test
    void noOpCommandsDoNotEnterOrClearHistory() {
        TimelineTrack track = track("Visible");
        track.setVisible(false);
        history.executeCommand(new SetTrackVisibleCommand(model, track, true));
        assertTrue(history.undo());
        assertEquals(1, history.getRedoSize());

        history.executeCommand(new SetTrackVisibleCommand(model, track, false));
        history.executeCommand(new MoveTrackCommand(model, 0, 0));
        history.executeCommand(new MoveMarkerCommand(model, null, 0f, 1f));
        history.executeCommand(new MoveAutomationKeyframeCommand(model, null, null, 0f, 1f));
        history.executeCommand(new MoveAutomationKeyframeBetweenTracksCommand(
                model, null, null, null, 0f, 1f));

        assertEquals(0, history.getUndoSize());
        assertEquals(1, history.getRedoSize());
        assertTrue(history.redo());
        assertTrue(track.isVisible());
    }

    @Test
    void unboundCommandBridgeRejectsInsteadOfMutatingTimeline() {
        TimelineTrack track = track("Visible");
        track.setVisible(false);
        TimelineCommandBridge.bind(null);
        try {
            assertThrows(IllegalStateException.class, () ->
                    TimelineCommandBridge.execute(new SetTrackVisibleCommand(model, track, true)));
            assertFalse(track.isVisible());
        } finally {
            TimelineCommandBridge.bind(null);
        }
    }

    @Test
    void batchDeleteRollsBackEarlierSubcommandsWhenLaterSubcommandFails() {
        TimelineCommandHistory failingHistory = new TimelineCommandHistory();
        TimelineTrack track = new TimelineTrack("Batch", TrackType.CONTAINER);
        TimelineClip first = new TimelineClip("First", 0f, 1f, 0);
        TimelineClip second = new TimelineClip("Second", 2f, 3f, 0);
        ClipKeyframe keyframe = new ClipKeyframe(0.5f, TimelineInterpolation.SMOOTH);
        first.addKeyframe(keyframe);
        track.addClip(first);
        track.addClip(second);
        TimelineMarker marker = new TimelineMarker("Fail", 5f);
        TimelineModel markerFailureModel = new TimelineModel(new TimelineViewportState(), false) {
            @Override
            public void removeMarker(java.util.UUID markerId) {
                throw new IllegalStateException("Marker deletion failed");
            }
        };
        markerFailureModel.setSuppressPersistenceWrites(true);
        markerFailureModel.addTrack(track);
        markerFailureModel.addMarker(marker);
        failingHistory.setModel(markerFailureModel);

        BatchDeleteSelectionCommand command = new BatchDeleteSelectionCommand(
                markerFailureModel, List.of(first, second, keyframe, marker));
        assertThrows(IllegalStateException.class, () -> failingHistory.executeCommand(command));

        assertEquals(List.of(first, second), track.getClips());
        assertSame(keyframe, first.getClipKeyframes().getFirst());
        assertTrue(markerFailureModel.getMarkers().contains(marker));
        assertEquals(0, failingHistory.getUndoSize());
    }

    @Test
    void batchAutomationDeleteRestoresOnlyEntriesActuallyRemoved() {
        TimelineTrack track = track("Automation");
        WeatherKeyframe present = new WeatherKeyframe("Present", 1f);
        WeatherKeyframe absent = new WeatherKeyframe("Absent", 2f);
        track.addAutomationKeyframe(present);
        BatchRemoveAutomationKeyframesCommand command = new BatchRemoveAutomationKeyframesCommand(
                model, List.of(
                        new BatchRemoveAutomationKeyframesCommand.Entry(track, present),
                        new BatchRemoveAutomationKeyframesCommand.Entry(track, absent)));

        history.executeCommand(command);
        assertTrue(track.getAutomationKeyframes().isEmpty());
        assertTrue(history.undo());
        assertEquals(List.of(present), track.getAutomationKeyframes());
        assertTrue(history.redo());
        assertTrue(track.getAutomationKeyframes().isEmpty());
    }

    @Test
    void batchAutomationUndoFailureKeepsPreexistingEntries() {
        WeatherKeyframe first = new WeatherKeyframe("First", 1f);
        WeatherKeyframe second = new WeatherKeyframe("Second", 2f);
        AtomicBoolean failRestore = new AtomicBoolean();
        TimelineTrack track = new TimelineTrack("Automation", TrackType.CONTAINER) {
            @Override
            public void addAutomationKeyframe(TimelineSelectable keyframe) {
                if (failRestore.get()) {
                    throw new IllegalStateException("Automation restore failed");
                }
                super.addAutomationKeyframe(keyframe);
            }
        };
        model.addTrack(track);
        track.addAutomationKeyframe(first);
        track.addAutomationKeyframe(second);
        BatchRemoveAutomationKeyframesCommand command = new BatchRemoveAutomationKeyframesCommand(
                model, List.of(
                        new BatchRemoveAutomationKeyframesCommand.Entry(track, first),
                        new BatchRemoveAutomationKeyframesCommand.Entry(track, second)));

        history.executeCommand(command);
        track.addAutomationKeyframe(first);
        failRestore.set(true);
        assertThrows(IllegalStateException.class, history::undo);

        assertEquals(List.of(first), track.getAutomationKeyframes());
        assertEquals(1, history.getUndoSize());
        failRestore.set(false);
        assertTrue(history.undo());
        assertEquals(2, track.getAutomationKeyframes().size());
        assertTrue(track.getAutomationKeyframes().containsAll(List.of(first, second)));
    }

    @Test
    void audioEventMovesWithinAndBetweenTracksRemainUndoable() {
        AudioTrack source = new AudioTrack("Source");
        AudioTrack target = new AudioTrack("Target");
        model.addTrack(source);
        model.addTrack(target);
        AudioEvent event = new AudioEvent("Place", "block.stone.place", 1f);
        source.addEvent(event);

        history.executeCommand(new MoveAutomationKeyframeCommand(model, source, event, 1f, 3f));
        assertEquals(3f, event.getTime());
        assertTrue(history.undo());
        assertEquals(1f, event.getTime());
        assertTrue(history.redo());
        assertEquals(3f, event.getTime());

        history.executeCommand(new MoveAutomationKeyframeBetweenTracksCommand(
                model, source, target, event, 3f, 5f));
        assertTrue(source.getEvents().isEmpty());
        assertEquals(List.of(event), target.getEvents());
        assertEquals(5f, event.getTime());
        assertTrue(history.undo());
        assertEquals(List.of(event), source.getEvents());
        assertTrue(target.getEvents().isEmpty());
        assertEquals(3f, event.getTime());
        assertTrue(history.redo());
        assertTrue(source.getEvents().isEmpty());
        assertEquals(List.of(event), target.getEvents());
        assertEquals(5f, event.getTime());
    }

    @Test
    void clipKeyframeDeleteUndoPreservesSubtypeUuidInterpolationAndValue() {
        TimelineTrack track = track("Audio automation");
        AudioTimelineClip clip = new AudioTimelineClip("Volume", 0f, 2f, 0);
        AudioClipKeyframe keyframe = new AudioClipKeyframe(
                1f, TimelineInterpolation.SMOOTH, 0.25f);
        clip.addKeyframe(keyframe);
        track.addClip(clip);
        var id = keyframe.getId();

        history.executeCommand(new RemoveClipKeyframeCommand(model, track, clip, keyframe));
        assertTrue(clip.getClipKeyframes().isEmpty());
        assertTrue(history.undo());
        ClipKeyframe restored = clip.getClipKeyframes().getFirst();
        assertTrue(restored instanceof AudioClipKeyframe);
        assertEquals(id, restored.getId());
        assertEquals(TimelineInterpolation.SMOOTH, restored.getInterpolation());
        assertEquals(0.25f, ((AudioClipKeyframe) restored).getVolume(), 0.0001f);
    }

    @Test
    void trackMoveAndUndoEmitStructuralNotifications() {
        TimelineTrack first = track("First");
        TimelineTrack second = track("Second");
        List<String> changes = new ArrayList<>();
        model.addModelChangeListener(changes::add);

        history.executeCommand(new MoveTrackCommand(model, 0, 1));
        assertSame(first, model.getTrackAt(1));
        assertTrue(history.undo());
        assertSame(first, model.getTrackAt(0));
        assertSame(second, model.getTrackAt(1));
        assertEquals(2, changes.stream().filter("track_moved"::equals).count());
    }

    private TimelineTrack track(String name) {
        TimelineTrack track = new TimelineTrack(name, TrackType.CONTAINER);
        model.addTrack(track);
        return track;
    }
}
