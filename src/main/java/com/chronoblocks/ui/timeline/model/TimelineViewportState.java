package com.chronoblocks.ui.timeline.model;

/**
 * 时间线视口状态（ChronoBlocks）
 *
 * <p>负责存储时间线的滚动、缩放等纯 UI 状态。
 * 渲染器、控制栏、交互层、面板都应以此为真相源。
 */
public class TimelineViewportState {

    private float pixelsPerSecond = 80f;
    // 允许更小的缩放：用于查看很长的时间线（用户希望小于 5）
    private float minPixelsPerSecond = 1f;
    private float maxPixelsPerSecond = 800f;
    private float scrollX = 0f;
    private float scrollY = 0f;

    public float getPixelsPerSecond() {
        return pixelsPerSecond;
    }

    public void setPixelsPerSecond(float pixelsPerSecond) {
        this.pixelsPerSecond = clamp(pixelsPerSecond, minPixelsPerSecond, maxPixelsPerSecond);
    }

    public float getMinPixelsPerSecond() {
        return minPixelsPerSecond;
    }

    public void setMinPixelsPerSecond(float minPixelsPerSecond) {
        this.minPixelsPerSecond = Math.max(0.001f, minPixelsPerSecond);
        if (this.minPixelsPerSecond > maxPixelsPerSecond) {
            maxPixelsPerSecond = this.minPixelsPerSecond;
        }
        setPixelsPerSecond(pixelsPerSecond);
    }

    public float getMaxPixelsPerSecond() {
        return maxPixelsPerSecond;
    }

    public void setMaxPixelsPerSecond(float maxPixelsPerSecond) {
        this.maxPixelsPerSecond = Math.max(minPixelsPerSecond, maxPixelsPerSecond);
        setPixelsPerSecond(pixelsPerSecond);
    }

    public float getScrollX() {
        return scrollX;
    }

    public void setScrollX(float scrollX) {
        this.scrollX = Math.max(0f, scrollX);
    }

    public float getScrollY() {
        return scrollY;
    }

    public void setScrollY(float scrollY) {
        this.scrollY = Math.max(0f, scrollY);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

