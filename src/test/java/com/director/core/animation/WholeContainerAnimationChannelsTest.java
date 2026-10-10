package com.director.core.animation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationChannelsTest {
    private ClipAnimationConfig config() {
        var c = ClipAnimationConfig.defaultConfig(); c.setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        c.setDuration(2); return c;
    }
    @Test void allSixteenChannelCombinationsKeepDisabledComponentsAtIdentity() {
        for (int mask = 0; mask < 16; mask++) {
            var c = config(); var s = c.getWholeContainer(); s.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
            s.setOffset(4, -8, 12); s.setRotation(180, 720, -360); s.setScale(0.2f); s.setOpacity(0.1f);
            s.setTranslationEnabled((mask & 1) != 0); s.setRotationEnabled((mask & 2) != 0);
            s.setScaleEnabled((mask & 4) != 0); s.setOpacityEnabled((mask & 8) != 0);
            for (boolean exit : new boolean[] {false, true}) {
                var f = WholeContainerAnimation.sample(c, 10, 20, 11, exit);
                assertEquals((mask & 1) != 0 ? 2 : 0, f.x());
                assertEquals((mask & 1) != 0 ? -4 : 0, f.y());
                assertEquals((mask & 1) != 0 ? 6 : 0, f.z());
                assertEquals((mask & 2) != 0 ? 90 : 0, f.rotationX());
                assertEquals((mask & 2) != 0 ? 360 : 0, f.rotationY());
                assertEquals((mask & 2) != 0 ? -180 : 0, f.rotationZ());
                assertEquals((mask & 4) != 0 ? 0.6f : 1, f.scale(), 0.0001f);
                assertEquals((mask & 8) != 0 ? 0.55f : 1, f.alpha(), 0.0001f);
                assertFalse(f.worldVisible()); assertTrue(f.animating());
            }
        }
    }
    @Test void standaloneFadeHasNoTransformAndHandsOffOnlyAtTheEnd() {
        var c = config(); var s = c.getWholeContainer(); s.setEffect(WholeContainerAnimationConfig.Effect.FADE);
        s.setScale(7); s.setOffset(20, 30, 40); s.setRotation(720, 360, 180); s.setOpacity(0.2f);
        for (boolean exit : new boolean[] {false, true}) {
            var start = WholeContainerAnimation.sample(c, 10, 20, 10, exit);
            var mid = WholeContainerAnimation.sample(c, 10, 20, 11, exit);
            var end = WholeContainerAnimation.sample(c, 10, 20, 12, exit);
            assertEquals(exit ? 1 : 0.2f, start.alpha(), 0.0001f);
            assertEquals(0.6f, mid.alpha(), 0.0001f);
            assertEquals(1, mid.scale()); assertEquals(0, mid.x()); assertEquals(0, mid.rotationY());
            assertFalse(mid.worldVisible()); assertTrue(mid.renderVisible());
            assertEquals(!exit, end.worldVisible()); assertEquals(exit ? 0 : 1, end.alpha());
        }
    }
    @Test void fullyTransparentFramesRemainWorldSuppressedUntilHandoff() {
        var c = config(); c.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.FADE);
        var f = WholeContainerAnimation.sample(c, 10, 20, 10, false);
        assertEquals(0, f.alpha()); assertFalse(f.worldVisible()); assertTrue(f.animating());
        assertTrue(WholeContainerAnimation.sample(c, 10, 20, 12, false).worldVisible());
    }
    @Test void opacityClampsOvershootingEasingAndCanBeSeekedBackwards() {
        var c = config(); c.setEasing("EASE_OUT_BACK"); c.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.FADE);
        for (boolean exit : new boolean[] {false, true}) {
            for (int step = 0; step <= 200; step++) {
                var f = WholeContainerAnimation.sample(c, 10, 20, 10 + step / 100.0f, exit);
                assertTrue(f.alpha() >= 0 && f.alpha() <= 1);
            }
            var mid = WholeContainerAnimation.sample(c, 10, 20, 10.5f, exit);
            WholeContainerAnimation.sample(c, 10, 20, 15, exit);
            assertEquals(mid, WholeContainerAnimation.sample(c, 10, 20, 10.5f, exit));
        }
    }
    @Test void togglingChannelsRetainsAuthoredValues() {
        var c = config(); var s = c.getWholeContainer(); s.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
        s.setOffset(4, 8, 12); s.setTranslationEnabled(false);
        assertEquals(0, WholeContainerAnimation.sample(c, 10, 20, 11, false).y());
        assertEquals(8, s.getOffsetY()); s.setTranslationEnabled(true);
        assertEquals(4, WholeContainerAnimation.sample(c, 10, 20, 11, false).y());
    }
    @Test void legacyJsonRetainsAllThreeTransformsAndDoesNotFadeUnexpectedly() {
        var old = new Gson().fromJson("{\"effect\":\"TRANSFORM\",\"scale\":0.25,\"offsetY\":8,\"rotationY\":720}", WholeContainerAnimationConfig.class);
        var c = config(); c.setWholeContainer(old);
        var f = WholeContainerAnimation.sample(c, 10, 20, 11, false);
        assertEquals(0.625f, f.scale()); assertEquals(4, f.y()); assertEquals(360, f.rotationY()); assertEquals(1, f.alpha());
        assertTrue(old.isTranslationEnabled()); assertTrue(old.isRotationEnabled()); assertTrue(old.isScaleEnabled());
        assertFalse(old.isOpacityEnabled());
    }
    @Test void instantClipsIgnoreDurationButRespectDelayAndWorldDirection() {
        var c = config(); c.setDelay(1); c.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.INSTANT);
        assertEquals(new WholeContainerAnimation.Window(11, 11), WholeContainerAnimation.window(c, 10, 20));
        for (boolean exit : new boolean[] {false, true}) {
            assertEquals(exit, WholeContainerAnimation.sample(c, 10, 20, 10.5f, exit).worldVisible());
            var at = WholeContainerAnimation.sample(c, 10, 20, 11, exit);
            assertEquals(!exit, at.worldVisible()); assertFalse(at.animating());
        }
    }
}
