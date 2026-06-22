package com.director.ui.timeline.interaction;

import com.director.timeline.snapping.TimelineSnapping;

/**
 * 吸附参考线状态，供 {@link TimelineRenderer} 绘制使用。
 */
public final class TimelineSnapGuideState {

    private boolean active;
    private float time = -1f;
    private boolean toGrid;
    private boolean toClip;
    private boolean toKeyframe;

    public boolean isActive() {
        return active;
    }

    public float getTime() {
        return time;
    }

    public boolean isToGrid() {
        return toGrid;
    }

    public boolean isToClip() {
        return toClip;
    }

    public boolean isToKeyframe() {
        return toKeyframe;
    }

    public void update(TimelineSnapping.Result snap) {
        if (snap == null) {
            clear();
            return;
        }
        boolean snapActive = snap.snappedToGrid || snap.snappedToClipEdge || snap.snappedToKeyframe;
        if (!snapActive) {
            clear();
            return;
        }
        active = true;
        time = snap.snappedTime;
        toGrid = snap.snappedToGrid;
        toClip = snap.snappedToClipEdge;
        toKeyframe = snap.snappedToKeyframe;
    }

    public void clear() {
        active = false;
        time = -1f;
        toGrid = false;
        toClip = false;
        toKeyframe = false;
    }
}
