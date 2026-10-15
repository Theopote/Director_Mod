package com.director.client.gizmo;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.handles.GizmoHandle;

/**
 * 🧭 GizmoContext - Gizmo 上下文
 * 
 * 每个 Gizmo 拥有自己的上下文，包含目标 Box、轴心点、活动状态等信息
 * 
 * @author Director Team
 * @version 1.0
 */
public class GizmoContext {
    private GizmoType type;
    private GizmoMode mode = GizmoMode.TRANSLATE; // 默认平移模式
    private Box targetBox;
    private Vec3d pivot;
    private boolean isActive;
    private GizmoHandle activeHandle;
    private double snapStep = 0.0; // 0.0 表示不吸附
    
    public GizmoContext(GizmoType type, Box targetBox, Vec3d pivot) {
        this.type = type;
        this.targetBox = targetBox;
        this.pivot = pivot;
        this.isActive = false;
        this.activeHandle = null;
    }
    
    // ========== Getters & Setters ==========
    
    public GizmoType getType() {
        return type;
    }
    
    public void setType(GizmoType type) {
        this.type = type;
    }
    
    public Box getTargetBox() {
        return targetBox;
    }
    
    public void setTargetBox(Box targetBox) {
        this.targetBox = targetBox;
    }
    
    public Vec3d getPivot() {
        return pivot;
    }
    
    public void setPivot(Vec3d pivot) {
        this.pivot = pivot;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public GizmoHandle getActiveHandle() {
        return activeHandle;
    }
    
    public void setActiveHandle(GizmoHandle handle) {
        this.activeHandle = handle;
    }
    
    public double getSnapStep() {
        return snapStep;
    }
    
    public void setSnapStep(double snapStep) {
        this.snapStep = snapStep;
    }

    private final org.joml.Quaternionf orientation = new org.joml.Quaternionf();
    public org.joml.Quaternionf getOrientation() { return new org.joml.Quaternionf(orientation); }
    public void setOrientation(org.joml.Quaternionf value) {
        if (value == null || !value.isFinite() || value.lengthSquared()<1e-8f) orientation.identity();
        else orientation.set(value).normalize();
    }
    public Vec3d toHandleDirection(Vec3d world) {
        var v = new org.joml.Quaternionf(orientation).conjugate().transform(new org.joml.Vector3f((float)world.x,(float)world.y,(float)world.z));
        return new Vec3d(v.x,v.y,v.z);
    }
    public Vec3d toHandlePoint(Vec3d world) { return pivot.add(toHandleDirection(world.subtract(pivot))); }

    public GizmoMode getMode() {
        return mode;
    }

    public void setMode(GizmoMode mode) {
        this.mode = mode != null ? mode : GizmoMode.TRANSLATE;
    }
}

