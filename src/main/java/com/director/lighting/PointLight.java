package com.director.lighting;

import java.util.Objects;
import java.util.UUID;

/** Immutable scene light; brightness uses Minecraft's 0..15 block-light scale. */
public record PointLight(UUID id, String name, double x, double y, double z,
                         float brightness, float range, float falloff, boolean enabled,
                         LightType type, java.util.List<LightPoint> anchors,
                         float width, float height, float yaw, float pitch) {
    /** Compatibility constructor for the original point-light scene format. */
    public PointLight(UUID id,String name,double x,double y,double z,float brightness,float range,float falloff,boolean enabled) {
        this(id,name,x,y,z,brightness,range,falloff,enabled,LightType.POINT,java.util.List.of(),2,2,0,0);
    }
    public PointLight {
        Objects.requireNonNull(id, "id");
        type = type==null ? LightType.POINT : type;
        anchors = anchors==null ? java.util.List.of() : java.util.List.copyOf(anchors);
        // Missing geometry fields in original point-light JSON use these defaults.
        width = width==0 ? 2 : width; height = height==0 ? 2 : height;
        if (!Float.isFinite(width) || width<.1f || width>64 || !Float.isFinite(height) || height<.1f || height>64
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Invalid area light geometry");
        if (type==LightType.LINE && (anchors.size()<2 || anchors.size()>128))
            throw new IllegalArgumentException("Line lights require 2..128 anchors");
        name = name == null || name.isBlank() ? "Point light" : name;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 30_000_000)
            throw new IllegalArgumentException("Invalid light position");
        if (!Float.isFinite(brightness) || brightness < 0 || brightness > 15
                || !Float.isFinite(range) || range < 1 || range > 32
                || !Float.isFinite(falloff) || falloff < .25f || falloff > 4)
            throw new IllegalArgumentException("Invalid light settings");
    }
    public static PointLight create(double x, double y, double z) {
        return new PointLight(UUID.randomUUID(), "Point light", x, y, z, 15, 8, 1, true);
    }
    public PointLight at(double x, double y, double z) {
        return new PointLight(id, name, x, y, z, brightness, range, falloff, enabled,type,anchors,width,height,yaw,pitch);
    }
    public PointLight settings(float brightness,float range,float falloff,boolean enabled) {
        return new PointLight(id,name,x,y,z,brightness,range,falloff,enabled,type,anchors,width,height,yaw,pitch);
    }
    public PointLight area(float width,float height,float yaw,float pitch) {
        return new PointLight(id,name,x,y,z,brightness,range,falloff,enabled,type,anchors,width,height,yaw,pitch);
    }
    public static PointLight createArea(double x,double y,double z,float yaw,float pitch) {
        return new PointLight(UUID.randomUUID(),"Area light",x,y,z,15,8,1,true,LightType.AREA,java.util.List.of(),2,2,yaw,pitch);
    }
    public static PointLight createLine(java.util.List<LightPoint> points) {
        var pivot=points.getFirst();
        var local=points.stream().map(p -> new LightPoint(p.x()-pivot.x(),p.y()-pivot.y(),p.z()-pivot.z())).toList();
        return new PointLight(UUID.randomUUID(),"Line light",pivot.x(),pivot.y(),pivot.z(),15,8,1,true,LightType.LINE,local,2,2,0,0);
    }
    public PointLight withAnchor(int index,LightPoint world) {
        var copy=new java.util.ArrayList<>(anchors);
        copy.set(index,new LightPoint(world.x()-x,world.y()-y,world.z()-z));
        return new PointLight(id,name,x,y,z,brightness,range,falloff,enabled,type,copy,width,height,yaw,pitch);
    }
}
