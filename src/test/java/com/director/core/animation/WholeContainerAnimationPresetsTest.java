package com.director.core.animation;

import com.director.core.animation.easing.EasingFunction;
import com.director.core.container.Container;
import net.minecraft.util.math.BlockPos;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationPresetsTest {
    private ClipAnimationConfig config() {
        var config = ClipAnimationConfig.defaultConfig();
        config.setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        config.setDelay(1); config.setDuration(3);
        return config;
    }
    @Test void presetsPreserveTimingAndHiddenPerBlockSettings() {
        for (var preset : WholeContainerAnimationPresets.Preset.values()) {
            var config = config();
            var sequence = config.getSequenceParameters(); sequence.setGlobalDelay(7);
            config.getParams().put("authoredValue", 42);
            config.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
            WholeContainerAnimationPresets.apply(config, preset, null);
            assertEquals(3, config.getDuration()); assertEquals(1, config.getDelay());
            assertEquals(ClipAnimationConfig.AnimationPhase.EXIT, config.getPhase());
            assertSame(sequence, config.getSequenceParameters()); assertEquals(7, sequence.getGlobalDelay());
            assertEquals(42, config.getParams().get("authoredValue")); assertTrue(config.isWholeContainer());
        }
    }
    @Test void flightPresetsStartBeyondTheAssemblyBoundsAndUseIndependentInstances() {
        var bounds = new Container.BoundingBox(new BlockPos(-20, -10, 0), new BlockPos(19, 9, 3));
        var config = config();
        WholeContainerAnimationPresets.apply(config, WholeContainerAnimationPresets.Preset.FROM_ABOVE, bounds);
        var above = config.getWholeContainer(); assertTrue(above.getOffsetY() > bounds.getHeight());
        WholeContainerAnimationPresets.apply(config, WholeContainerAnimationPresets.Preset.FROM_BELOW, bounds);
        assertTrue(config.getWholeContainer().getOffsetY() < -bounds.getHeight());
        WholeContainerAnimationPresets.apply(config, WholeContainerAnimationPresets.Preset.FROM_SIDE, bounds);
        assertTrue(config.getWholeContainer().getOffsetX() < -bounds.getWidth());
        assertTrue(above.getOffsetY() > bounds.getHeight());
        WholeContainerAnimationPresets.apply(config, WholeContainerAnimationPresets.Preset.FROM_ABOVE, null);
        assertEquals(10, config.getWholeContainer().getOffsetY());
    }
    @Test void everyPresetCompletesTheFullTransformInBothDirections() {
        for (var preset : WholeContainerAnimationPresets.Preset.values()) {
            var config = config(); WholeContainerAnimationPresets.apply(config, preset, null);
            var settings = config.getWholeContainer();
            var start = WholeContainerAnimation.sample(config, 10, 20, 11, false);
            var almostExit = WholeContainerAnimation.sample(config, 10, 20, 13.999f, true);
            assertEquals(start.scale(), almostExit.scale(), 0.002f);
            assertEquals(start.y(), almostExit.y(), 0.002f);
            assertEquals(start.rotationY(), almostExit.rotationY(), 0.002f);
            assertEquals(start.alpha(), almostExit.alpha(), 0.002f);
            if (settings.getEffect() == WholeContainerAnimationConfig.Effect.ROTATE)
                assertEquals(720, start.rotationY());
            assertTrue(WholeContainerAnimation.sample(config, 10, 20, 14, false).worldVisible());
            assertFalse(WholeContainerAnimation.sample(config, 10, 20, 14, true).renderVisible());
        }
    }
    @Test void resetOnlyTouchesTransformAndLeavesCurrentEffectAndTimingIntact() {
        for (var effect : WholeContainerAnimationConfig.Effect.values()) {
            var config = config(); var c = config.getWholeContainer(); c.setEffect(effect);
            c.setPivot(WholeContainerAnimationConfig.Pivot.CUSTOM); c.setCustomPivot(8, 9, 10);
            c.setRotation(-180, -720, 360); c.setOffset(-25, 8, 3); c.setScale(5);
            config.setEasing("EASE_OUT_ELASTIC");
            WholeContainerAnimationPresets.resetTransform(config);
            c = config.getWholeContainer();
            assertEquals(effect, c.getEffect()); assertNotEquals(WholeContainerAnimationConfig.Pivot.CUSTOM, c.getPivot());
            assertEquals(0, c.getPivotX()); assertEquals(0, c.getPivotY()); assertEquals(0, c.getPivotZ());
            assertEquals(3, config.getDuration()); assertEquals(1, config.getDelay());
            assertEquals(EasingFunction.EASE_OUT_ELASTIC.name(), config.getEasing());
            if (effect == WholeContainerAnimationConfig.Effect.ROTATE) {
                assertEquals(1, c.getScale()); assertEquals(180, c.getRotationY()); assertEquals(0, c.getOffsetY());
            }
        }
    }
    @Test void presetParametersSurviveJsonAndCopiesWithoutSharingMutableState() {
        var gson = new Gson();
        for (var preset : WholeContainerAnimationPresets.Preset.values()) {
            var config = config(); WholeContainerAnimationPresets.apply(config, preset, null);
            var copy = config.copy();
            var restored = gson.fromJson(gson.toJson(config), ClipAnimationConfig.class);
            var expected = WholeContainerAnimation.sample(config, 10, 20, 12, false);
            assertEquals(expected, WholeContainerAnimation.sample(copy, 10, 20, 12, false));
            assertEquals(expected, WholeContainerAnimation.sample(restored, 10, 20, 12, false));
            copy.getWholeContainer().setOffset(99, 99, 99);
            assertNotEquals(99, config.getWholeContainer().getOffsetX());
        }
    }
    @Test void elasticGrowthOvershootsAndBounceDropAddsHopsWithoutChangingOpacity() {
        var c = config();
        WholeContainerAnimationPresets.apply(c, WholeContainerAnimationPresets.Preset.ELASTIC_GROW, null);
        assertEquals(WholeContainerAnimationConfig.Pivot.BOTTOM_CENTER, c.getWholeContainer().getPivot());
        assertEquals(WholeContainerAnimationConfig.Motion.ELASTIC, c.getWholeContainer().getMotion());
        assertTrue(WholeContainerAnimation.sample(c, 10, 20, 11.75f, false).scale() > 1);
        WholeContainerAnimationPresets.apply(c, WholeContainerAnimationPresets.Preset.BOUNCE_DROP, null);
        var plain = c.copy(); plain.getWholeContainer().setMotion(WholeContainerAnimationConfig.Motion.NONE);
        for (boolean exit : new boolean[] {false, true}) {
            var bouncing = WholeContainerAnimation.sample(c, 10, 20, 12.5f, exit);
            var baseline = WholeContainerAnimation.sample(plain, 10, 20, 12.5f, exit);
            assertTrue(bouncing.y() > 0);
            assertTrue(bouncing.y() < c.getWholeContainer().getBounceHeight());
            assertEquals(baseline.scale(), bouncing.scale()); assertEquals(baseline.alpha(), bouncing.alpha());
        }
    }
    @Test void bounceDropScalesDistanceAndHopHeightWithAssemblyBounds() {
        var c = config();
        var small = new Container.BoundingBox(BlockPos.ORIGIN, new BlockPos(1, 1, 1));
        WholeContainerAnimationPresets.apply(c, WholeContainerAnimationPresets.Preset.BOUNCE_DROP, small);
        float smallHeight = c.getWholeContainer().getBounceHeight();
        var large = new Container.BoundingBox(BlockPos.ORIGIN, new BlockPos(30, 30, 30));
        WholeContainerAnimationPresets.apply(c, WholeContainerAnimationPresets.Preset.BOUNCE_DROP, large);
        assertTrue(c.getWholeContainer().getOffsetY() > large.getHeight());
        assertTrue(c.getWholeContainer().getBounceHeight() > smallHeight);
        assertTrue(c.getWholeContainer().getBounceHeight() <= 2);
    }
    @Test void motionPresetsKeepTheirShapeWhenDurationChangesInBothDirections() {
        for (var preset : new WholeContainerAnimationPresets.Preset[] {
                WholeContainerAnimationPresets.Preset.ELASTIC_GROW, WholeContainerAnimationPresets.Preset.BOUNCE_DROP}) {
            var c = config(); WholeContainerAnimationPresets.apply(c, preset, null);
            for (boolean exit : new boolean[] {false, true}) {
                c.setDuration(3);
                var before = WholeContainerAnimation.sample(c, 10, 20, 12.5f, exit);
                c.setDuration(1);
                assertEquals(before, WholeContainerAnimation.sample(c, 10, 20, 11.5f, exit));
            }
        }
    }
    @Test void arcPresetAdaptsToAssemblyBoundsAndKeepsVisualSettings() {
        var c = config();
        c.getWholeContainer().setDustPreset(WholeContainerAnimationConfig.DustPreset.SHOCKWAVE);
        c.getWholeContainer().setShockwaveCount(5);
        var bounds = new Container.BoundingBox(BlockPos.ORIGIN, new BlockPos(19, 15, 4));
        WholeContainerAnimationPresets.apply(c, WholeContainerAnimationPresets.Preset.ARC_FLY, bounds);
        var s = c.getWholeContainer();
        assertTrue(Math.abs(s.getOffsetX()) > bounds.getWidth());
        assertTrue(s.getArcHeight() >= bounds.getHeight() * 0.75f);
        assertEquals(WholeContainerAnimationConfig.FlightPath.ARC, s.getFlightPath());
        assertEquals(WholeContainerAnimationConfig.DustPreset.SHOCKWAVE, s.getDustPreset());
        assertEquals(5, s.getShockwaveCount());
        WholeContainerAnimationPresets.resetTransform(c);
        assertEquals(WholeContainerAnimationConfig.FlightPath.STRAIGHT, c.getWholeContainer().getFlightPath());
    }
}
