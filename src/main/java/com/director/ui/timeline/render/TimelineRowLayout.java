package com.director.ui.timeline.render;

import java.util.Arrays;

/** Immutable display layout. Child rows belong to a track, not to the persisted track list. */
public final class TimelineRowLayout {
    private final float[] offsets;
    private final float headerHeight;
    private final float gap;

    public TimelineRowLayout(float headerHeight, float gap, float childHeight, int... childCounts) {
        if (!Float.isFinite(headerHeight) || headerHeight <= 0 || !Float.isFinite(gap) || gap < 0
                || !Float.isFinite(childHeight) || childHeight <= 0) {
            throw new IllegalArgumentException("Invalid row dimensions");
        }
        this.headerHeight = headerHeight;
        this.gap = gap;
        offsets = new float[childCounts.length + 1];
        for (int i = 0; i < childCounts.length; i++) {
            if (childCounts[i] < 0) throw new IllegalArgumentException("Negative child count");
            offsets[i + 1] = offsets[i] + headerHeight + gap + childCounts[i] * childHeight;
            if (!Float.isFinite(offsets[i + 1])) throw new IllegalArgumentException("Layout overflow");
        }
    }

    public int trackCount() { return offsets.length - 1; }
    public float trackTop(int index) { return offsets[index]; }
    public float totalHeight() { return trackCount() == 0 ? 0 : offsets[trackCount()] - gap; }

    /** Includes child rows and gaps; callers doing clip/header hits must check header bounds. */
    public int ownerAt(float contentY) {
        if (!Float.isFinite(contentY) || contentY < 0 || contentY >= totalHeight()) return -1;
        int index = Arrays.binarySearch(offsets, contentY);
        return index >= 0 ? index : -index - 2;
    }

    public int headerAt(float contentY) {
        int owner = ownerAt(contentY);
        return owner >= 0 && contentY < offsets[owner] + headerHeight ? owner : -1;
    }

    public int[] visibleRange(float scrollY, float height) {
        if (!Float.isFinite(scrollY) || !Float.isFinite(height) || height <= 0
                || trackCount() == 0 || scrollY >= totalHeight() || scrollY + height <= 0) {
            return new int[]{-1, -1};
        }
        int first = ownerAt(Math.max(0, scrollY));
        float lastY = Math.nextDown(Math.min(totalHeight(), scrollY + height));
        return new int[]{first, ownerAt(lastY)};
    }
}
