package com.director.timeline.playback.block;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerEffectCursorTest {
    @Test void forwardCrossingFiresOnceEvenAfterAFrameSkip() {
        var c = new WholeContainerEffectCursor();
        c.beginFrame(0, false, false); c.beginFrame(3, true, false);
        assertTrue(c.crosses(2));
        c.beginFrame(3, true, false); assertFalse(c.crosses(2));
        c.beginFrame(4, true, false); assertFalse(c.crosses(2));
    }
    @Test void startingAtDepartureBoundaryIsIncluded() {
        var c = new WholeContainerEffectCursor();
        c.beginFrame(0, false, false); c.beginFrame(0.1f, true, false);
        assertTrue(c.crosses(0));
        c.beginFrame(0.2f, true, false); assertFalse(c.crosses(0));
    }
    @Test void pausedSeekingAndScrubbingNeverEmit() {
        var c = new WholeContainerEffectCursor();
        c.beginFrame(0, false, false); c.beginFrame(3, false, false);
        assertFalse(c.crosses(2));
        c.beginFrame(0, true, true); c.beginFrame(3, true, true);
        assertFalse(c.crosses(2));
        c.beginFrame(4, true, false); assertFalse(c.crosses(3.5f));
        c.beginFrame(5, true, false); assertTrue(c.crosses(4.5f));
    }
    @Test void finalStoppedFrameCanCrossTheEndButPausedEditsCannotReplayIt() {
        var c = new WholeContainerEffectCursor();
        c.beginFrame(1, true, false); c.beginFrame(2, false, false);
        assertTrue(c.crosses(2));
        c.beginFrame(3, false, false); assertFalse(c.crosses(2.5f));
    }
    @Test void loopingAndReplayingRearmWhileModelResetDoesNotCatchUpOldEvents() {
        var c = new WholeContainerEffectCursor();
        c.beginFrame(0, true, false); c.beginFrame(3, true, false); assertTrue(c.crosses(2));
        c.beginFrame(0, true, false); assertFalse(c.crosses(2));
        c.beginFrame(3, true, false); assertTrue(c.crosses(2));
        c.reset(); c.beginFrame(4, true, false); assertFalse(c.crosses(2));
    }
    @Test void firstPlayingFrameAtTheSameTimeStillIncludesStartBoundaryOnNextAdvance() {
        var c = new WholeContainerEffectCursor();
        c.beginFrame(0, false, false);
        c.beginFrame(0, true, false); assertFalse(c.crosses(0));
        c.beginFrame(0.1f, true, false); assertTrue(c.crosses(0));
        c.beginFrame(0.2f, true, false); assertFalse(c.crosses(0));
    }
    @Test void loopingBackToZeroIncludesZeroTimeEffectsOnNextAdvance() {
        var c = new WholeContainerEffectCursor();
        c.beginFrame(0, false, false); c.beginFrame(1, true, false);
        assertTrue(c.crosses(0));
        c.beginFrame(0, true, false); assertFalse(c.crosses(0));
        c.beginFrame(0, true, false); assertFalse(c.crosses(0));
        c.beginFrame(0.1f, true, false); assertTrue(c.crosses(0));
        c.beginFrame(0.2f, true, false); assertFalse(c.crosses(0));
    }
    @Test void rewindingAndScrubbingClearOldWavesButPausingDoesNotReplayOrClearThem() {
        var c = new WholeContainerEffectCursor();
        c.beginFrame(0, false, false); assertFalse(c.shouldClearEffects(false));
        c.beginFrame(2, true, false); assertFalse(c.shouldClearEffects(false));
        c.beginFrame(2, false, false); assertFalse(c.shouldClearEffects(false));
        c.beginFrame(1, false, false); assertTrue(c.shouldClearEffects(false));
        c.beginFrame(3, false, true); assertTrue(c.shouldClearEffects(true));
        c.reset(); c.beginFrame(0, true, false); assertFalse(c.shouldClearEffects(false));
    }
}
