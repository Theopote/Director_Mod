package com.director.core.animation;

import com.director.timeline.model.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationPersistenceTest {
    @Test void newEffectsAndCustomPivotsSurviveProjectJsonAndClipDuplication() {
        for (var effect : new WholeContainerAnimationConfig.Effect[] {
                WholeContainerAnimationConfig.Effect.ROTATE, WholeContainerAnimationConfig.Effect.TRANSFORM}) {
            var viewport = new TimelineViewportState();
            var model = new TimelineModel(viewport);
            model.setSuppressPersistenceWrites(true);
            var track = new TimelineTrack("blocks", TrackType.CONTAINER);
            var clip = new TimelineClip("assembly", 3, 8, 0xFFFFFFFF);
            clip.getAnimationConfig().setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
            var settings = clip.getAnimationConfig().getWholeContainer();
            settings.setEffect(effect);
            settings.setPivot(WholeContainerAnimationConfig.Pivot.CUSTOM);
            settings.setCustomPivot(-1.25f, 2.5f, 0.75f);
            settings.setRotation(-180, 720, 45);
            settings.setOffset(20, 4, -8);
            settings.setScale(0.25f);
            track.addClip(clip);
            model.addTrack(track);
            var gson = new Gson();
            var dto = gson.fromJson(gson.toJson(TimelineModelPersistence.toDTO(model, viewport)),
                    TimelineModelPersistence.TimelineDTO.class);
            var restored = new TimelineModel(new TimelineViewportState());
            restored.setSuppressPersistenceWrites(true);
            TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
            var loaded = restored.getTracks().getFirst().getClips().getFirst().getAnimationConfig().getWholeContainer();
            assertEquals(effect, loaded.getEffect());
            assertEquals(WholeContainerAnimationConfig.Pivot.CUSTOM, loaded.getPivot());
            assertEquals(-1.25f, loaded.getPivotX());
            assertEquals(2.5f, loaded.getPivotY());
            assertEquals(0.75f, loaded.getPivotZ());
            assertEquals(-180, loaded.getRotationX());
            assertEquals(720, loaded.getRotationY());
            assertEquals(45, loaded.getRotationZ());
            assertEquals(20, loaded.getOffsetX());
            assertEquals(0.25f, loaded.getScale());
            var duplicate = clip.duplicateShallow().getAnimationConfig().getWholeContainer();
            duplicate.setCustomPivot(0, 0, 0);
            duplicate.setRotation(0, 0, 0);
            assertEquals(-1.25f, settings.getPivotX());
            assertEquals(720, settings.getRotationY());
        }
    }

    @Test void projectJsonRoundTripAndLegacyDefaults() {
        var viewport = new TimelineViewportState();
        var model = new TimelineModel(viewport);
        model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("blocks", TrackType.CONTAINER);
        var clip = new TimelineClip("assembly", 3, 8, 0xFFFFFFFF);
        var config = clip.getAnimationConfig();
        config.setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        config.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.FLY);
        config.getWholeContainer().setPivot(WholeContainerAnimationConfig.Pivot.CENTER);
        config.getWholeContainer().setScale(0.2f);
        config.getWholeContainer().setOffset(-30, 4, 12);
        config.setDelay(0.4f);
        config.setDuration(1.5f);
        track.addClip(clip);
        model.addTrack(track);
        var gson = new Gson();
        var dto = gson.fromJson(gson.toJson(TimelineModelPersistence.toDTO(model, viewport)),
                TimelineModelPersistence.TimelineDTO.class);
        var restored = new TimelineModel(new TimelineViewportState());
        restored.setSuppressPersistenceWrites(true);
        TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
        var loaded = restored.getTracks().getFirst().getClips().getFirst().getAnimationConfig();
        assertTrue(loaded.isWholeContainer());
        assertEquals(WholeContainerAnimationConfig.Effect.FLY, loaded.getWholeContainer().getEffect());
        assertEquals(WholeContainerAnimationConfig.Pivot.CENTER, loaded.getWholeContainer().getPivot());
        assertEquals(-30, loaded.getWholeContainer().getOffsetX());
        assertEquals(4, loaded.getWholeContainer().getOffsetY());
        assertEquals(12, loaded.getWholeContainer().getOffsetZ());
        assertEquals(0.2f, loaded.getWholeContainer().getScale());
        assertEquals(0.4f, loaded.getDelay());
        assertEquals(1.5f, loaded.getDuration());
        var duplicate = clip.duplicateShallow();
        duplicate.getAnimationConfig().getWholeContainer().setScale(0.8f);
        assertTrue(duplicate.getAnimationConfig().isWholeContainer());
        assertEquals(0.2f, config.getWholeContainer().getScale());
        dto.tracks.getFirst().clips.getFirst().animationScope = null;
        dto.tracks.getFirst().clips.getFirst().wholeContainerAnimation = null;
        TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
        assertFalse(restored.getTracks().getFirst().getClips().getFirst().getAnimationConfig().isWholeContainer());
    }
}
