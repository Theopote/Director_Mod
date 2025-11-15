package com.chronoblocks.animation.timeline;

/**
 * 可见性关键帧
 * 
 * @author ChronoBlocks Team
 * @version 5.3
 */
public final class VisibilityKeyframe {
    public float time;
    public boolean visible;

    public VisibilityKeyframe(float time, boolean visible) {
        this.time = time;
        this.visible = visible;
    }
}

