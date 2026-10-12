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
    @Test void durationIsIndependentOfClipAndWindowsMayExtendBeyondIt() {
        var c = config(); var l = PerBlockAnimationTiming.layout(c, 10, 11);
        assertEquals(2, l.duration()); assertEquals(1, l.sequenceSpan());
        assertEquals(10.9f, l.window(0.9f).start(), 0.00001);
        assertEquals(12.9f, l.window(0.9f).end(), 0.00001);
    }
    @Test void delayShiftsStartsWithoutChangingCadenceOrActionDuration() {
        var c = config(); c.setDelay(1); c.getSequenceParameters().setGlobalDelay(0.5f);
        var l = PerBlockAnimationTiming.layout(c, 10, 12);
        assertEquals(11.5f, l.window(0).start());
        assertEquals(12.5f, l.window(0.5f).start());
        assertEquals(14.5f, l.window(0.5f).end());
    }
    @Test void excessiveDelayDoesNotCropOrCollapseTheAction() {
        var c = config(); c.setDelay(10); c.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
        var e = new BlockSequenceEntry(BlockPos.ORIGIN, 0, 0, 0);
        assertTrue(BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 0, 1, 5, 1).isVisible());
        assertEquals(0.5f, BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 0, 1, 11, 1).getRawProgress());
        assertFalse(BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 0, 1, 12, 1).isVisible());
    }
    @Test void resizingChangesOnlyStartOffsetsAndSeekingIsDeterministic() {
        var c = config(); var e = new BlockSequenceEntry(BlockPos.ORIGIN, 0.75f, 0, 0);
        var slow = PerBlockAnimationTiming.layout(c, 0, 4).window(e.getNormalizedTime());
        var fast = PerBlockAnimationTiming.layout(c, 0, 2).window(e.getNormalizedTime());
        assertEquals(3, slow.start()); assertEquals(1.5f, fast.start());
        assertEquals(2, slow.end()-slow.start()); assertEquals(2, fast.end()-fast.start());
        var a = BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 0, 2, 2.5f, 1);
        assertEquals(0.5f, a.getRawProgress());
        BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 0, 2, 5, 1);
        assertEquals(a.getRawProgress(), BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 0, 2, 2.5f, 1).getRawProgress());
    }
    @Test void instantUsesTheSameStartClockWithoutAnActionWindow() {
        var c = config(); c.setMode(AnimationMode.INSTANT);
        var w = PerBlockAnimationTiming.layout(c, 10, 12).window(0.5f);
        assertEquals(11, w.start()); assertEquals(w.start(), w.end());
    }
    @Test void everyModeKeepsTailProgressInBothDirections() {
        for (var mode : AnimationMode.values()) for (var phase : ClipAnimationConfig.AnimationPhase.values()) {
            var c = config(); c.setMode(mode); c.setPhase(phase);
            var e = new BlockSequenceEntry(BlockPos.ORIGIN, 0.75f, 0, 0);
            var w = PerBlockAnimationTiming.layout(c, 10, 11).window(e.getNormalizedTime());
            assertEquals(phase == ClipAnimationConfig.AnimationPhase.EXIT,
                    BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 10, 11, 9, 42).isVisible());
            assertEquals(phase != ClipAnimationConfig.AnimationPhase.EXIT,
                    BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 10, 11, w.end()+0.1f, 42).isVisible());
            if (mode != AnimationMode.INSTANT)
                assertEquals(0.5f, BlockAnimationEngine.sample(BlockPos.ORIGIN, null, c, e, 10, 11, w.start()+1, 42).getRawProgress(), 0.00001);
        }
    }
}
