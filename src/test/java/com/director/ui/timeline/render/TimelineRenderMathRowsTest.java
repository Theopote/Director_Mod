package com.director.ui.timeline.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRenderMathRowsTest {
    @Test void renderingAndHitsShareExpandedOffsetsAfterScrolling() {
        var viewport = new TimelineRenderViewport();
        viewport.setTrackHeight(30);
        viewport.setTrackGap(2);
        viewport.update(0, 0, 800, 120);
        viewport.setScrollY(100);
        var math = new TimelineRenderMath(viewport);
        math.setRowLayout(new TimelineRowLayout(30, 2, 20, 0, 7, 0));
        assertEquals(204, math.trackContentY(2));
        assertEquals(104, math.trackIndexToY(2));
        assertEquals(2, math.yToTrackIndex(104));
        assertEquals(-1, math.yToTrackIndex(0), "child rows must not accept track/clip hits");
        assertArrayEquals(new int[]{1, 2}, math.getVisibleTrackRange(3));
    }

    @Test void collapseReplacesLayoutAndRejectsOutsideHits() {
        var viewport = new TimelineRenderViewport();
        viewport.update(0, 0, 800, 120);
        var math = new TimelineRenderMath(viewport);
        math.setRowLayout(new TimelineRowLayout(30, 2, 20, 7, 0));
        assertEquals(172, math.trackContentY(1));
        math.setRowLayout(new TimelineRowLayout(30, 2, 20, 0, 0));
        assertEquals(32, math.trackContentY(1));
        assertEquals(-1, math.headerAtContentY(100));
        assertEquals(-1, math.headerAtContentY(-1));
        viewport.setScrollY(100);
        assertArrayEquals(new int[]{-1, -1}, math.getVisibleTrackRange(2));
    }
}
