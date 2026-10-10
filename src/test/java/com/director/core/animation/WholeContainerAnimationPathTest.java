package com.director.core.animation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationPathTest {
    private ClipAnimationConfig config() {
        var c = ClipAnimationConfig.defaultConfig();
        c.setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER); c.setDuration(2); c.setEasing("LINEAR");
        var s = c.getWholeContainer(); s.setEffect(WholeContainerAnimationConfig.Effect.FLY);
        s.setOffset(8, 4, -12); s.setFlightPath(WholeContainerAnimationConfig.FlightPath.ARC); s.setArcHeight(3);
        return c;
    }
    private WholeContainerAnimation.Frame at(ClipAnimationConfig c, float p, boolean exit) {
        return WholeContainerAnimation.sample(c, 10, 20, 10 + c.getDuration() * p, exit);
    }
    @Test void arcHasAuthoredMidpointLiftAndPreservesEndpoints() {
        var c = config(); var start = at(c, 0, false); var mid = at(c, 0.5f, false);
        assertEquals(8, start.x()); assertEquals(4, start.y()); assertEquals(-12, start.z());
        assertEquals(4, mid.x()); assertEquals(5, mid.y()); assertEquals(-6, mid.z());
        var almostEnd = at(c, 0.99999f, false);
        assertEquals(0, almostEnd.y(), 0.001f);
        var end = at(c, 1, false);
        assertEquals(0, end.y()); assertTrue(end.worldVisible()); assertFalse(end.animating());
        assertEquals(0, at(c, 0, true).y()); assertFalse(at(c, 1, true).worldVisible());
    }
    @Test void exitTraversesTheSameSpatialArcAndNegativeHeightBendsDownward() {
        var c = config();
        for (int i = 1; i < 100; i++) {
            float p = i / 100f;
            var enter = at(c, p, false); var exit = at(c, 1 - p, true);
            assertEquals(enter.x(), exit.x(), 0.00002f);
            assertEquals(enter.y(), exit.y(), 0.00002f);
            assertEquals(enter.z(), exit.z(), 0.00002f);
        }
        c.getWholeContainer().setArcHeight(-3);
        assertEquals(-1, at(c, 0.5f, false).y());
        c.getWholeContainer().setArcHeight(0);
        assertEquals(2, at(c, 0.5f, false).y());
    }
    @Test void pathComposesWithOtherChannelsAndStopsWhenTranslationIsDisabled() {
        var c = config(); var s = c.getWholeContainer();
        s.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
        s.setScale(0); s.setRotation(0, 720, 0); s.setOpacityEnabled(true); s.setOpacity(0);
        var mid = at(c, 0.5f, false);
        assertEquals(0.5f, mid.scale()); assertEquals(360, mid.rotationY()); assertEquals(0.5f, mid.alpha());
        s.setTranslationEnabled(false);
        var disabled = at(c, 0.5f, false);
        assertEquals(0, disabled.x()); assertEquals(0, disabled.y()); assertEquals(0, disabled.z());
        assertEquals(mid.scale(), disabled.scale()); assertEquals(mid.rotationY(), disabled.rotationY());
        assertEquals(mid.alpha(), disabled.alpha()); assertEquals(3, s.getArcHeight());
    }
    @Test void durationTruncationAndBackwardSeekingKeepTheSameNormalizedPath() {
        var c = config(); var before = at(c, 0.25f, false);
        c.setDuration(1); assertEquals(before, at(c, 0.25f, false));
        c.setDuration(2);
        assertEquals(before, WholeContainerAnimation.sample(c, 10, 11, 10.25f, false));
        at(c, 0.8f, false); assertEquals(before, at(c, 0.25f, false));
    }
    @Test void legacyStraightPathsAndAuthoredArcSettingsSurviveJsonAndCopies() {
        var gson = new Gson();
        var legacy = gson.fromJson("{}", WholeContainerAnimationConfig.class);
        assertEquals(WholeContainerAnimationConfig.FlightPath.STRAIGHT, legacy.getFlightPath());
        var c = config(); var loaded = gson.fromJson(gson.toJson(c), ClipAnimationConfig.class);
        assertEquals(at(c, 0.3f, false), at(loaded, 0.3f, false));
        assertEquals(at(c, 0.3f, false), at(c.copy(), 0.3f, false));
        var copy = c.copy(); copy.getWholeContainer().setArcHeight(10);
        assertEquals(3, c.getWholeContainer().getArcHeight());
        c.getWholeContainer().setFlightPath(WholeContainerAnimationConfig.FlightPath.STRAIGHT);
        assertEquals(2, at(c, 0.5f, false).y());
    }
    @Test void springOvershootKeepsPathFiniteAndAlphaBounded() {
        var c = config(); c.setEasing("EASE_OUT_BACK");
        var s = c.getWholeContainer(); s.setArcHeight(1000);
        for (var motion : WholeContainerAnimationConfig.Motion.values()) {
            s.setMotion(motion);
            for (boolean exit : new boolean[] {false, true})
                for (int i = 0; i < 100; i++) {
                    var f = at(c, i / 100f, exit);
                    assertTrue(Float.isFinite(f.x()) && Float.isFinite(f.y()) && Float.isFinite(f.z()));
                    assertTrue(f.alpha() >= 0 && f.alpha() <= 1);
                }
        }
        s.setArcHeight(Float.NaN); assertEquals(3, s.getArcHeight());
        s.setArcHeight(Float.POSITIVE_INFINITY); assertEquals(3, s.getArcHeight());
        s.setArcHeight(-2000); assertEquals(-1000, s.getArcHeight());
    }
}
