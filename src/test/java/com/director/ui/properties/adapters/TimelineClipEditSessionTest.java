package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.WholeContainerAnimationConfig;
import com.director.core.animation.WholeContainerAnimationPresets;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TimelineClipEditSessionTest {
    TimelineModel model;
    TimelineTrack track;
    TimelineClip clip;
    TimelineCommandHistory history;
    TimelineClipEditSession edits;
    AtomicInteger refreshes;

    @BeforeEach void setup() {
        model = new TimelineModel(new TimelineViewportState());
        track = new TimelineTrack("Blocks", TrackType.CONTAINER);
        model.addTrack(track);
        clip = new TimelineClip("Structure", 10, 20, 0);
        clip.getAnimationConfigForEditing().setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        track.addClip(clip);
        history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        refreshes = new AtomicInteger();
        edits = new TimelineClipEditSession(c -> {
            assertSame(clip, c); refreshes.incrementAndGet();
        });
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); }
    void change(Runnable edit, boolean active) {
        edits.beginFrame(model, clip);
        edit.run();
        edits.endFrame(true, active, "Edit whole animation");
    }
    @Test void continuousDragCommitsOnceOnReleaseAndRefreshesOnUndoRedo() {
        for (int i = 1; i <= 80; i++) {
            final int angle = i * 9;
            change(() -> clip.getAnimationConfigForEditing().getWholeContainer().setRotation(0, angle, 0), true);
        }
        assertEquals(0, history.getUndoSize());
        assertEquals(0, refreshes.get());
        edits.beginFrame(model, clip);
        edits.endFrame(false, false, "Edit whole animation");
        assertEquals(1, history.getUndoSize());
        assertEquals(1, refreshes.get());
        history.undo();
        assertEquals(180, clip.getAnimationConfig().getWholeContainer().getRotationY());
        history.redo();
        assertEquals(720, clip.getAnimationConfig().getWholeContainer().getRotationY());
        assertEquals(3, refreshes.get());
    }
    @Test void undoRestoresCombinedTransformAndCustomPivotWithoutTouchingPerBlockSettings() {
        var config = clip.getAnimationConfigForEditing();
        config.setParam("legacySetting", 42);
        config.getSequenceParameters().setGlobalDelay(3);
        change(() -> {
            var c = config.getWholeContainer();
            c.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
            c.setPivot(WholeContainerAnimationConfig.Pivot.CUSTOM);
            c.setCustomPivot(-2, 0.5f, 3); c.setRotation(-720, 1080, 360);
            c.setOffset(12, 4, -8); c.setScale(0.25f);
            config.setDelay(1); config.setDuration(4); config.setEasing("EASE_IN_CUBIC");
        }, false);
        history.undo();
        assertSame(config, clip.getAnimationConfigForEditing());
        assertEquals(WholeContainerAnimationConfig.Effect.SCALE, config.getWholeContainer().getEffect());
        assertEquals(0, config.getWholeContainer().getPivotX());
        assertEquals(2, config.getDuration());
        assertEquals(0, config.getDelay());
        assertEquals("LINEAR", config.getEasing());
        history.redo();
        assertEquals(1080, config.getWholeContainer().getRotationY());
        assertEquals(-2, config.getWholeContainer().getPivotX());
        assertEquals(0.25f, config.getWholeContainer().getScale());
        assertEquals(4, config.getDuration());
        assertEquals(42, config.getParams().get("legacySetting"));
        assertEquals(3, config.getSequenceParameters().getGlobalDelay());
    }
    @Test void scopeSwitchesKeepSettingsAndAreUndoableInBothDirections() {
        clip.getAnimationConfigForEditing().setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK);
        change(() -> clip.getAnimationConfigForEditing().setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER), false);
        history.undo(); assertFalse(clip.getAnimationConfig().isWholeContainer());
        history.redo(); assertTrue(clip.getAnimationConfig().isWholeContainer());
        change(() -> clip.getAnimationConfigForEditing().setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK), false);
        history.undo(); assertTrue(clip.getAnimationConfig().isWholeContainer());
        assertEquals(1, history.getUndoSize());
        assertEquals(1, history.getRedoSize());
        assertEquals(180, clip.getAnimationConfig().getWholeContainer().getRotationY());
    }
    @Test void applyingPresetAndResettingEachHaveOneUndoStep() {
        var config = clip.getAnimationConfigForEditing();
        config.getWholeContainer().setPivot(WholeContainerAnimationConfig.Pivot.CUSTOM);
        config.getWholeContainer().setCustomPivot(1, 2, 3);
        change(() -> WholeContainerAnimationPresets.apply(config, WholeContainerAnimationPresets.Preset.SPIN_FLY, null), false);
        assertEquals(1, history.getUndoSize());
        assertEquals(720, config.getWholeContainer().getRotationY());
        history.undo();
        assertEquals(WholeContainerAnimationConfig.Pivot.CUSTOM, config.getWholeContainer().getPivot());
        assertEquals(1, config.getWholeContainer().getPivotX());
        history.redo(); assertEquals(720, config.getWholeContainer().getRotationY());
        change(() -> WholeContainerAnimationPresets.resetTransform(config), false);
        assertEquals(2, history.getUndoSize());
        assertEquals(180, config.getWholeContainer().getRotationY());
        history.undo(); assertEquals(720, config.getWholeContainer().getRotationY());
        history.redo(); assertEquals(180, config.getWholeContainer().getRotationY());
        assertEquals(2, config.getDuration()); assertEquals(0, config.getDelay());
    }

    @Test void channelTogglesAndOpacityValuesUndoAndRedoAsOneGesture() {
        var s = clip.getAnimationConfigForEditing().getWholeContainer(); s.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
        change(() -> {
            s.setTranslationEnabled(false); s.setRotationEnabled(false); s.setScaleEnabled(false);
            s.setOpacityEnabled(true); s.setOpacity(0.25f);
        }, false);
        assertEquals(1, history.getUndoSize());
        history.undo();
        assertTrue(s.isTranslationEnabled()); assertTrue(s.isRotationEnabled()); assertTrue(s.isScaleEnabled());
        assertFalse(s.isOpacityEnabled()); assertEquals(0, s.getOpacity());
        history.redo();
        assertFalse(s.isTranslationEnabled()); assertFalse(s.isRotationEnabled()); assertFalse(s.isScaleEnabled());
        assertTrue(s.isOpacityEnabled()); assertEquals(0.25f, s.getOpacity());
    }

    @Test void noOpAndDragReturningToOriginalDoNotCreateHistoryOrSounds() {
        edits.beginFrame(model, clip); edits.endFrame(false, false, "Edit");
        change(() -> clip.getAnimationConfigForEditing().getWholeContainer().setRotation(0, 720, 0), true);
        change(() -> clip.getAnimationConfigForEditing().getWholeContainer().setRotation(0, 180, 0), false);
        assertEquals(0, history.getUndoSize()); assertEquals(0, refreshes.get());
    }
    @Test void collapsedSectionFlushesThePendingGesture() {
        change(() -> clip.getAnimationConfigForEditing().setDelay(1), true);
        edits.finishIfIdle(false);
        assertEquals(1, history.getUndoSize());
        history.undo(); assertEquals(0, clip.getAnimationConfig().getDelay());
        history.redo(); assertEquals(1, clip.getAnimationConfig().getDelay());
    }
    @Test void switchingClipsCommitsToOriginalTarget() {
        change(() -> clip.getAnimationConfigForEditing().getWholeContainer().setScale(0.5f), true);
        var other = new TimelineClip("Other", 20, 30, 0); track.addClip(other);
        edits.beginFrame(model, other);
        assertEquals(1, history.getUndoSize());
        history.undo(); assertEquals(0, clip.getAnimationConfig().getWholeContainer().getScale());
        assertEquals(0, other.getAnimationConfig().getWholeContainer().getScale());
    }
    @Test void removedClipDropsPendingEdit() {
        change(() -> clip.getAnimationConfigForEditing().setDuration(1), true);
        track.removeClip(clip); edits.finishIfIdle(false);
        assertEquals(0, history.getUndoSize()); assertEquals(0, refreshes.get());
    }
    @Test void changingProjectsDoesNotPutOldGestureIntoNewHistory() {
        change(() -> clip.getAnimationConfigForEditing().setDuration(1), true);
        edits.beginFrame(new TimelineModel(new TimelineViewportState()), null);
        edits.finishIfIdle(false);
        assertEquals(0, history.getUndoSize()); assertEquals(0, refreshes.get());
    }
    @Test void independentConsecutiveDragsProduceIndependentUndoSteps() {
        change(() -> clip.getAnimationConfigForEditing().getWholeContainer().setRotation(0, 720, 0), false);
        change(() -> clip.getAnimationConfigForEditing().getWholeContainer().setScale(0.5f), false);
        assertEquals(2, history.getUndoSize());
        history.undo();
        assertEquals(0, clip.getAnimationConfig().getWholeContainer().getScale());
        assertEquals(720, clip.getAnimationConfig().getWholeContainer().getRotationY());
        history.undo();
        assertEquals(180, clip.getAnimationConfig().getWholeContainer().getRotationY());
        history.redo(); history.redo();
        assertEquals(0.5f, clip.getAnimationConfig().getWholeContainer().getScale());
        assertEquals(720, clip.getAnimationConfig().getWholeContainer().getRotationY());
    }

    @Test void durationUndoAndRedoKeepThePreviewAtTheSamePhase() {
        model.setCurrentTime(10.5f);
        change(() -> {
            clip.getAnimationConfigForEditing().setDuration(1);
            model.setCurrentTime(10.25f); // live UI preview adjustment
        }, false);
        history.undo(); assertEquals(10.5f, model.getCurrentTime());
        history.redo(); assertEquals(10.25f, model.getCurrentTime());
    }
    @Test void perBlockEditsAlsoEnterClipHistory() {
        clip.getAnimationConfigForEditing().setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK);
        change(() -> clip.getAnimationConfigForEditing().setDuration(1), false);
        assertEquals(1, history.getUndoSize()); assertEquals(1, refreshes.get());
        history.undo(); assertEquals(2, clip.getAnimationConfig().getDuration());
        history.redo(); assertEquals(1, clip.getAnimationConfig().getDuration());
    }
    @Test void motionModifierAndParametersUndoAndRedoTogether() {
        var s = clip.getAnimationConfigForEditing().getWholeContainer();
        change(() -> {
            s.setMotion(WholeContainerAnimationConfig.Motion.BOUNCE);
            s.setMotionStrength(0.6f); s.setMotionCycles(5); s.setBounceHeight(3);
        }, false);
        assertEquals(1, history.getUndoSize());
        history.undo();
        assertEquals(WholeContainerAnimationConfig.Motion.NONE, s.getMotion());
        assertEquals(1, s.getMotionStrength()); assertEquals(3, s.getMotionCycles()); assertEquals(1, s.getBounceHeight());
        history.redo();
        assertEquals(WholeContainerAnimationConfig.Motion.BOUNCE, s.getMotion());
        assertEquals(0.6f, s.getMotionStrength()); assertEquals(5, s.getMotionCycles()); assertEquals(3, s.getBounceHeight());
    }
    @Test void dustSettingsUndoAndRedoTogether() {
        var s = clip.getAnimationConfigForEditing().getWholeContainer();
        change(() -> {
            s.setDustEnabled(true); s.setDustIntensity(2); s.setDustSpread(3);
        }, false);
        history.undo();
        assertFalse(s.isDustEnabled()); assertEquals(1, s.getDustIntensity()); assertEquals(0.6f, s.getDustSpread());
        history.redo();
        assertTrue(s.isDustEnabled()); assertEquals(2, s.getDustIntensity()); assertEquals(3, s.getDustSpread());
    }
    @Test void visualPresetSelectionUndoAndRedoRestoresPresetAndParameters() {
        var s = clip.getAnimationConfigForEditing().getWholeContainer();
        change(() -> s.setDustPreset(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST), false);
        history.undo();
        assertEquals(WholeContainerAnimationConfig.DustPreset.NONE, s.getDustPreset());
        history.redo();
        assertEquals(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST, s.getDustPreset());
        assertEquals(2.5f, s.getDustIntensity()); assertEquals(1.2f, s.getDustSpread());
        change(() -> s.setDustPreset(WholeContainerAnimationConfig.DustPreset.NONE), false);
        history.undo();
        assertEquals(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST, s.getDustPreset());
        history.redo();
        assertFalse(s.isDustEnabled());
    }
    @Test void applyingMotionPresetUndoesTogetherAndPreservesVisualSelection() {
        var config = clip.getAnimationConfigForEditing();
        config.getWholeContainer().setDustPreset(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST);
        change(() -> com.director.core.animation.WholeContainerAnimationPresets.apply(config,
                com.director.core.animation.WholeContainerAnimationPresets.Preset.BOUNCE_DROP, null), false);
        assertEquals(1, history.getUndoSize());
        history.undo();
        assertEquals(WholeContainerAnimationConfig.Motion.NONE, config.getWholeContainer().getMotion());
        assertEquals(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST, config.getWholeContainer().getDustPreset());
        history.redo();
        assertEquals(WholeContainerAnimationConfig.Motion.BOUNCE, config.getWholeContainer().getMotion());
        assertEquals(WholeContainerAnimationConfig.Effect.FLY, config.getWholeContainer().getEffect());
        assertEquals(WholeContainerAnimationConfig.DustPreset.IMPACT_DUST, config.getWholeContainer().getDustPreset());
    }
    @Test void shockwaveSelectionAndTweaksUndoAndRedoWithoutChangingMotion() {
        var config = clip.getAnimationConfigForEditing();
        config.getWholeContainer().setMotion(WholeContainerAnimationConfig.Motion.ELASTIC);
        change(() -> {
            config.getWholeContainer().setDustPreset(WholeContainerAnimationConfig.DustPreset.SHOCKWAVE);
            config.getWholeContainer().setDustSpread(2);
        }, false);
        history.undo();
        assertEquals(WholeContainerAnimationConfig.DustPreset.NONE, config.getWholeContainer().getDustPreset());
        assertEquals(WholeContainerAnimationConfig.Motion.ELASTIC, config.getWholeContainer().getMotion());
        history.redo();
        assertEquals(WholeContainerAnimationConfig.DustPreset.SHOCKWAVE, config.getWholeContainer().getDustPreset());
        assertEquals(2, config.getWholeContainer().getDustSpread());
    }
    @Test void waveCountAndDecayUndoAndRedoTogether() {
        var s = clip.getAnimationConfigForEditing().getWholeContainer();
        s.setDustPreset(WholeContainerAnimationConfig.DustPreset.SHOCKWAVE);
        change(() -> { s.setShockwaveCount(5); s.setShockwaveDecay(0.6f); }, false);
        history.undo();
        assertEquals(3, s.getShockwaveCount()); assertEquals(0.35f, s.getShockwaveDecay());
        history.redo();
        assertEquals(5, s.getShockwaveCount()); assertEquals(0.6f, s.getShockwaveDecay());
    }
    @Test void arcPathAndHeightUndoAndRedoTogether() {
        var s = clip.getAnimationConfigForEditing().getWholeContainer();
        change(() -> {
            s.setFlightPath(WholeContainerAnimationConfig.FlightPath.ARC);
            s.setArcHeight(-5);
        }, false);
        history.undo();
        assertEquals(WholeContainerAnimationConfig.FlightPath.STRAIGHT, s.getFlightPath());
        assertEquals(3, s.getArcHeight());
        history.redo();
        assertEquals(WholeContainerAnimationConfig.FlightPath.ARC, s.getFlightPath());
        assertEquals(-5, s.getArcHeight());
    }
    @Test void dampingAndDebrisSettingsRestoreOnUndoRedo() {
        var s = clip.getAnimationConfigForEditing().getWholeContainer();
        change(() -> {
            s.setMotionDamping(9);
            s.setDustPreset(WholeContainerAnimationConfig.DustPreset.DEBRIS_CLOUD);
            s.setDustIntensity(4); s.setDustSpread(2); s.setDebrisRatio(0.4f);
        }, false);
        history.undo();
        assertEquals(6, s.getMotionDamping());
        assertEquals(WholeContainerAnimationConfig.DustPreset.NONE, s.getDustPreset());
        assertEquals(0.8f, s.getDebrisRatio());
        history.redo();
        assertEquals(9, s.getMotionDamping());
        assertEquals(WholeContainerAnimationConfig.DustPreset.DEBRIS_CLOUD, s.getDustPreset());
        assertEquals(4, s.getDustIntensity()); assertEquals(2, s.getDustSpread());
        assertEquals(0.4f, s.getDebrisRatio());
    }
    @Test void scopeSwitchUndoAlsoRestoresCumulativeClipLength() {
        float originalLength = clip.getLength();
        change(() -> {
            clip.getAnimationConfigForEditing().setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK);
            clip.getAnimationConfigForEditing().setDuration(0.1f);
            clip.setLength(7);
        }, false);
        history.undo();
        assertEquals(originalLength, clip.getLength());
        assertTrue(clip.getAnimationConfig().isWholeContainer());
        history.redo();
        assertEquals(7, clip.getLength());
        assertFalse(clip.getAnimationConfig().isWholeContainer());
    }

    @Test void basicPropertiesRestorePrunedKeyframesAndVisibilityAsOneGesture() {
        clip.addKeyframe(10);
        clip.addKeyframe(15);
        clip.addKeyframe(20);
        clip.addVisibilityKeyframe(VisibilityKeyframe.ghost(2, 0.4f));
        var ids = clip.getClipKeyframes().stream().map(k -> k.getId()).toList();
        change(() -> {
            clip.setStartTime(12);
            clip.setLength(5);
            clip.setColor(0x123456);
            clip.setAutoFadePreviousClips(false);
            clip.setAutoFadeOpacity(0.7f);
            clip.setPostAnimationFadeEnabled(true);
            clip.setPostAnimationFadeStart(1);
            clip.setPostAnimationFadeEnd(3);
            clip.clearVisibilityKeyframes();
            clip.addVisibilityKeyframe(VisibilityKeyframe.hide(1));
        }, false);
        assertEquals(1, clip.getClipKeyframes().size());
        assertEquals(15, clip.getClipKeyframes().getFirst().getTime());
        history.undo();
        assertEquals(10, clip.getStartTime());
        assertEquals(20, clip.getEndTime());
        assertEquals(0, clip.getColor());
        assertTrue(clip.isAutoFadePreviousClips());
        assertEquals(0.35f, clip.getAutoFadeOpacity());
        assertFalse(clip.isPostAnimationFadeEnabled());
        assertEquals(ids, clip.getClipKeyframes().stream().map(k -> k.getId()).toList());
        assertEquals(VisibilityKeyframe.ghost(2, 0.4f), clip.getVisibilityKeyframes().first());
        history.redo();
        assertEquals(12, clip.getStartTime());
        assertEquals(17, clip.getEndTime());
        assertEquals(0x123456, clip.getColor());
        assertFalse(clip.isAutoFadePreviousClips());
        assertEquals(0.7f, clip.getAutoFadeOpacity());
        assertTrue(clip.isPostAnimationFadeEnabled());
        assertEquals(VisibilityKeyframe.hide(1), clip.getVisibilityKeyframes().first());
    }

    @Test void perBlockSequenceAndEffectEditsUndoAndRedoTogether() {
        var config = clip.getAnimationConfigForEditing();
        config.setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK);
        var sequence = config.getSequenceParameters();
        var effect = new com.director.core.animation.effect.EffectInstance(
                com.director.core.animation.effect.EffectType.FLASH_WHITE);
        change(() -> {
            config.setDelay(1.5f);
            config.setDuration(0.75f);
            config.setEasing("EASE_IN_CUBIC");
            config.setParam("strength", 3);
            sequence.setFlowType(com.director.core.animation.sequence.evaluator.FlowType.RANDOM);
            sequence.setBatchSize(4);
            config.addEffect(effect);
        }, false);
        var effectId = effect.getId();
        history.undo();
        assertSame(config, clip.getAnimationConfigForEditing());
        assertSame(sequence, config.getSequenceParameters());
        assertEquals(0, config.getDelay());
        assertEquals(2, config.getDuration());
        assertEquals("LINEAR", config.getEasing());
        assertFalse(config.getParams().containsKey("strength"));
        assertEquals(com.director.core.animation.sequence.evaluator.FlowType.PLANAR, sequence.getFlowType());
        assertEquals(10, sequence.getBatchSize());
        assertTrue(config.getEffects().isEmpty());
        history.redo();
        assertEquals(1.5f, config.getDelay());
        assertEquals(0.75f, config.getDuration());
        assertEquals("EASE_IN_CUBIC", config.getEasing());
        assertEquals(3, config.getParams().get("strength"));
        assertEquals(com.director.core.animation.sequence.evaluator.FlowType.RANDOM, sequence.getFlowType());
        assertEquals(4, sequence.getBatchSize());
        assertEquals(effectId, config.getEffects().getFirst().getId());
    }

    @Test void cancelRestoresTheLivePreviewWithoutCreatingHistory() {
        clip.addKeyframe(15);
        clip.addKeyframe(20);
        edits.beginFrame(model, clip);
        clip.setStartTime(12);
        clip.setLength(2);
        clip.setColor(0xabcdef);
        edits.endFrame(true, true, "Edit clip properties");
        edits.cancel();
        assertEquals(0, history.getUndoSize());
        assertEquals(10, clip.getStartTime());
        assertEquals(20, clip.getEndTime());
        assertEquals(0, clip.getColor());
        assertEquals(2, clip.getClipKeyframes().size());
    }
}
