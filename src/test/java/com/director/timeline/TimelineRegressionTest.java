package com.director.timeline;

import com.director.timeline.audio.TimelineAudioPlaybackPolicy;
import com.director.timeline.layout.TimelineClipLayoutResolver;
import com.director.timeline.layout.TimelineClipLayoutResolver.Interval;
import com.director.timeline.model.*;
import com.director.timeline.model.audio.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.director.timeline.snapping.TimelineSnapping;
import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.ui.timeline.commands.*;
import com.director.ui.timeline.playback.*;
import com.director.ui.timeline.playback.audio.AudioTrackSampler;
import com.director.ui.timeline.state.ClipboardManager;
import org.junit.jupiter.api.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRegressionTest {
    TimelineModel model;
    TimelineAudioService audio;
    TimelineCommandHistory history;
    @BeforeEach void setup() {
        model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        audio = TimelineAudioService.getInstance();
        audio.setSuppressAutoCreate(true);
        audio.setTimelineModel(model);
        model.setAudioLoadHook(audio);
        history = new TimelineCommandHistory(); history.setModel(model);
        TimelineCommandBridge.bind(history);
        TimelineAudioController.getInstance().setTimelineModel(model);
    }
    @AfterEach void cleanup() {
        audio.setTimelineModel(null);
        TimelineAudioController.getInstance().setTimelineModel(null);
        TimelineCommandBridge.bind(null);
        history.setModel(null);
        ClipboardManager.getInstance().clear();
        PlaybackOrchestrator.get().bindModel(null);
    }
    AudioTrack track(String name) { var track = new AudioTrack(name); model.addTrack(track); return track; }
    void link(TimelineClip clip, AudioEvent event) throws Exception {
        Method method = TimelineAudioService.class.getDeclaredMethod("linkEventToClip", TimelineClip.class, AudioEvent.class);
        method.setAccessible(true); method.invoke(audio, clip, event);
    }
    void linkClips(TimelineClip container, TimelineClip sound) throws Exception {
        Method method = TimelineAudioService.class.getDeclaredMethod("linkAudioClip", TimelineClip.class, TimelineClip.class);
        method.setAccessible(true); method.invoke(audio, container, sound);
    }
    int samplerCount() throws Exception {
        Field field = TimelineAudioController.class.getDeclaredField("samplers"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(TimelineAudioController.getInstance())).size();
    }
    @Test void actorDuplicationRetainsPayloadKeysAndStableRedoIdentity() {
        var track = new TimelineTrack("actor", TrackType.ACTOR); model.addTrack(track);
        var entity = new EntityTransformClip("move", 0, 4); entity.setToPos(3, 4, 5);
        entity.addKeyframe(new EntityClipKeyframe(2));
        track.getOrCreateActorTrackPayload().addClip(entity);
        var wrapper = new EntityTimelineClip(entity, 123); track.addClip(wrapper);
        var command = new DuplicateClipCommand(model, track, wrapper); history.executeCommand(command);
        var copy = assertInstanceOf(EntityTimelineClip.class, command.getDuplicatedClip());
        assertNotEquals(entity.getId(), copy.getEntityClip().getId());
        assertEquals(2, track.getActorTrackPayload().getClips().size());
        assertEquals(6.5f, copy.getEntityClip().getKeyframes().getFirst().getTime());
        assertEquals(3, ((EntityTransformClip) copy.getEntityClip()).getToPos().x);
        ((EntityTransformClip) copy.getEntityClip()).setToPos(9, 9, 9);
        assertEquals(3, entity.getToPos().x);
        assertTrue(history.undo()); assertEquals(1, track.getActorTrackPayload().getClips().size());
        assertTrue(history.redo()); assertSame(copy, command.getDuplicatedClip());
        assertEquals(2, track.getActorTrackPayload().getClips().size());
    }
    @Test void everyEntitySubtypeCopiesWithoutLosingType() {
        for (EntityClipType type : EntityClipType.values()) {
            var dto = new TimelineModelPersistence.EntityClipDTO(); dto.clipType = type.name(); dto.name = "typed";
            dto.startTime = 0; dto.endTime = 4; dto.weight = 0.6f; dto.muted = true;
            var entity = TimelineModelPersistence.entityClipFromDTO(dto);
            var copy = assertInstanceOf(EntityTimelineClip.class, TimelineClipCopies.copy(new EntityTimelineClip(entity, 5)));
            assertEquals(entity.getClass(), copy.getEntityClip().getClass());
            assertTrue(copy.getEntityClip().isMuted()); assertEquals(0.6f, copy.getEntityClip().getWeight());
        }
    }
    @Test void clipboardPastePreservesLengthAndParticipatesInUndo() {
        var track = track("paste"); model.setCurrentTime(5);
        var original = new AudioTimelineClip("source", 0, 4, 0);
        original.addKeyframe(new AudioClipKeyframe(2, TimelineInterpolation.LINEAR, 0.3f));
        ClipboardManager.getInstance().copy(original); ClipboardManager.getInstance().pasteToTrack(track, model);
        var copy = assertInstanceOf(AudioTimelineClip.class, model.getSelectedClip());
        assertEquals(5, copy.getStartTime()); assertEquals(9, copy.getEndTime());
        assertEquals(7, copy.getClipKeyframes().getFirst().getTime());
        assertTrue(history.undo()); assertTrue(track.getClips().isEmpty());
        assertTrue(history.redo()); assertSame(copy, track.getClips().getFirst());
    }
    @Test void clipboardCannotPasteIntoLockedOrIncompatibleTrack() {
        var track = track("locked"); track.setLocked(true);
        ClipboardManager.getInstance().copy(new AudioTimelineClip("source", 0, 4, 0));
        ClipboardManager.getInstance().pasteToTrack(track, model); assertTrue(track.getClips().isEmpty());
        var actor = new TimelineTrack("actor", TrackType.ACTOR);
        ClipboardManager.getInstance().pasteToTrack(actor, model); assertTrue(actor.getClips().isEmpty());
    }
    @Test void audioDuplicationPreservesEventsAndOwnAutomationAcrossUndo() throws Exception {
        var track = track("sound"); var clip = new AudioTimelineClip("sound", 0, 4, 0); track.addClip(clip);
        clip.addKeyframe(new AudioClipKeyframe(0, TimelineInterpolation.LINEAR, 0.25f));
        var event = new AudioEvent("edited", "minecraft:block.stone.place", 2); event.setPitch(1.7f); event.setVolume(0.6f);
        track.addEvent(event); link(clip, event);
        var command = new DuplicateClipCommand(model, track, clip); history.executeCommand(command);
        assertEquals(2, track.getEvents().size()); var copyEvent = track.getEvents().getLast();
        assertEquals(6.5f, copyEvent.getTime()); assertEquals(1.7f, copyEvent.getPitch());
        assertEquals(0.25f, audio.getVolumeMultiplierForEvent(copyEvent));
        history.undo(); assertEquals(1, track.getEvents().size());
        history.redo(); assertEquals(2, track.getEvents().size()); assertSame(copyEvent, track.getEvents().getLast());
    }
    @Test void deleteAudioClipUndoRestoresEditedEventAndLink() throws Exception {
        var track = track("sound"); var clip = new AudioTimelineClip("sound", 0, 4, 0); track.addClip(clip);
        var event = new AudioEvent("edited", "minecraft:block.stone.place", 2); event.setVolume(0.4f); track.addEvent(event); link(clip, event);
        var command = new DeleteClipCommand(model, clip, track.getClips()); history.executeCommand(command);
        assertTrue(track.getEvents().isEmpty()); history.undo();
        assertSame(event, track.getEvents().getFirst()); assertEquals(0.4f, event.getVolume());
        clip.setTimeRange(5, 9); audio.onClipTimeRangeChanged(clip, 0, 4, 5, 9); assertEquals(7, event.getTime());
    }
    @Test void deletingContainerTrackRestoresLinkedAudioOnUndo() throws Exception {
        var blocks = new TimelineTrack("blocks", TrackType.CONTAINER); model.addTrack(blocks);
        var clip = new TimelineClip("blocks", 0, 4, 0); blocks.addClip(clip);
        var sound = track("sound"); var linked = new AudioTimelineClip("linked", 0, 4, 0); sound.addClip(linked); linkClips(clip, linked);
        var event = new AudioEvent("edited", "", 2); sound.addEvent(event); link(clip, event);
        history.executeCommand(new DeleteTrackCommand(model, blocks, model.getTracks()));
        assertTrue(sound.getClips().isEmpty()); assertTrue(sound.getEvents().isEmpty());
        history.undo(); assertSame(blocks, model.getTracks().getFirst()); assertSame(linked, sound.getClips().getFirst());
        assertSame(event, sound.getEvents().getFirst());
        history.redo(); assertTrue(sound.getEvents().isEmpty());
    }
    @Test void deletionDoesNotRemoveIndependentOverlappingEvents() {
        var sound = track("sound"); var clip = new AudioTimelineClip("clip", 0, 4, 0); sound.addClip(clip);
        var independent = new AudioEvent("independent", "", 2); sound.addEvent(independent);
        history.executeCommand(new DeleteClipCommand(model, clip, sound.getClips()));
        assertSame(independent, sound.getEvents().getFirst());
    }
    @Test void deleteAudioTrackNotifiesAndRestoresSamplerAtOriginalIndex() throws Exception {
        var first = track("first"); var second = track("second"); assertEquals(2, samplerCount());
        history.executeCommand(new DeleteTrackCommand(model, first, model.getTracks())); assertEquals(1, samplerCount());
        history.undo(); assertEquals(2, samplerCount()); assertSame(first, model.getTracks().getFirst());
    }
    @Test void resetClearsOldHistoryAndAudioSamplers() throws Exception {
        var sound = track("old"); history.executeCommand(new DeleteTrackCommand(model, sound, model.getTracks())); history.undo();
        Method method = TimelineModel.class.getDeclaredMethod("resetInMemoryToDefaults"); method.setAccessible(true); method.invoke(model);
        assertFalse(history.undo()); assertFalse(history.redo()); assertEquals(0, samplerCount()); assertTrue(model.getTracks().isEmpty());
    }
    @Test void crossTrackMoveUndoRestoresOwnerAndSoundEvents() throws Exception {
        var first = track("first"); var second = track("second");
        var clip = new AudioTimelineClip("clip", 0, 4, 0); first.addClip(clip);
        var event = new AudioEvent("event", "", 2); first.addEvent(event); link(clip, event);
        var command = new MoveClipCommand(model, clip, first, second, 0, 4, 5, 9); history.executeCommand(command);
        assertSame(second, clip.getParentTrack()); assertEquals(7, event.getTime()); assertTrue(first.getEvents().isEmpty());
        history.undo(); assertSame(first, clip.getParentTrack()); assertEquals(2, event.getTime()); assertSame(event, first.getEvents().getFirst());
        history.redo(); assertSame(second, clip.getParentTrack()); assertSame(event, second.getEvents().getFirst());
    }
    @Test void verticalOnlyMoveRemainsUndoable() {
        var first = track("first"); var second = track("second"); var clip = new AudioTimelineClip("clip", 0, 4, 0); first.addClip(clip);
        history.executeCommand(new MoveClipCommand(model, clip, first, second, 0, 4, 0, 4));
        history.undo(); assertSame(first, clip.getParentTrack());
    }
    @Test void linkedEventsOnSecondTrackFollowMoveAndPreview() throws Exception {
        track("first"); var second = track("second"); var clip = new AudioTimelineClip("clip", 0, 4, 0); second.addClip(clip);
        var event = new AudioEvent("event", "", 2); second.addEvent(event); link(clip, event);
        audio.beginClipTimeRangePreview(clip, 0, 4); audio.updateClipTimeRangePreview(clip, 5, 9);
        assertEquals(7, audio.getPreviewTimeForEvent(event)); assertEquals(2, event.getTime()); audio.endClipTimeRangePreview(clip);
        history.executeCommand(new MoveClipCommand(model, new ClipAdapter(clip), 5, 9)); assertEquals(7, event.getTime());
    }
    @Test void linkedAudioRangeMovesAtomicallyAndRemapsItsOwnDuration() throws Exception {
        var blocks = new TimelineTrack("blocks", TrackType.CONTAINER); model.addTrack(blocks);
        var clip = new TimelineClip("blocks", 0, 4, 0); blocks.addClip(clip);
        var track = track("sound"); var linked = new AudioTimelineClip("linked", 0, 6, 0); track.addClip(linked);
        linked.addKeyframe(new AudioClipKeyframe(3, TimelineInterpolation.LINEAR, 0.5f)); linkClips(clip, linked);
        clip.setTimeRange(5, 9); audio.onClipTimeRangeChanged(clip, 0, 4, 5, 9);
        assertEquals(5, linked.getStartTime());
        assertEquals(5 + linked.getLength() / 2, linked.getClipKeyframes().getFirst().getTime());
    }
    @Test void durationAndOutPointSurviveRoundTripAndLegacyFilesKeepDefault() {
        model.setDuration(60); model.setOutPoint(40);
        var dto = TimelineModelPersistence.toDTO(model, model.getViewportState());
        TimelineModelPersistence.fromDTO(dto, model, model.getViewportState());
        assertEquals(60, model.getDuration()); assertEquals(40, model.getOutPoint());
        dto.duration = null; dto.outPoint = null; TimelineModelPersistence.fromDTO(dto, model, model.getViewportState()); assertEquals(20, model.getDuration());
    }
    @Test void layoutFindsLegalNearestGapEvenWithManyOrUnsortedBlockers() {
        assertEquals(5, TimelineClipLayoutResolver.resolveNearestStart(List.of(new Interval(0, 5)), 1, 2, 0));
        var intervals = new ArrayList<Interval>(); for (int i = 0; i < 100; i++) intervals.add(new Interval(i, i + 1));
        Collections.reverse(intervals);
        float start = TimelineClipLayoutResolver.resolveNearestStart(intervals, 1, 2, 0); assertEquals(100, start);
        assertFalse(TimelineClipLayoutResolver.overlapsAny(intervals, start, start + 2));
        assertEquals(3, TimelineClipLayoutResolver.resolveNearestStart(List.of(new Interval(0, 3), new Interval(6, 9)), 4, 3, 0));
    }
    @Test void snappingIncludesAutomationObjectKeysAndCustomPathsWithoutSelfSnapping() {
        var camera = new TimelineTrack("camera", TrackType.CAMERA); model.addTrack(camera);
        model.getSnapSettings().setSnapToGrid(false); model.getSnapSettings().setSnapToClipEdges(false);
        var key = new CameraKeyframe("key", 3); camera.addAutomationKeyframe(key);
        assertEquals(3, TimelineSnapping.snapTime(model, 3.03f, camera, null, 0).snappedTime);
        assertEquals(3.03f, TimelineSnapping.snapTime(model, 3.03f, camera, null, 0, key).snappedTime);
        var clip = new AudioTimelineClip("sound", 0, 9, 0); camera.addClip(clip); clip.addKeyframe(new AudioClipKeyframe(5, TimelineInterpolation.LINEAR, 1));
        assertEquals(5, TimelineSnapping.snapTime(model, 5.03f, camera, null, 0).snappedTime);
        var path = new CustomPathClip("custom", 6, 9); path.addKeyframe(new CameraKeyframe("path key", 7)); camera.addCameraPathClip(path);
        assertEquals(7, TimelineSnapping.snapTime(model, 7.03f, camera, null, 0).snappedTime);
        model.getSnapSettings().setSnapToClipEdges(true);
        assertEquals(6, TimelineSnapping.snapTime(model, 6.03f, camera, null, 0).snappedTime);
        assertEquals(6.03f, TimelineSnapping.snapTime(model, 6.03f, camera, null, 0, path).snappedTime);
    }
    static class CountingEvent extends AudioEvent {
        int calls;
        CountingEvent(float time) { super("count", "", time); }
        @Override public boolean isEnabled() { calls++; return false; }
    }
    @Test void backwardClockJumpDoesNotReplayInteriorEvents() {
        var track = track("loop"); var event = new CountingEvent(2); track.addEvent(event);
        var sampler = new AudioTrackSampler(track); sampler.setPlaying(true); sampler.sample(0); sampler.sample(4.9f); sampler.sample(0);
        assertEquals(1, event.calls);
    }
    @Test void explicitLoopWindowsIncludeHeadAndTailExactlyOnce() {
        var track = track("loop"); var head = new CountingEvent(0); var middle = new CountingEvent(2); var tail = new CountingEvent(5);
        track.addEvent(head); track.addEvent(middle); track.addEvent(tail);
        var sampler = new AudioTrackSampler(track); sampler.setPlaying(true);
        sampler.sampleWindow(0, 4.9f, true); sampler.sampleWindow(4.9f, 5, false); sampler.sampleWindow(0, 0.1f, true);
        assertEquals(2, head.calls); assertEquals(1, middle.calls); assertEquals(1, tail.calls);
    }
    @Test void pausedSeekAndPlayingSeekDoNotBurstSkippedEvents() {
        var track = track("seek"); var event = new CountingEvent(4); track.addEvent(event);
        model.setCurrentTime(1); model.setPlaying(true); var controller = TimelineAudioController.getInstance(); controller.update();
        model.setPlaying(false); model.setCurrentTime(8); controller.update(); model.setPlaying(true); controller.update(); assertEquals(0, event.calls);
        model.setCurrentTime(1); controller.update(); model.setCurrentTime(8); controller.update(); assertEquals(0, event.calls);
    }
    @Test void samplerResumingAfterPauseDoesNotReplayOldWindow() {
        var track = track("seek"); var event = new CountingEvent(4); track.addEvent(event);
        var sampler = new AudioTrackSampler(track); sampler.setPlaying(true); sampler.sample(1);
        sampler.setPlaying(false); sampler.setPlaying(true); sampler.sample(8); assertEquals(0, event.calls);
    }
    @Test void lockedTrackStillPlaysAndHiddenMutedNonSoloTracksStaySilent() {
        var first = track("first"); var second = track("second"); first.setLocked(true);
        assertTrue(TimelineAudioPlaybackPolicy.isAudible(model.getTracks(), first));
        first.setSolo(true); assertFalse(TimelineAudioPlaybackPolicy.isAudible(model.getTracks(), second));
        first.setVisible(false); assertFalse(TimelineAudioPlaybackPolicy.isAudible(model.getTracks(), first));
        assertTrue(TimelineAudioPlaybackPolicy.isAudible(model.getTracks(), second));
        second.setMuted(true); assertFalse(TimelineAudioPlaybackPolicy.isAudible(model.getTracks(), second));
    }
    @Test void cancellingDragRestoresTrackRangeEventsAndScrubbing() throws Exception {
        var first = track("first"); var second = track("second");
        var clip = new AudioTimelineClip("clip", 0, 4, 0); first.addClip(clip);
        var event = new AudioEvent("event", "", 2); first.addEvent(event); link(clip, event);
        var manager = new com.director.ui.timeline.interaction.TimelineInteractionManager();
        var renderer = new com.director.ui.timeline.render.TimelineRenderer(model, model.getViewportState());
        var handler = new com.director.ui.timeline.interaction.TimelineInteractionHandler(model, manager, history, renderer);
        var handle = new com.director.ui.timeline.interaction.TimelineDragHandles.ClipHandle(first, clip);
        manager.startInteraction(com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode.DRAG_CLIP, handle, 0, 0, 0, 4);
        audio.beginClipTimeRangePreview(clip, 0, 4);
        first.removeClip(clip); second.addClip(clip); clip.setTimeRange(5, 9); audio.moveLinkedEventsToTrack(clip, second);
        handler.cancelAllInteractionsSilently();
        assertSame(first, clip.getParentTrack()); assertEquals(0, clip.getStartTime()); assertEquals(4, clip.getEndTime());
        assertSame(event, first.getEvents().getFirst()); assertTrue(second.getEvents().isEmpty());
        assertEquals(-1, audio.getPreviewTimeForEvent(event));
        manager.startInteraction(com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode.DRAG_PLAYHEAD, null, 0, 0, 0, 0);
        model.setScrubbing(true); handler.cancelAllInteractionsSilently(); assertFalse(model.isScrubbing());
    }
    @Test void loopProgressionPreservesOverflowAndTriggersOnlyHeadAndTail() throws Exception {
        var sound = track("loop"); var head = new CountingEvent(0); var middle = new CountingEvent(2); var tail = new CountingEvent(5);
        sound.addEvent(head); sound.addEvent(middle); sound.addEvent(tail);
        model.setDuration(5); model.setOutPoint(5); model.setCurrentTime(4.9f); model.setLoopEnabled(true);
        var orchestrator = PlaybackOrchestrator.get(); orchestrator.bindModel(model); model.setPlaying(true);
        Method advance = PlaybackOrchestrator.class.getDeclaredMethod("advanceTime", float.class); advance.setAccessible(true);
        advance.invoke(orchestrator, 0.2f);
        assertEquals(0.1f, model.getCurrentTime(), 0.0001f);
        assertEquals(1, head.calls); assertEquals(0, middle.calls); assertEquals(1, tail.calls);
    }
    @Test void stoppingAtEndStillSamplesFinalAudioEvent() throws Exception {
        var sound = track("end"); var tail = new CountingEvent(5); sound.addEvent(tail);
        model.setDuration(5); model.setOutPoint(5); model.setCurrentTime(4.9f);
        var orchestrator = PlaybackOrchestrator.get(); orchestrator.bindModel(model); model.setPlaying(true);
        Method advance = PlaybackOrchestrator.class.getDeclaredMethod("advanceTime", float.class); advance.setAccessible(true);
        advance.invoke(orchestrator, 0.2f);
        assertFalse(model.isPlaying()); assertEquals(5, model.getCurrentTime()); assertEquals(1, tail.calls);
    }
    @Test void clipboardCutPreservesLinkedAudioAcrossPasteUndoAndCutUndo() throws Exception {
        var blocks = new TimelineTrack("blocks", TrackType.CONTAINER); model.addTrack(blocks);
        var clip = new TimelineClip("blocks", 0, 4, 0); blocks.addClip(clip);
        var sound = track("sound"); var linked = new AudioTimelineClip("linked", 0, 4, 0); sound.addClip(linked); linkClips(clip, linked);
        linked.addKeyframe(new AudioClipKeyframe(2, TimelineInterpolation.LINEAR, 0.2f));
        var event = new AudioEvent("edited", "", 2); event.setPitch(1.6f); sound.addEvent(event); link(clip, event);
        var clipboard = ClipboardManager.getInstance(); clipboard.cut(clip, blocks, model, history);
        assertTrue(sound.getEvents().isEmpty()); model.setCurrentTime(5); clipboard.pasteToTrack(blocks, model);
        assertEquals(7, sound.getEvents().getFirst().getTime()); assertEquals(1.6f, sound.getEvents().getFirst().getPitch());
        assertEquals(0.2f, audio.getVolumeMultiplierForEvent(sound.getEvents().getFirst()));
        history.undo(); assertTrue(sound.getEvents().isEmpty()); history.undo();
        assertSame(clip, blocks.getClips().getFirst()); assertSame(linked, sound.getClips().getFirst()); assertSame(event, sound.getEvents().getFirst());
    }
    @Test void persistedAudioOnSecondTrackRebuildsAutomationLinks() {
        track("empty first"); var sound = track("second"); var clip = new AudioTimelineClip("clip", 0, 4, 0); sound.addClip(clip);
        clip.addKeyframe(new AudioClipKeyframe(0, TimelineInterpolation.LINEAR, 0.3f)); sound.addEvent(new AudioEvent("event", "", 2));
        var dto = TimelineModelPersistence.toDTO(model, model.getViewportState()); TimelineModelPersistence.fromDTO(dto, model, model.getViewportState());
        var restored = (AudioTrack) model.getTracks().getLast(); var event = restored.getEvents().getFirst();
        assertEquals(0.3f, audio.getVolumeMultiplierForEvent(event));
        var loaded = restored.getClips().getFirst(); loaded.setTimeRange(5, 9); audio.onClipTimeRangeChanged(loaded, 0, 4, 5, 9); assertEquals(7, event.getTime());
    }
    @Test void exportHonorsSoloVisibilityAndAudioClipVolume() throws Exception {
        com.director.core.DirectorServices.initialize();
        var service = com.director.core.DirectorServices.get().containerTimeline(); var previous = service.getTimelineModel(); service.setTimelineModel(model);
        try {
            var first = track("solo"); var second = track("other"); first.setSolo(true); first.setLocked(true);
            var clip = new AudioTimelineClip("clip", 0, 4, 0); first.addClip(clip); clip.addKeyframe(new AudioClipKeyframe(0, TimelineInterpolation.LINEAR, 0.2f));
            var event = new AudioEvent("first", "", 2); first.addEvent(event); second.addEvent(new AudioEvent("second", "", 3)); link(clip, event);
            var exporter = new com.director.export.video.TimelineAudioExporter();
            Method collect = exporter.getClass().getDeclaredMethod("collectEventClips", float.class, float.class); collect.setAccessible(true);
            var events = (List<?>) collect.invoke(exporter, 0f, 4f); assertEquals(1, events.size());
            Field volume = events.getFirst().getClass().getDeclaredField("volume"); volume.setAccessible(true); assertEquals(0.2f, volume.getFloat(events.getFirst()));
            first.setVisible(false); events = (List<?>) collect.invoke(exporter, 0f, 4f); assertEquals(1, events.size());
            Field time = events.getFirst().getClass().getDeclaredField("timeSec"); time.setAccessible(true); assertEquals(3, time.getFloat(events.getFirst()));
        } finally { service.setTimelineModel(previous); }
    }
    @Test void deletingClipUndoRestoresLegacyInterpolation() {
        var track = track("clip"); var clip = new TimelineClip("clip", 0, 4, 0); clip.addKeyframe(2); track.addClip(clip);
        ChangeKeyframeInterpolationCommand.setKeyframeInterpolationDirectly(clip, 2, TimelineInterpolation.STEP);
        history.executeCommand(new DeleteClipCommand(model, clip, track.getClips())); history.undo();
        assertEquals(TimelineInterpolation.STEP, ChangeKeyframeInterpolationCommand.getKeyframeInterpolation(clip, 2));
    }
}
