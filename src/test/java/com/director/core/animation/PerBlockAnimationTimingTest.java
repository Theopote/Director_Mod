package com.director.core.animation;

import com.director.core.container.AnimationMode;
import com.director.core.animation.sequence.BlockSequenceEntry;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerBlockAnimationTimingTest {
    private ClipAnimationConfig config() {
        var c = ClipAnimationConfig.defaultConfig();
        c.setMode(AnimationMode.FADE_IN); c.setDuration(2); c.setDelay(0);
        return c;
    }
    @Test void fullClipDurationDoesNotTrimLaterBlocksOrStartExitBeforeDelay() {
        var c = config(); c.setDelay(0.5f); c.setDuration(2);
        var layout = PerBlockAnimationTiming.layout(c, 10, 12);
        for (float n : new float[] {0, 0.5f, 1}) {
            var w = layout.window(n);
            assertEquals(10.5f, w.start());
            assertEquals(12, w.end());
        }
        assertEquals(Float.NEGATIVE_INFINITY, layout.threshold(-0.1f));
        assertEquals(Float.POSITIVE_INFINITY, layout.threshold(0));
    }
    @Test void regularSequencePreservesOrderAndEqualAnimationDurations() {
        var c = config(); c.setDelay(1); c.getSequenceParameters().setGlobalDelay(0.5f);
        var l = PerBlockAnimationTiming.layout(c, 10, 20);
        assertEquals(11.5f, l.window(0).start());
        assertEquals(18, l.window(1).start());
        assertEquals(20, l.window(1).end());
        assertEquals(2, l.window(0.5f).end() - l.window(0.5f).start());
    }
    @Test void engineRespectsShortClipsAndDoesNotReverseCollapsedExitVisibility() {
        var c = config(); c.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
        c.setDelay(10);
        var e = new BlockSequenceEntry(BlockPos.ORIGIN, 1, 0, 0);
        assertTrue(BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 0, 1, 0.5f, 1).isVisible());
        assertFalse(BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 0, 1, 1, 1).isVisible());
    }
    @Test void engineAndTimingUseSameNormalizedProgressAndTimeSeekingIsDeterministic() {
        var c = config(); c.setDelay(0.5f);
        var e = new BlockSequenceEntry(BlockPos.ORIGIN, 0.75f, 0, 0);
        var w = PerBlockAnimationTiming.layout(c, 10, 12).window(e.getNormalizedTime());
        float mid = (w.start() + w.end()) / 2;
        var first = BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 10, 12, mid, 1);
        assertEquals(0.5f, first.getRawProgress(), 0.00001);
        BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 10, 12, 12, 1);
        assertEquals(first.getRawProgress(),
                BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 10, 12, mid, 1).getRawProgress());
    }
    @Test void instantEventsUseFullSequenceSpanAndClampExcessiveDelay() {
        var c = config(); c.setMode(AnimationMode.INSTANT);
        var l = PerBlockAnimationTiming.layout(c, 10, 12);
        assertEquals(11, l.window(0.5f).start());
        assertEquals(l.window(0.5f).start(), l.window(0.5f).end());
        c.setDelay(20);
        assertEquals(12, PerBlockAnimationTiming.layout(c, 10, 12).window(0).start());
    }
}
