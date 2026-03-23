package com.director.client.gizmo.handles;

import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.Axis;
import com.director.client.gizmo.HitResult;
import com.director.client.gizmo.util.RaycastUtil;

/**
 * 🧭 PlaneHandle - 平面手柄
 * 
 * 用于在平面上进行双向平移操作（如 XY 平面、XZ 平面、YZ 平面）
 * 
 * @author Director Team
 * @version 1.0
 */
public class PlaneHandle extends AbstractGizmoHandle {
    private final Axis axis1;
    private final Axis axis2;
    private final double size;
    private final Vec3d planeNormal;
    private final Vec3d planeAxis1;
    private final Vec3d planeAxis2;
    
    public PlaneHandle(String id, Vec3d position, Axis axis1, Axis axis2, double size) {
        super(id, position);
        this.axis1 = axis1;
        this.axis2 = axis2;
        this.size = size;
        
        // 确定平面法线和平面内的两个轴
        planeNormal = determineNormal(axis1, axis2);
        planeAxis1 = determineAxisVector(axis1);
        planeAxis2 = determineAxisVector(axis2);
    }
    
    @Override
    public HitResult intersectRay(Vec3d rayOrigin, Vec3d rayDir) {
        // 计算射线与平面的交点
        Vec3d intersection = RaycastUtil.rayToPlaneIntersection(rayOrigin, rayDir, position, planeNormal);
        if (intersection == null) {
            return HitResult.miss();
        }
        
        // 检查交点是否在平面范围内（方形平面）
        Vec3d localPos = intersection.subtract(position);
        double dot1 = localPos.dotProduct(planeAxis1);
        double dot2 = localPos.dotProduct(planeAxis2);
        
        double halfSize = size / 2.0;
        if (Math.abs(dot1) <= halfSize && Math.abs(dot2) <= halfSize) {
            double distance = rayOrigin.subtract(intersection).length();
            return new HitResult(this, distance);
        }
        
        return HitResult.miss();
    }
    
    @Override
    public Vec3d onDragUpdate(Vec3d rayOrigin, Vec3d rayDir) {
        if (!dragging || dragStartHitPoint == null) {
            return Vec3d.ZERO;
        }
        
        // 计算当前射线与平面的交点
        Vec3d currentHit = RaycastUtil.rayToPlaneIntersection(rayOrigin, rayDir, position, planeNormal);
        if (currentHit == null) {
            return Vec3d.ZERO;
        }
        
        // 计算增量（限制在平面内）
        Vec3d delta = currentHit.subtract(dragStartHitPoint);
        
        // 投影到平面上（移除法线方向的分量）
        double normalComponent = delta.dotProduct(planeNormal);
        Vec3d projectedDelta = delta.subtract(planeNormal.multiply(normalComponent));
        
        // 更新拖拽起点
        dragStartHitPoint = currentHit;
        
        return projectedDelta;
    }
    
    private Vec3d determineNormal(Axis a1, Axis a2) {
        // 计算垂直于平面的法线（即第三个轴的方向）
        if ((a1 == Axis.X && a2 == Axis.Y) || (a1 == Axis.Y && a2 == Axis.X)) {
            return new Vec3d(0, 0, 1); // Z 轴
        } else if ((a1 == Axis.X && a2 == Axis.Z) || (a1 == Axis.Z && a2 == Axis.X)) {
            return new Vec3d(0, 1, 0); // Y 轴
        } else {
            return new Vec3d(1, 0, 0); // X 轴
        }
    }
    
    private Vec3d determineAxisVector(Axis axis) {
        return switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
        };
    }
    
    public Axis getAxis1() {
        return axis1;
    }
    
    public Axis getAxis2() {
        return axis2;
    }
}

