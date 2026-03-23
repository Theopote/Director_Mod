package com.director.client.gizmo.handles;

import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.HitResult;
import com.director.client.gizmo.util.RaycastUtil;

/**
 * 🧭 CenterHandle - 中心手柄
 * 
 * 用于移动旋转中心点（Pivot）
 * 
 * @author Director Team
 * @version 1.0
 */
public class CenterHandle extends AbstractGizmoHandle {
    private final double radius;
    
    public CenterHandle(String id, Vec3d position, double radius) {
        super(id, position);
        this.radius = radius;
    }
    
    @Override
    public HitResult intersectRay(Vec3d rayOrigin, Vec3d rayDir) {
        // 使用球体检测
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
        
        // ✅ 使用与摄像机垂直的平面来计算当前拖拽位置
        // 1. 计算从 position 到摄像机的方向作为平面法线
        Vec3d planeNormal = rayOrigin.subtract(position).normalize();
        
        // 2. 计算平面常数 d (平面方程: nx*x + ny*y + nz*z = d)
        double planeD = planeNormal.dotProduct(position);
        
        // 3. 计算射线与平面的交点
        double denominator = planeNormal.dotProduct(rayDir);
        if (Math.abs(denominator) < 1e-6) {
            // 射线与平面平行，无法计算交点
            return Vec3d.ZERO;
        }
        
        double t = (planeD - planeNormal.dotProduct(rayOrigin)) / denominator;
        if (t < 0) {
            // 交点在射线背后
            return Vec3d.ZERO;
        }
        
        Vec3d currentHit = rayOrigin.add(rayDir.multiply(t));
        
        // 4. 计算增量
        Vec3d delta = currentHit.subtract(dragStartHitPoint);
        
        // 5. 更新拖拽起点（用于下一帧）
        dragStartHitPoint = currentHit;
        
        // 6. 如果 delta 太小，认为是点击而不是拖动
        if (delta.lengthSquared() < 0.01) {
            return Vec3d.ZERO;
        }
        
        return delta;
    }
}

