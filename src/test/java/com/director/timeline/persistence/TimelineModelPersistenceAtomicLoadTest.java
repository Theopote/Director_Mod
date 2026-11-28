package com.director.timeline.persistence;

import com.director.lighting.PointLight;
import com.director.timeline.audio.TimelineAudioLoadHook;
import com.director.timeline.audio.TimelineClipSoundCollector;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineMarker;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.audio.AudioTrack;
import com.director.timeline.persistence.TimelineModelPersistence.AutomationKeyframeDTO;
import com.director.timeline.persistence.TimelineModelPersistence.ClipDTO;
import com.director.timeline.persistence.TimelineModelPersistence.MarkerDTO;
import com.director.timeline.persistence.TimelineModelPersistence.TimelineDTO;
import com.director.timeline.persistence.TimelineModelPersistence.TrackDTO;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineModelPersistenceAtomicLoadTest {
    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private TimelineModel model;
    private TimelineViewportState viewport;
    private RecordingAudioLoadHook audioHook;

    @BeforeEach
    void setUp() {
        viewport = new TimelineViewportState();
        model = new TimelineModel(viewport, false);
        model.setSuppressPersistenceWrites(true);
        model.setClipSoundCollector(TimelineClipSoundCollector.NOOP);

        audioHook = new RecordingAudioLoadHook();
        audioHook.timelineModel = model;
        model.setAudioLoadHook(audioHook);

        PointLight liveLight = PointLight.create(1, 2, 3);
        model.replacePointLight(liveLight.id(), liveLight);
        TimelineTrack liveTrack = new TimelineTrack("live", TrackType.CONTAINER);
        TimelineClip liveClip = new TimelineClip("live clip", 1, 3, 0xFF112233);
        assertTrue(liveTrack.addClip(liveClip));
        model.addTrack(liveTrack);

        AudioTrack liveAudio = new AudioTrack("live audio");
        liveAudio.addEvent(new AudioEvent("live event", "minecraft:block.stone.place", 2));
        model.addTrack(liveAudio);
        model.addMarker(new TimelineMarker("live marker", 4));

        model.setSelectedClip(liveClip);
        model.setFrameRate(48);
        model.setTimeUnit(1);
        model.setDuration(40);
        model.setLoopEnabled(true);
        model.setPlaybackSpeed(1.5f);
        model.setOutPoint(30);
        model.setInPoint(5);
        model.setCurrentTime(7);
        model.setSnappingEnabled(false);
        model.getSnapSettings().setGridInterval(0.25f);
        model.getSnapSettings().setSnapToGrid(false);
        model.getSnapSettings().setSnapToClipEdges(false);
        model.getSnapSettings().setSnapToKeyframes(false);
        model.getSnapSettings().setPixelThreshold(12);

        viewport.setPixelsPerSecond(123);
        viewport.setScrollX(17);
        viewport.setScrollY(19);
    }

    @AfterEach
    void tearDown() {
        TimelineTrack.setClipSoundCollector(TimelineClipSoundCollector.NOOP);
    }

    @Test
    void missingDtoIsRejectedWithoutChangingLiveModel() {
        assertFailureLeavesLiveModelUnchanged(
                () -> TimelineModelPersistence.fromDTO(null, model, viewport));
    }

    @Test
    void nullClipListInSecondTrackLeavesLiveModelUnchanged() {
        TimelineDTO dto = validDto();
        TrackDTO damaged = track("damaged", TrackType.CONTAINER);
        damaged.clips = null;
        dto.tracks.add(damaged);

        assertFailureLeavesLiveModelUnchanged(
                () -> TimelineModelPersistence.fromDTO(dto, model, viewport));
    }

    @Test
    void nullLabelInSecondTrackLeavesLiveModelUnchanged() {
        TimelineDTO dto = validDto();
        TrackDTO damaged = track("damaged", TrackType.CONTAINER);
        ClipDTO badClip = clip("bad", 1, 3);
        badClip.label = null;
        damaged.clips.add(badClip);
        dto.tracks.add(damaged);

        assertFailureLeavesLiveModelUnchanged(
                () -> TimelineModelPersistence.fromDTO(dto, model, viewport));
    }

    @Test
    void invalidTimeInSecondTrackLeavesLiveModelUnchanged() {
        TimelineDTO dto = validDto();
        TrackDTO damaged = track("damaged", TrackType.CONTAINER);
        damaged.clips.add(clip("bad", 5, 2));
        dto.tracks.add(damaged);

        assertFailureLeavesLiveModelUnchanged(
                () -> TimelineModelPersistence.fromDTO(dto, model, viewport));
    }

    @Test
    void duplicateContainerBindingIsRejectedWithoutChangingLiveModel() {
        TimelineDTO dto = validDto();
        UUID containerId = UUID.randomUUID();
        TrackDTO duplicates = track("duplicates", TrackType.CONTAINER);
        ClipDTO first = clip("first", 1, 3);
        first.boundContainerId = containerId.toString();
        ClipDTO second = clip("second", 4, 6);
        second.boundContainerId = containerId.toString();
        duplicates.clips.add(first);
        duplicates.clips.add(second);
        dto.tracks.add(duplicates);

        assertFailureLeavesLiveModelUnchanged(
                () -> TimelineModelPersistence.fromDTO(dto, model, viewport));
    }

    @Test
    void duplicateContainerBindingAcrossTracksIsRejectedWithoutChangingLiveModel() {
        TimelineDTO dto = validDto();
        UUID containerId = UUID.randomUUID();
        TrackDTO firstTrack = track("first", TrackType.CONTAINER);
        ClipDTO first = clip("first", 1, 3);
        first.boundContainerId = containerId.toString();
        firstTrack.clips.add(first);
        dto.tracks.add(firstTrack);

        TrackDTO secondTrack = track("second", TrackType.CONTAINER);
        ClipDTO second = clip("second", 4, 6);
        second.boundContainerId = containerId.toString();
        secondTrack.clips.add(second);
        dto.tracks.add(secondTrack);

        assertFailureLeavesLiveModelUnchanged(
                () -> TimelineModelPersistence.fromDTO(dto, model, viewport));
    }

    @Test
    void trackConstraintRejectionLeavesLiveModelUnchanged() {
        TimelineDTO dto = validDto();
        TrackDTO incompatible = track("camera", TrackType.CAMERA);
        ClipDTO boundClip = clip("bound", 1, 3);
        boundClip.boundContainerId = UUID.randomUUID().toString();
        incompatible.clips.add(boundClip);
        dto.tracks.add(incompatible);

        assertFailureLeavesLiveModelUnchanged(
                () -> TimelineModelPersistence.fromDTO(dto, model, viewport));
    }

    @Test
    void malformedJsonFileIsReportedAndLeavesLiveModelUnchanged() throws Exception {
        Path file = tempDir.resolve("timeline_model.json");
        Files.writeString(file, "{not-json");
        Snapshot before = snapshot();

        assertThrows(JsonSyntaxException.class,
                () -> TimelineModelPersistence.loadFromFile(model, viewport, file));

        assertSnapshotEquals(before);
    }

    @Test
    void invalidDtoFileIsReportedAndLeavesLiveModelUnchanged() throws Exception {
        TimelineDTO dto = validDto();
        TrackDTO damaged = track("damaged", TrackType.CONTAINER);
        ClipDTO badClip = clip("bad", 1, 3);
        badClip.label = null;
        damaged.clips.add(badClip);
        dto.tracks.add(damaged);
        Path file = tempDir.resolve("timeline_model.json");
        Files.writeString(file, GSON.toJson(dto));

        assertFailureLeavesLiveModelUnchanged(
                () -> TimelineModelPersistence.loadFromFile(model, viewport, file));
    }

    @Test
    void successfulFileLoadKeepsModelIdentityAndRebindsAudioWhileSuppressed() throws Exception {
        TimelineModel identity = model;
        TimelineViewportState viewportIdentity = viewport;
        UUID containerId = UUID.randomUUID();
        TimelineDTO dto = validDto();
        dto.tracks.getFirst().clips.getFirst().boundContainerId = containerId.toString();
        // live/global 中同容器占用不能污染 staged 模型的局部唯一性校验。
        model.getTracks().getFirst().getClips().getFirst().setBoundContainerId(containerId);

        PointLight loadedLight = PointLight.create(10, 20, 30);
        dto.pointLights.add(loadedLight);
        TrackDTO lightTrack = track("loaded light", TrackType.LIGHT);
        ClipDTO lightClip = clip("light", 2, 8);
        lightClip.lightId = loadedLight.id().toString();
        lightTrack.clips.add(lightClip);
        dto.tracks.add(lightTrack);

        TrackDTO audioTrack = track("loaded audio", TrackType.AUDIO);
        audioTrack.clips.add(clip("loaded sound", 2, 8));
        AutomationKeyframeDTO audioEvent = new AutomationKeyframeDTO();
        audioEvent.type = "AudioEvent";
        audioEvent.audioLabel = "loaded event";
        audioEvent.audioSoundId = "minecraft:block.stone.place";
        audioEvent.time = 3;
        audioTrack.automationKeyframes.add(audioEvent);
        dto.tracks.add(audioTrack);

        Path file = tempDir.resolve("timeline_model.json");
        Files.writeString(file, GSON.toJson(dto));

        assertNotNull(TimelineModelPersistence.loadFromFile(model, viewport, file));

        assertSame(identity, model);
        assertSame(viewportIdentity, model.getViewportState());
        assertSame(identity, audioHook.timelineModel);
        assertTrue(audioHook.suppressedDuringRebuild);
        assertFalse(audioHook.suppressAutoCreate);
        assertEquals(3, model.getTrackCount());
        assertEquals(List.of(loadedLight), model.getPointLights());
        assertEquals("loaded marker", model.getMarkers().getFirst().getName());
        assertEquals(240f, viewport.getPixelsPerSecond());
        assertEquals(44f, viewport.getScrollX());
        assertEquals(19f, viewport.getScrollY());
        assertTrue(model.getSelectedItems().isEmpty());

        TimelineTrack loadedTrack = model.getTracks().getFirst();
        assertEquals(containerId, loadedTrack.getClips().getFirst().getBoundContainerId());
        TimelineClip duplicate = new TimelineClip("duplicate", 10, 12, 0);
        duplicate.setBoundContainerId(containerId);
        assertFalse(loadedTrack.addClip(duplicate), "committed tracks must use the live model's uniqueness scope");
    }

    private void assertFailureLeavesLiveModelUnchanged(Executable load) {
        Snapshot before = snapshot();
        assertThrows(RuntimeException.class, load);
        assertSnapshotEquals(before);
    }

    private void assertSnapshotEquals(Snapshot expected) {
        Snapshot actual = snapshot();
        assertEquals(expected.persistedJson(), actual.persistedJson());
        assertEquals(expected.tracks(), actual.tracks());
        assertEquals(expected.selection(), actual.selection());
        assertSame(expected.selectedClip(), actual.selectedClip());
        assertEquals(expected.pixelsPerSecond(), actual.pixelsPerSecond());
        assertEquals(expected.scrollX(), actual.scrollX());
        assertEquals(expected.scrollY(), actual.scrollY());
        assertEquals(expected.currentTime(), actual.currentTime());
        assertEquals(expected.suppressPersistenceWrites(), actual.suppressPersistenceWrites());
        assertEquals(expected.audioSuppressed(), actual.audioSuppressed());
        assertSame(expected.audioModel(), actual.audioModel());
        assertEquals(expected.audioRebuildCount(), actual.audioRebuildCount());
        assertEquals(expected.actorEditContextRevision(), actual.actorEditContextRevision());
        assertEquals(expected.audioSeekRevision(), actual.audioSeekRevision());
    }

    private Snapshot snapshot() {
        TimelineDTO dto = TimelineModelPersistence.toDTO(model, viewport);
        dto.timestamp = 0;
        dto.worldFingerprint = null;
        return new Snapshot(
                GSON.toJson(dto),
                List.copyOf(model.getTracks()),
                Set.copyOf(model.getSelectedItems()),
                model.getSelectedClip(),
                viewport.getPixelsPerSecond(),
                viewport.getScrollX(),
                viewport.getScrollY(),
                model.getCurrentTime(),
                model.isSuppressPersistenceWrites(),
                audioHook.suppressAutoCreate,
                audioHook.timelineModel,
                audioHook.rebuildCount,
                model.getActorEditContextRevision(),
                model.getAudioSeekRevision()
        );
    }

    private static TimelineDTO validDto() {
        TimelineDTO dto = new TimelineDTO();
        dto.schemaVersion = TimelineModelPersistence.CURRENT_SCHEMA_VERSION;
        dto.pixelsPerSecond = 240;
        dto.scrollX = 44;
        dto.snapEnabled = true;
        dto.snapInterval = 0.5f;
        dto.frameRate = 60;
        dto.timeUnit = 2;
        dto.duration = 50f;
        dto.loopEnabled = false;
        dto.playbackSpeed = 0.75f;
        dto.inPoint = 2f;
        dto.outPoint = 45f;

        MarkerDTO marker = new MarkerDTO();
        marker.id = UUID.randomUUID().toString();
        marker.name = "loaded marker";
        marker.time = 6;
        dto.markers.add(marker);

        TrackDTO container = track("loaded", TrackType.CONTAINER);
        container.clips.add(clip("loaded clip", 1, 6));
        dto.tracks.add(container);
        return dto;
    }

    private static TrackDTO track(String name, TrackType type) {
        TrackDTO track = new TrackDTO();
        track.name = name;
        track.typeName = type.name();
        return track;
    }

    private static ClipDTO clip(String label, float start, float end) {
        ClipDTO clip = new ClipDTO();
        clip.label = label;
        clip.startTime = start;
        clip.endTime = end;
        return clip;
    }

    private record Snapshot(
            String persistedJson,
            List<TimelineTrack> tracks,
            Set<Object> selection,
            TimelineClip selectedClip,
            float pixelsPerSecond,
            float scrollX,
            float scrollY,
            float currentTime,
            boolean suppressPersistenceWrites,
            boolean audioSuppressed,
            TimelineModel audioModel,
            int audioRebuildCount,
            long actorEditContextRevision,
            long audioSeekRevision
    ) {
    }

    private static final class RecordingAudioLoadHook implements TimelineAudioLoadHook {
        private boolean suppressAutoCreate;
        private TimelineModel timelineModel;
        private int rebuildCount;
        private boolean suppressedDuringRebuild;

        @Override
        public boolean isSuppressAutoCreate() {
            return suppressAutoCreate;
        }

        @Override
        public void setSuppressAutoCreate(boolean suppress) {
            suppressAutoCreate = suppress;
        }

        @Override
        public void setTimelineModel(TimelineModel model) {
            timelineModel = model;
            rebuildCount++;
            suppressedDuringRebuild = suppressAutoCreate;
        }
    }
}
