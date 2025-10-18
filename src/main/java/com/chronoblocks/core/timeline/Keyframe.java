package com.chronoblocks.core.timeline;

/**
 * Keyframe - Represents a time/value pair and interpolation mode.
 */
public class Keyframe {
    private double time; // seconds
    private double[] value; // generic numeric vector for transform/material parameters
    private Interpolation interpolation = Interpolation.LINEAR;

    public Keyframe(double time, double[] value) {
        this.time = time;
        this.value = value;
    }

    public double getTime() { return time; }
    public void setTime(double time) { this.time = time; }

    public double[] getValue() { return value; }
    public void setValue(double[] value) { this.value = value; }

    public Interpolation getInterpolation() { return interpolation; }
    public void setInterpolation(Interpolation interpolation) { this.interpolation = interpolation; }
}
