package com.director.client.gizmo.handles;

import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.HitResult;

/**
 * 🧭 AbstractGizmoHandle - Gizmo 手柄抽象基类
 * 
 * @author Director Team
 * @version 1.0
 */
public abstract class AbstractGizmoHandle implements GizmoHandle {
    protected final String id;
    protected Vec3d position;
    protected boolean dragging = false;
    protected Vec3d dragStartRayOrigin;
    protected Vec3d dragStartRayDir;
    protected Vec3d dragStartHitPoint;
    
    public AbstractGizmoHandle(String id, Vec3d position) {
        this.id = id;
        this.position = position;
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public Vec3d getPosition() {
        return position;
    }
    
    public void setPosition(Vec3d position) {
        this.position = position;
    }
    
    @Override
    public void onDragStart(Vec3d rayOrigin, Vec3d rayDir) {
        this.dragging = true;
        this.dragStartRayOrigin = rayOrigin;
        this.dragStartRayDir = rayDir;
        HitResult hit = intersectRay(rayOrigin, rayDir);
        if (hit.isHit()) {
            this.dragStartHitPoint = rayOrigin.add(rayDir.multiply(hit.getDistance()));
        } else {
            this.dragStartHitPoint = position;
        }
    }
    
    @Override
    public void onDragEnd() {
        this.dragging = false;
        this.dragStartRayOrigin = null;
        this.dragStartRayDir = null;
        this.dragStartHitPoint = null;
    }
    
    @Override
    public boolean isDragging() {
        return dragging;
    }
}

