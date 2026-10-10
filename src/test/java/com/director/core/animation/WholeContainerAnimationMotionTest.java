package com.director.core.animation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationMotionTest {
    private ClipAnimationConfig config(WholeContainerAnimationConfig.Motion motion) {
        var c = ClipAnimationConfig.defaultConfig();
        c.setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        c.setDuration(2); c.setEasing("LINEAR");
        var s = c.getWholeContainer();
        s.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
        s.setOffset(4, 0, 0); s.setRotation(0, 360, 0); s.setScale(0);
        s.setOpacityEnabled(true); s.setMotion(motion);
        return c;
    }
    private WholeContainerAnimation.Frame at(ClipAnimationConfig c, float phase, boolean exit) {
        return WholeContainerAnimation.sample(c, 10, 20, 10 + phase * c.getDuration(), exit);
    }
    @Test void elasticOvershootsGeometryButLeavesOpacityAndWorldHandoffAlone() {
        var c = config(WholeContainerAnimationConfig.Motion.ELASTIC);
        var f = at(c, 1f / 6, false);
        assertTrue(f.scale() > 1);
        assertTrue(f.x() < 0); assertTrue(f.rotationY() < 0);
        assertEquals(1f / 6, f.alpha(), 0.00001f);
        assertFalse(f.worldVisible());
        var end = at(c, 1, false);
        assertTrue(end.worldVisible()); assertFalse(end.animating());
        assertEquals(1, end.scale()); assertEquals(0, end.y());
    }
    @Test void bounceStaysAboveTheBasePathAndDecaysWithReversedExit() {
        var c = config(WholeContainerAnimationConfig.Motion.BOUNCE);
        c.getWholeContainer().setBounceHeight(2);
        var early = at(c, 1f / 6, false);
        var late = at(c, 0.5f, false);
        assertTrue(early.y() > late.y()); assertTrue(late.y() > 0);
        assertEquals(0, at(c, 0, false).y());
        for (int i = 0; i < 100; i++) {
            float p = i / 100f;
            var enter = at(c, p, false);
            assertTrue(enter.y() >= 0); assertFalse(enter.worldVisible());
            if (i > 0) {
                var exit = at(c, 1 - p, true);
                assertEquals(enter.y(), exit.y(), 0.00002f);
            }
        }
    }
    @Test void zeroStrengthRestoresUnmodifiedSamplingAndDisabledChannelsStayDisabled() {
        for (var motion : WholeContainerAnimationConfig.Motion.values()) {
            var c = config(motion); c.getWholeContainer().setMotionStrength(0);
            var baseline = config(WholeContainerAnimationConfig.Motion.NONE);
            for (boolean exit : new boolean[] {false, true})
                assertEquals(at(baseline, 0.3f, exit), at(c, 0.3f, exit));
            var s = c.getWholeContainer(); s.setMotionStrength(1);
            s.setTranslationEnabled(false); s.setRotationEnabled(false); s.setScaleEnabled(false);
            var fade = at(c, 0.3f, false);
            assertEquals(1, fade.scale()); assertEquals(0, fade.y()); assertEquals(0, fade.rotationY());
            assertEquals(0.3f, fade.alpha(), 0.00001f);
        }
    }
    @Test void normalizedShapeSurvivesDurationChangesClipTruncationAndSeeking() {
        for (var motion : WholeContainerAnimationConfig.Motion.values()) {
            var c = config(motion);
            for (boolean exit : new boolean[] {false, true}) {
                var before = at(c, 0.25f, exit);
                c.setDuration(1);
                assertEquals(before, at(c, 0.25f, exit));
                c.setDuration(2);
                assertEquals(before, WholeContainerAnimation.sample(c, 10, 11, 10.25f, exit));
                at(c, 0.8f, exit);
                assertEquals(before, at(c, 0.25f, exit));
            }
        }
    }
    @Test void legacyDefaultsCopyAndJsonPreserveIndependentModifierSettings() {
        var legacy = new Gson().fromJson("{}", WholeContainerAnimationConfig.class);
        assertEquals(WholeContainerAnimationConfig.Motion.NONE, legacy.getMotion());
        assertEquals(1, legacy.getMotionStrength()); assertEquals(3, legacy.getMotionCycles());
        var c = config(WholeContainerAnimationConfig.Motion.BOUNCE);
        var s = c.getWholeContainer(); s.setMotionStrength(0.4f); s.setMotionCycles(5); s.setBounceHeight(7);
        var loaded = new Gson().fromJson(new Gson().toJson(c), ClipAnimationConfig.class);
        assertEquals(at(c, 0.25f, false), at(loaded, 0.25f, false));
        var copy = c.copy();
        assertEquals(at(c, 0.25f, false), at(copy, 0.25f, false));
        copy.getWholeContainer().setBounceHeight(20);
        assertEquals(7, s.getBounceHeight());
    }
    @Test void invalidModifierValuesAreBoundedAndInstantIgnoresModifiers() {
        var c = config(WholeContainerAnimationConfig.Motion.BOUNCE); var s = c.getWholeContainer();
        s.setMotionStrength(Float.NaN); s.setBounceHeight(Float.POSITIVE_INFINITY); s.setMotionCycles(999);
        assertEquals(1, s.getMotionStrength()); assertEquals(1, s.getBounceHeight()); assertEquals(8, s.getMotionCycles());
        s.setMotionStrength(-5); s.setBounceHeight(-5); s.setMotionCycles(-5);
        assertEquals(0, s.getMotionStrength()); assertEquals(0, s.getBounceHeight()); assertEquals(1, s.getMotionCycles());
        s.setEffect(WholeContainerAnimationConfig.Effect.INSTANT);
        assertFalse(at(c, 0, false).animating()); assertTrue(at(c, 0, false).worldVisible());
    }
}
