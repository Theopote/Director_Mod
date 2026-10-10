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

    @Test void independentChannelsAndOpacitySurviveProjectSaveAndDuplication() {
        var viewport = new TimelineViewportState();
        var model = new TimelineModel(viewport); model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("blocks", TrackType.CONTAINER);
        var clip = new TimelineClip("Fade and rotate", 3, 8, 0);
        var config = clip.getAnimationConfig(); config.setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        var settings = config.getWholeContainer(); settings.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
        settings.setTranslationEnabled(false); settings.setScaleEnabled(false);
        settings.setRotationEnabled(true); settings.setOpacityEnabled(true);
        settings.setOpacity(0.25f); settings.setRotation(0, 720, 0);
        track.addClip(clip); model.addTrack(track);
        var gson = new Gson();
        var dto = gson.fromJson(gson.toJson(TimelineModelPersistence.toDTO(model, viewport)), TimelineModelPersistence.TimelineDTO.class);
        var restored = new TimelineModel(new TimelineViewportState()); restored.setSuppressPersistenceWrites(true);
        TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
        var loaded = restored.getTracks().getFirst().getClips().getFirst().getAnimationConfig();
        assertFalse(loaded.getWholeContainer().isTranslationEnabled()); assertFalse(loaded.getWholeContainer().isScaleEnabled());
        assertTrue(loaded.getWholeContainer().isRotationEnabled()); assertTrue(loaded.getWholeContainer().isOpacityEnabled());
        assertEquals(0.25f, loaded.getWholeContainer().getOpacity());
        assertEquals(WholeContainerAnimation.sample(config, 3, 8, 4, false), WholeContainerAnimation.sample(loaded, 3, 8, 4, false));
        var copy = clip.duplicateShallow().getAnimationConfig().getWholeContainer();
        copy.setOpacity(1); copy.setTranslationEnabled(true); copy.setOpacityEnabled(false);
        assertEquals(0.25f, settings.getOpacity()); assertFalse(settings.isTranslationEnabled()); assertTrue(settings.isOpacityEnabled());
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
        config.getParams().put("enterSoundTiming", "START");
        config.getParams().put("enterSoundOffsetSeconds", -0.2f);
        config.getParams().put("exitSoundTiming", "PRE_LAND");
        config.getParams().put("exitSoundOffsetSeconds", 0.35f);
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
        assertEquals("START", loaded.getParams().get("enterSoundTiming"));
        assertEquals(-0.2f, ((Number) loaded.getParams().get("enterSoundOffsetSeconds")).floatValue());
        assertEquals("PRE_LAND", loaded.getParams().get("exitSoundTiming"));
        assertEquals(0.35f, ((Number) loaded.getParams().get("exitSoundOffsetSeconds")).floatValue());
        assertEquals(4.9f - 1.0f / 60, AnimationSoundCollector.wholeContainerEventTime(
                restored.getTracks().getFirst().getClips().getFirst()), 0.0001f);
        var duplicate = clip.duplicateShallow();
        duplicate.getAnimationConfig().getParams().put("enterSoundOffsetSeconds", 1.0f);
        assertEquals(-0.2f, config.getParams().get("enterSoundOffsetSeconds"));
        duplicate.getAnimationConfig().getWholeContainer().setScale(0.8f);
        assertTrue(duplicate.getAnimationConfig().isWholeContainer());
        assertEquals(0.2f, config.getWholeContainer().getScale());
        dto.tracks.getFirst().clips.getFirst().animationScope = null;
        dto.tracks.getFirst().clips.getFirst().wholeContainerAnimation = null;
        TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
        assertFalse(restored.getTracks().getFirst().getClips().getFirst().getAnimationConfig().isWholeContainer());
    }
}
