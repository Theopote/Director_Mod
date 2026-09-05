package com.director.ui.timeline.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRowLayoutTest {
    @Test void expandedChildrenShiftFollowingTracksWithoutBecomingTracks() {
        var layout = new TimelineRowLayout(30, 2, 20, 0, 7, 0);
        assertEquals(3, layout.trackCount());
        assertEquals(32, layout.trackTop(1));
        assertEquals(204, layout.trackTop(2));
        assertEquals(234, layout.totalHeight());
        assertEquals(1, layout.ownerAt(100));
        assertEquals(-1, layout.headerAt(100));
        assertEquals(2, layout.headerAt(204));
        assertArrayEquals(new int[]{1, 2}, layout.visibleRange(100, 120));
    }

    @Test void collapsedLayoutAndBoundaries() {
        var layout = new TimelineRowLayout(30, 2, 20, 0, 0);
        assertEquals(62, layout.totalHeight());
        assertEquals(-1, layout.headerAt(30));
        assertEquals(1, layout.headerAt(32));
        assertEquals(-1, layout.ownerAt(62));
        assertEquals(-1, layout.ownerAt(Float.NaN));
        assertArrayEquals(new int[]{0, 0}, layout.visibleRange(0, 32));
        assertArrayEquals(new int[]{-1, -1}, layout.visibleRange(62, 10));
        assertEquals(0, new TimelineRowLayout(30, 2, 20).totalHeight());
    }

    @Test void rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineRowLayout(0, 2, 20));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRowLayout(30, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRowLayout(30, 2, 20, -1));
    }
}
