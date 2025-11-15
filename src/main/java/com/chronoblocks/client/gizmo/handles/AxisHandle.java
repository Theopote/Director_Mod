package com.chronoblocks.client.gizmo.handles;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.chronoblocks.client.gizmo.Axis;
import com.chronoblocks.client.gizmo.HitResult;
import com.chronoblocks.client.gizmo.util.RaycastUtil;

/**
 * 🧭 AxisHandle - 轴手柄
 * 
 * 用于沿单个轴进行平移操作
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class AxisHandle extends AbstractGizmoHandle {
    private final Axis axis;
    private final double length;
    private final double radius;
    private final Vec3d axisDirection;
    
    public AxisHandle(String id, Vec3d position, Axis axis, double length, double radius) {
        super(id, position);
        this.axis = axis;
        this.length = length;
        this.radius = radius;
        
        // 确定轴方向
        this.axisDirection = switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
        };
    }
    
    @Override
    public HitResult intersectRay(Vec3d rayOrigin, Vec3d rayDir) {
        // 计算轴线的边界盒（简化为圆柱）
        Vec3d start = position;
        Vec3d end = position.add(axisDirection.multiply(length));
        Box axisBox = new Box(
            Math.min(start.x, end.x) - radius,
            Math.min(start.y, end.y) - radius,
            Math.min(start.z, end.z) - radius,
            Math.max(start.x, end.x) + radius,
            Math.max(start.y, end.y) + radius,
            Math.max(start.z, end.z) + radius
        );
        
        double distance = RaycastUtil.rayToBoxDistance(rayOrigin, rayDir, axisBox);
        if (distance < Double.POSITIVE_INFINITY) {
            // 进一步检查是否在圆柱范围内（简化实现，使用盒检测）
            return new HitResult(this, distance);
        }
        
        return HitResult.miss();
    }
    
    @Override
    public Vec3d onDragUpdate(Vec3d rayOrigin, Vec3d rayDir) {
        if (!dragging || dragStartHitPoint == null) {
            return Vec3d.ZERO;
        }
        
        // 计算当前射线与轴线的最近点
        Vec3d currentHit = calculateAxisProjection(rayOrigin, rayDir);
        if (currentHit == null) {
            return Vec3d.ZERO;
        }
        
        // 计算增量（限制在轴方向上）
        Vec3d delta = currentHit.subtract(dragStartHitPoint);
        
        // 投影到轴上
        double deltaScalar = delta.dotProduct(axisDirection);
        Vec3d projectedDelta = axisDirection.multiply(deltaScalar);
        
        // 更新拖拽起点（用于下一次计算）
        dragStartHitPoint = currentHit;
        
        return projectedDelta;
    }
    
    /**
     * 计算射线与轴线的最近点（投影）
     */
    private Vec3d calculateAxisProjection(Vec3d rayOrigin, Vec3d rayDir) {
        Vec3d axisStart = position;
        Vec3d axisEnd = position.add(axisDirection.multiply(length));
        
        // 计算射线到轴线的最近点
        Vec3d axisVec = axisEnd.subtract(axisStart);
        Vec3d toRay = rayOrigin.subtract(axisStart);
        
        double axisLenSq = axisVec.lengthSquared();
        if (axisLenSq < 1e-6) {
            return axisStart;
        }
        
        double t = toRay.dotProduct(axisVec) / axisLenSq;
        t = Math.max(0, Math.min(1, t)); // 限制在轴线段内
        
        Vec3d axisPoint = axisStart.add(axisVec.multiply(t));
        
        // 计算射线与通过 axisPoint 且垂直于轴线的平面的交点
        Vec3d planeNormal = axisDirection;
        Vec3d planePoint = axisPoint;
        
        Vec3d intersection = RaycastUtil.rayToPlaneIntersection(rayOrigin, rayDir, planePoint, planeNormal);
        
        return intersection != null ? intersection : axisPoint;
    }
    
    public Axis getAxis() {
        return axis;
    }
}

