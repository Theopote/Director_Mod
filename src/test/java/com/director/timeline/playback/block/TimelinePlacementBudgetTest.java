package com.director.timeline.playback.block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelinePlacementBudgetTest {
    @BeforeEach void reset() {
        for (int i = 0; i < 1000; i++) TimelinePlacementBudget.beginFrame(false, false, false);
    }

    @Test void normalPlaybackDoesNotAccelerateWorldWrites() {
        TimelinePlacementBudget.beginFrame(false, false, false);
        assertFalse(TimelinePlacementBudget.isPlacementAccelerated());
        assertEquals(TimelinePlacementBudget.getPlaybackGlobalPerFrame(), TimelinePlacementBudget.getGlobalPerFrame());
    }

    @Test void seekingAndScrubbingUseTheirBudgets() {
        TimelinePlacementBudget.beginFrame(false, false, true);
        assertTrue(TimelinePlacementBudget.isPlacementAccelerated());
        assertEquals(TimelinePlacementBudget.getScrubGlobalPerFrame(), TimelinePlacementBudget.getGlobalPerFrame());
        TimelinePlacementBudget.beginFrame(true, false, false);
        assertTrue(TimelinePlacementBudget.isPlacementAccelerated());
    }

    @Test void releasingPlayheadUsesCatchUpBudgetEvenIfTimeChangedAndEventuallyReturnsToPlayback() {
        TimelinePlacementBudget.beginFrame(true, false, true);
        TimelinePlacementBudget.beginFrame(false, true, true);
        assertTrue(TimelinePlacementBudget.isCatchUp());
        assertEquals(TimelinePlacementBudget.getCatchUpGlobalPerFrame(), TimelinePlacementBudget.getGlobalPerFrame());
        assertEquals(TimelinePlacementBudget.getCatchUpPerContainerPerFrame(), TimelinePlacementBudget.getPerContainerPerFrame());
        reset();
        assertFalse(TimelinePlacementBudget.isPlacementAccelerated());
    }
}
