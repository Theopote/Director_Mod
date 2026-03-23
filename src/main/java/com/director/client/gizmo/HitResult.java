package com.director.client.gizmo;

import com.director.client.gizmo.handles.GizmoHandle;

/**
 * 🧭 HitResult - 射线检测结果
 * 
 * @author Director Team
 * @version 1.0
 */
public class HitResult {
    private final GizmoHandle handle;
    private final double distance;
    private final boolean hit;
    
    public HitResult(GizmoHandle handle, double distance) {
        this.handle = handle;
        this.distance = distance;
        this.hit = handle != null;
    }
    
    public static HitResult miss() {
        return new HitResult(null, Double.POSITIVE_INFINITY);
    }
    
    public GizmoHandle getHandle() {
        return handle;
    }
    
    public double getDistance() {
        return distance;
    }
    
    public boolean isHit() {
        return hit;
    }
}

