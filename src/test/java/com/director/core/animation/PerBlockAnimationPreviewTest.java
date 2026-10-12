package com.director.core.animation;

import com.director.core.animation.effect.EffectInstance;
import com.director.core.animation.effect.EffectType;
import com.director.core.animation.sequence.BlockSequenceEntry;
import com.director.core.container.AnimationMode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PerBlockAnimationPreviewTest {
    private ClipAnimationConfig config(AnimationMode mode, ClipAnimationConfig.AnimationPhase phase) {
        var config = ClipAnimationConfig.defaultConfig();
        config.setMode(mode);
        config.setPhase(phase);
        config.setDuration(2);
        config.setDelay(0.25f);
        return config;
    }

    private void assertSamePose(BlockAnimationSnapshot expected, BlockAnimationSnapshot actual) {
        assertEquals(expected.isVisible(), actual.isVisible());
        assertEquals(expected.isAnimationFinished(), actual.isAnimationFinished());
        assertEquals(expected.getAlpha(), actual.getAlpha(), 0.00001f);
        assertEquals(expected.getRawProgress(), actual.getRawProgress(), 0.00001f);
        assertEquals(expected.getEasedProgress(), actual.getEasedProgress(), 0.00001f);
        if (expected.getTransform() == null) {
            assertNull(actual.getTransform());
        } else {
            assertNotNull(actual.getTransform());
            assertTrue(expected.getTransform().peek().getPositionMatrix()
                    .equals(actual.getTransform().peek().getPositionMatrix(), 0.00001f));
            assertTrue(expected.getTransform().peek().getNormalMatrix()
                    .equals(actual.getTransform().peek().getNormalMatrix(), 0.00001f));
        }
    }

    @Test void forwardAndBackwardScrubbingMatchPlaybackForEveryModeAndPhase() {
        var pos = new BlockPos(3, 8, -4);
        var entry = new BlockSequenceEntry(pos, 0.75f, 0, 0);
        // The last block starts at 11 and completes at 13, after this one-second clip ends.
        float[] times = {9, 10.5f, 11, 11.25f, 12, 12.75f, 13, 14, 12, 11.25f, 10.5f};
        for (var mode : AnimationMode.values()) {
            for (var phase : ClipAnimationConfig.AnimationPhase.values()) {
                var config = config(mode, phase);
                for (float time : times) {
                    assertSamePose(BlockAnimationEngine.sample(pos, null, config, entry, 10, 11, time, 42),
                            BlockAnimationEngine.samplePreview(pos, null, config, entry, 10, 11, time, 42));
                }
            }
        }
    }

    @Test void previewPreservesOverlappingActionsWithDifferentProgress() {
        var config = config(AnimationMode.FADE_IN, ClipAnimationConfig.AnimationPhase.ENTER);
        config.setDelay(0);
        var first = new BlockSequenceEntry(BlockPos.ORIGIN, 0, 0, 0);
        var next = new BlockSequenceEntry(new BlockPos(1, 0, 0), 0.1f, 0, 0);
        var a = BlockAnimationEngine.samplePreview(first.getPos(), null, config, first, 0, 2, 0.5f, 42);
        var b = BlockAnimationEngine.samplePreview(next.getPos(), null, config, next, 0, 2, 0.5f, 42);
        assertTrue(a.isVisible()); assertTrue(b.isVisible());
        assertFalse(a.isAnimationFinished()); assertFalse(b.isAnimationFinished());
        assertEquals(0.25f, a.getRawProgress(), 0.00001f);
        assertEquals(0.15f, b.getRawProgress(), 0.00001f);
        assertTrue(a.getAlpha() > b.getAlpha());
    }

    @Test void scrubbingDoesNotCreateOrAdvanceParticleTriggerState() throws Exception {
        var gate = Class.forName(BlockAnimationEngine.class.getName() + "$EffectRuntimeGate");
        var statesField = gate.getDeclaredField("STATES");
        statesField.setAccessible(true);
        var states = (Map<?, ?>) statesField.get(null);
        var before = Map.copyOf(states);
        for (var mode : AnimationMode.values()) {
            for (var phase : ClipAnimationConfig.AnimationPhase.values()) {
                var config = config(mode, phase);
                var effect = new EffectInstance(EffectType.DUST_BURST);
                effect.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
                config.getEffects().add(effect);
                var entry = new BlockSequenceEntry(BlockPos.ORIGIN, 0, 0, 0);
                for (float time : new float[]{0, 0.25f, 1, 2.25f, 4, 1, 0.25f})
                    BlockAnimationEngine.samplePreview(BlockPos.ORIGIN, null, config, entry, 0, 1, time, 42);
            }
        }
        assertEquals(before, states);
    }
}
