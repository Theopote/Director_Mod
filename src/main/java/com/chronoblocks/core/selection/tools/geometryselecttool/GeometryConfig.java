package com.chronoblocks.core.selection.tools.geometryselecttool;

/**
 * ⚙️ 几何选区生成参数。
 */
public final class GeometryConfig {

    private double radius = 4.0;
    private double height = 8.0;
    private boolean hollow = false;
    private int segments = 16;

    public GeometryConfig() {}

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = clamp(radius, 0.5, 128.0);
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = clamp(height, 1.0, 256.0);
    }

    public boolean isHollow() {
        return hollow;
    }

    public void setHollow(boolean hollow) {
        this.hollow = hollow;
    }

    public int getSegments() {
        return segments;
    }

    public void setSegments(int segments) {
        this.segments = (int) clamp(segments, 3, 64);
    }

    public GeometryConfig copy() {
        GeometryConfig cfg = new GeometryConfig();
        cfg.radius = this.radius;
        cfg.height = this.height;
        cfg.hollow = this.hollow;
        cfg.segments = this.segments;
        return cfg;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

