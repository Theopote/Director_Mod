package com.director.lighting;
/** Geometry coordinate, independent of Minecraft world objects. */
public record LightPoint(double x, double y, double z) {
    public LightPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x)>30_000_000 || Math.abs(y)>30_000_000 || Math.abs(z)>30_000_000)
            throw new IllegalArgumentException("Invalid light coordinate");
    }
    public LightPoint add(double x,double y,double z) { return new LightPoint(this.x+x,this.y+y,this.z+z); }
}
