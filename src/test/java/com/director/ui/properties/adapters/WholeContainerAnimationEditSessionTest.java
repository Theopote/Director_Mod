package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.WholeContainerAnimationConfig;
import com.director.core.animation.WholeContainerAnimationPresets;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationEditSessionTest {
    TimelineModel model;
    TimelineTrack track;
    TimelineClip clip;
    TimelineCommandHistory history;
    WholeContainerAnimationEditSession edits;
    AtomicInteger refreshes;

    @BeforeEach void setup() {
        model = new TimelineModel(new TimelineViewportState());
        track = new TimelineTrack("Blocks", TrackType.CONTAINER);
        model.addTrack(track);
        clip = new TimelineClip("Structure", 10, 20, 0);
        clip.getAnimationConfig().setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        track.addClip(clip);
        history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        refreshes = new AtomicInteger();
        edits = new WholeContainerAnimationEditSession(c -> {
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
            change(() -> clip.getAnimationConfig().getWholeContainer().setRotation(0, angle, 0), true);
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
        var config = clip.getAnimationConfig();
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
        assertSame(config, clip.getAnimationConfig());
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
        clip.getAnimationConfig().setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK);
        change(() -> clip.getAnimationConfig().setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER), false);
        history.undo(); assertFalse(clip.getAnimationConfig().isWholeContainer());
        history.redo(); assertTrue(clip.getAnimationConfig().isWholeContainer());
        change(() -> clip.getAnimationConfig().setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK), false);
        history.undo(); assertTrue(clip.getAnimationConfig().isWholeContainer());
        assertEquals(1, history.getUndoSize());
        assertEquals(1, history.getRedoSize());
        assertEquals(180, clip.getAnimationConfig().getWholeContainer().getRotationY());
    }
    @Test void applyingPresetAndResettingEachHaveOneUndoStep() {
        var config = clip.getAnimationConfig();
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
        var s = clip.getAnimationConfig().getWholeContainer(); s.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
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
        change(() -> clip.getAnimationConfig().getWholeContainer().setRotation(0, 720, 0), true);
        change(() -> clip.getAnimationConfig().getWholeContainer().setRotation(0, 180, 0), false);
        assertEquals(0, history.getUndoSize()); assertEquals(0, refreshes.get());
    }
    @Test void collapsedSectionFlushesThePendingGesture() {
        change(() -> clip.getAnimationConfig().setDelay(1), true);
        edits.finishIfIdle(false);
        assertEquals(1, history.getUndoSize());
        history.undo(); assertEquals(0, clip.getAnimationConfig().getDelay());
        history.redo(); assertEquals(1, clip.getAnimationConfig().getDelay());
    }
    @Test void switchingClipsCommitsToOriginalTarget() {
        change(() -> clip.getAnimationConfig().getWholeContainer().setScale(0.5f), true);
        var other = new TimelineClip("Other", 20, 30, 0); track.addClip(other);
        edits.beginFrame(model, other);
        assertEquals(1, history.getUndoSize());
        history.undo(); assertEquals(0, clip.getAnimationConfig().getWholeContainer().getScale());
        assertEquals(0, other.getAnimationConfig().getWholeContainer().getScale());
    }
    @Test void removedClipDropsPendingEdit() {
        change(() -> clip.getAnimationConfig().setDuration(1), true);
        track.removeClip(clip); edits.finishIfIdle(false);
        assertEquals(0, history.getUndoSize()); assertEquals(0, refreshes.get());
    }
    @Test void changingProjectsDoesNotPutOldGestureIntoNewHistory() {
        change(() -> clip.getAnimationConfig().setDuration(1), true);
        edits.beginFrame(new TimelineModel(new TimelineViewportState()), null);
        edits.finishIfIdle(false);
        assertEquals(0, history.getUndoSize()); assertEquals(0, refreshes.get());
    }
    @Test void independentConsecutiveDragsProduceIndependentUndoSteps() {
        change(() -> clip.getAnimationConfig().getWholeContainer().setRotation(0, 720, 0), false);
        change(() -> clip.getAnimationConfig().getWholeContainer().setScale(0.5f), false);
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
            clip.getAnimationConfig().setDuration(1);
            model.setCurrentTime(10.25f); // live UI preview adjustment
        }, false);
        history.undo(); assertEquals(10.5f, model.getCurrentTime());
        history.redo(); assertEquals(10.25f, model.getCurrentTime());
    }
    @Test void perBlockOnlyEditsDoNotEnterWholeAnimationHistory() {
        clip.getAnimationConfig().setScope(ClipAnimationConfig.AnimationScope.PER_BLOCK);
        change(() -> clip.getAnimationConfig().setDuration(1), false);
        assertEquals(0, history.getUndoSize()); assertEquals(0, refreshes.get());
    }
    @Test void motionModifierAndParametersUndoAndRedoTogether() {
        var s = clip.getAnimationConfig().getWholeContainer();
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
        var s = clip.getAnimationConfig().getWholeContainer();
        change(() -> {
            s.setDustEnabled(true); s.setDustIntensity(2); s.setDustSpread(3);
        }, false);
        history.undo();
        assertFalse(s.isDustEnabled()); assertEquals(1, s.getDustIntensity()); assertEquals(0.6f, s.getDustSpread());
        history.redo();
        assertTrue(s.isDustEnabled()); assertEquals(2, s.getDustIntensity()); assertEquals(3, s.getDustSpread());
    }
    @Test void visualPresetSelectionUndoAndRedoRestoresPresetAndParameters() {
        var s = clip.getAnimationConfig().getWholeContainer();
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
}
