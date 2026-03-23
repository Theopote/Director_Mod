package com.director.client.gizmo.handles;

import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.HitResult;
import com.director.client.gizmo.util.RaycastUtil;

/**
 * 🧭 CornerHandle - 角点手柄
 * 
 * 用于从角点调整 Box 大小（保持对角点固定）
 * 
 * @author Director Team
 * @version 1.0
 */
public class CornerHandle extends AbstractGizmoHandle {
    private final double radius;
    private final net.minecraft.util.math.Vec3i cornerSign; // (1,1,1) 表示 max 角，(-1,-1,-1) 表示 min 角
    
    public CornerHandle(String id, Vec3d position, double radius, net.minecraft.util.math.Vec3i cornerSign) {
        super(id, position);
        this.radius = radius;
        this.cornerSign = cornerSign;
    }
    
    @Override
    public HitResult intersectRay(Vec3d rayOrigin, Vec3d rayDir) {
        // 使用球体检测（角点用小球表示）
        double distance = RaycastUtil.rayToSphereDistance(rayOrigin, rayDir, position, radius);
        if (distance < Double.POSITIVE_INFINITY) {
            return new HitResult(this, distance);
        }
        return HitResult.miss();
    }
    
    @Override
    public Vec3d onDragUpdate(Vec3d rayOrigin, Vec3d rayDir) {
        if (!dragging || dragStartHitPoint == null) {
            return Vec3d.ZERO;
        }
        
        // 计算当前射线与角点附近平面的交点
        // 简化实现：直接使用当前射线在角点位置的投影
        Vec3d currentHit = rayOrigin.add(rayDir.multiply(5.0)); // 使用固定距离（简化）
        
        // 计算增量
        Vec3d delta = currentHit.subtract(dragStartHitPoint);
        
        // 更新拖拽起点
        dragStartHitPoint = currentHit;
        
        return delta;
    }
    
    public net.minecraft.util.math.Vec3i getCornerSign() {
        return cornerSign;
    }
}

