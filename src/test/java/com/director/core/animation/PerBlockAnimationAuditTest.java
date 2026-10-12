package com.director.core.animation;

import com.director.core.animation.effect.*;
import com.director.core.animation.sequence.*;
import com.director.core.animation.sequence.evaluator.FloodSeedMode;
import com.director.core.container.AnimationMode;
import com.director.timeline.model.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.google.gson.Gson;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PerBlockAnimationAuditTest {
    private ClipAnimationConfig roundTrip(ClipAnimationConfig config) {
        var viewport = new TimelineViewportState();
        var model = new TimelineModel(viewport); model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("blocks", TrackType.CONTAINER);
        var clip = new TimelineClip("animation", 10, 11, 0);
        clip.setAnimationConfig(config); track.addClip(clip); model.addTrack(track);
        var gson = new Gson();
        var dto = gson.fromJson(gson.toJson(TimelineModelPersistence.toDTO(model, viewport)),
                TimelineModelPersistence.TimelineDTO.class);
        var restored = new TimelineModel(new TimelineViewportState()); restored.setSuppressPersistenceWrites(true);
        TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
        return restored.getTracks().getFirst().getClips().getFirst().getAnimationConfig();
    }

    @Test void scheduleAndExitEffectsSurviveProjectSaveAndLoad() {
        for (var concurrency : ConcurrencyMode.values()) for (var mapping : TimeMappingStrategy.values()) {
            var config = ClipAnimationConfig.defaultConfig();
            config.setMode(AnimationMode.GRAVITY_FALL); config.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
            config.setDuration(0.0125f); config.setDelay(1.25f);
            var params = config.getSequenceParameters();
            params.setConcurrencyMode(concurrency); params.setTimeMappingStrategy(mapping);
            params.setBatchSize(7); params.setBatchInterval(0.25f); params.setLayerStep(2.5f);
            params.setFloodSeedMode(FloodSeedMode.GLOBAL_RANDOM); params.setGlobalDelay(3.5f);
            params.setPerBlockDuration(0.125f);
            params.setRasterScanAxis("V"); params.setRasterRowStep(2); params.setRasterZigZag(false);
            var effect = new EffectInstance(EffectType.DUST_BURST);
            effect.setPhase(EffectInstance.EffectPhase.EXIT); effect.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
            effect.setStartOffset(0.75f); effect.setDuration(0.2f); effect.getParams().put("intensity", 2.5f);
            config.addEffect(effect);
            var loaded = roundTrip(config); var restored = loaded.getSequenceParameters();
            assertEquals(config.getPhase(), loaded.getPhase()); assertEquals(config.getMode(), loaded.getMode());
            assertEquals(config.getDuration(), loaded.getDuration()); assertEquals(config.getDelay(), loaded.getDelay());
            assertEquals(params.fingerprint(), restored.fingerprint());
            assertEquals(1, loaded.getEffects().size());
            var loadedEffect = loaded.getEffects().getFirst();
            assertEquals(effect.getType(), loadedEffect.getType()); assertEquals(effect.getPhase(), loadedEffect.getPhase());
            assertEquals(effect.getTriggerMode(), loadedEffect.getTriggerMode());
            assertEquals(effect.getStartOffset(), loadedEffect.getStartOffset()); assertEquals(effect.getDuration(), loadedEffect.getDuration());
            assertEquals(2.5f, ((Number) loadedEffect.getParams().get("intensity")).floatValue());
        }
    }

    @Test void gravityExitStartsAtRestThenFallsAndFades() {
        var config = ClipAnimationConfig.defaultConfig(); config.setMode(AnimationMode.GRAVITY_FALL);
        config.setPhase(ClipAnimationConfig.AnimationPhase.EXIT); config.setDuration(2);
        var entry = new BlockSequenceEntry(BlockPos.ORIGIN, 0, 0, 0);
        var start = BlockAnimationEngine.samplePreview(BlockPos.ORIGIN, null, config, entry, 0, 1, 0, 42);
        var middle = BlockAnimationEngine.samplePreview(BlockPos.ORIGIN, null, config, entry, 0, 1, 1, 42);
        var late = BlockAnimationEngine.samplePreview(BlockPos.ORIGIN, null, config, entry, 0, 1, 1.75f, 42);
        assertEquals(1, start.getAlpha()); assertTrue(start.getTransform().peek().getPositionMatrix().equals(new org.joml.Matrix4f(), 0.00001f));
        assertEquals(0.5f, middle.getAlpha(), 0.00001f);
        assertTrue(late.getAlpha() < middle.getAlpha());
        assertTrue(middle.getTransform().peek().getPositionMatrix().m31() < 0);
        assertFalse(BlockAnimationEngine.samplePreview(BlockPos.ORIGIN, null, config, entry, 0, 1, 2, 42).isVisible());
    }

    @Test void legacyRenderContextUsesIndependentWindowsAndIncludesTail() {
        var clip = new TimelineClip("blocks", 10, 11, 0); var config = clip.getAnimationConfig();
        config.setMode(AnimationMode.FADE_IN); config.setDuration(2); config.setDelay(0.25f);
        var entry = new BlockSequenceEntry(BlockPos.ORIGIN, 0.75f, 0, 0);
        assertNull(BlockAnimationContext.build(null, BlockPos.ORIGIN, clip, config, entry, 10.5));
        var context = BlockAnimationContext.build(null, BlockPos.ORIGIN, clip, config, entry, 12);
        assertNotNull(context); assertEquals(0.5f, context.getAnimProgressRaw());
        assertEquals(BlockAnimationEngine.samplePreview(BlockPos.ORIGIN, null, config, entry, 10, 11, 12, 42).getRawProgress(),
                context.getAnimProgressRaw());
        config.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
        assertEquals(0, BlockAnimationContext.build(null, BlockPos.ORIGIN, clip, config, entry, 10.5).getAnimProgressRaw());
    }

    @Test void suppressedDiskWritesStillInvalidateTheAnimationTail() {
        var model = new TimelineModel(new TimelineViewportState()); model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("blocks", TrackType.CONTAINER);
        var clip = new TimelineClip("blocks", 0, 1, 0);
        track.addClip(clip); model.addTrack(track); clip.setBoundContainerId(java.util.UUID.randomUUID());
        var config = clip.getAnimationConfig(); config.setMode(AnimationMode.FADE_IN); config.setDuration(2);
        model.setDuration(0.1f); assertEquals(3, model.getDuration());
        config.setDuration(10); model.markDirty(); assertEquals(11, model.getDuration());
    }

    @Test void fadeSettingsAndOcclusionSurviveProjectJson() {
        var viewport = new TimelineViewportState(); var model = new TimelineModel(viewport);
        model.setSuppressPersistenceWrites(true); var track = new TimelineTrack("blocks", TrackType.CONTAINER);
        var clip = new TimelineClip("fade", 0, 1, 0); clip.setPostAnimationFadeStart(3); clip.setPostAnimationFadeEnd(7);
        clip.getAnimationConfig().setOcclusionTransparencyEnabled(false); track.addClip(clip); model.addTrack(track);
        var gson = new Gson(); var dto = gson.fromJson(gson.toJson(TimelineModelPersistence.toDTO(model, viewport)),
                TimelineModelPersistence.TimelineDTO.class);
        var restored = new TimelineModel(new TimelineViewportState()); restored.setSuppressPersistenceWrites(true);
        TimelineModelPersistence.fromDTO(dto, restored, new TimelineViewportState());
        var loaded = restored.getTracks().getFirst().getClips().getFirst();
        assertEquals(3, loaded.getPostAnimationFadeStart()); assertEquals(7, loaded.getPostAnimationFadeEnd());
        assertFalse(loaded.getAnimationConfig().isOcclusionTransparencyEnabled());
    }

    @Test void zeroScaleEndpointsKeepGeometryAndNormalsInvertible() {
        for (var mode : new AnimationMode[]{AnimationMode.GROW_UP, AnimationMode.EXPAND, AnimationMode.POP_IN}) {
            var config = ClipAnimationConfig.defaultConfig(); config.setMode(mode);
            var entry = new BlockSequenceEntry(BlockPos.ORIGIN, 0, 0, 0);
            var snapshot = BlockAnimationEngine.samplePreview(BlockPos.ORIGIN, null, config, entry, 0, 1, 0, 42);
            var matrix = snapshot.getTransform().peek();
            assertTrue(matrix.getPositionMatrix().determinant() != 0, mode.name());
            for (float value : matrix.getNormalMatrix().get(new float[9])) assertTrue(Float.isFinite(value), mode.name());
            for (float value : new org.joml.Matrix4f(matrix.getPositionMatrix()).invert().get(new float[16]))
                assertTrue(Float.isFinite(value), mode.name());
        }
    }

    @Test void skippedWholeActionPrimesBothStartAndLateEffectThresholds() throws Exception {
        BlockAnimationEngine.resetEffects();
        var config = ClipAnimationConfig.defaultConfig(); var effect = new EffectInstance(EffectType.DUST_BURST);
        effect.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT); config.addEffect(effect);
        BlockAnimationEngine.primeEffectsBeforeStart(BlockPos.ORIGIN, config, 1);
        var gate = Class.forName(BlockAnimationEngine.class.getName() + "$EffectRuntimeGate");
        var statesField = gate.getDeclaredField("STATES"); statesField.setAccessible(true);
        var states = (Map<?, ?>) statesField.get(null); assertEquals(1, states.size());
        var state = states.values().iterator().next();
        var progress = state.getClass().getDeclaredField("lastTRaw"); progress.setAccessible(true);
        var initialized = state.getClass().getDeclaredField("initialized"); initialized.setAccessible(true);
        assertTrue(initialized.getBoolean(state));
        assertTrue(PerBlockEffectTrigger.crossed(progress.getFloat(state), 1, 0));
        assertTrue(PerBlockEffectTrigger.crossed(progress.getFloat(state), 1, 0.8f));
        BlockAnimationEngine.resetEffects();
    }

    @Test void resettingEffectsRearmsGatesAndTimelineCleanupClock() throws Exception {
        var gate = Class.forName(BlockAnimationEngine.class.getName() + "$EffectRuntimeGate");
        var get = gate.getDeclaredMethod("get", long.class, java.util.UUID.class, float.class); get.setAccessible(true);
        get.invoke(null, 0L, java.util.UUID.randomUUID(), 100f);
        var states = gate.getDeclaredField("STATES"); states.setAccessible(true);
        assertFalse(((Map<?, ?>) states.get(null)).isEmpty());
        BlockAnimationEngine.resetEffects();
        assertTrue(((Map<?, ?>) states.get(null)).isEmpty());
        var cleanup = gate.getDeclaredField("lastCleanupNow"); cleanup.setAccessible(true);
        assertEquals(Float.NEGATIVE_INFINITY, cleanup.getFloat(null));
    }
}
