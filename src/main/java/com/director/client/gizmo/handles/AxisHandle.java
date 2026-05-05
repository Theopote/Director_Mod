package com.director.client.gizmo.handles;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.Axis;
import com.director.client.gizmo.HitResult;
import com.director.client.gizmo.util.RaycastUtil;

/**
 * 🧭 AxisHandle - 轴手柄
 * 
 * 用于沿单个轴进行平移操作
 * 
 * @author Director Team
 * @version 1.0
 */
public class AxisHandle extends AbstractGizmoHandle {
    private final Axis axis;
    private final double length;
    private final double radius;
    private final Vec3d axisDirection;

    // 与 GizmoRenderer 的距离缩放保持一致（避免“看得见箭头但点不到/拖不动”）
    private static final double MIN_DISTANCE_MULTIPLIER = 8.0;
    private static final double DISTANCE_SCALE_DIVISOR = 15.0;

    // 与 GizmoRenderer 的可见箭头半径对齐（圆锥体底面半径）
    private static final double VISUAL_ARROW_RADIUS_BASE = 0.15;
    
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
        if (rayOrigin == null || rayDir == null) {
            return HitResult.miss();
        }

        Vec3d dir = signedAxisDirection(rayOrigin);
        double dm = distanceMultiplier(rayOrigin);
        // 覆盖渲染可见轴长度（GizmoRenderer 的 axisLength 也是按距离缩放）
        double effectiveLength = length * dm;
        // 覆盖圆锥体的“视觉半径”，并适当放大容错
        double dist = position.distanceTo(rayOrigin);
        double effectiveRadius = Math.max(
            radius * dm,
            Math.max(
                VISUAL_ARROW_RADIUS_BASE * dm,
                Math.min(0.8, Math.max(0.12, dist * 0.025))
            )
        );

        // 计算轴线的边界盒（简化为圆柱 → AABB）
        Vec3d start = position;
        Vec3d end = position.add(dir.multiply(effectiveLength));
        Box axisBox = new Box(
            Math.min(start.x, end.x) - effectiveRadius,
            Math.min(start.y, end.y) - effectiveRadius,
            Math.min(start.z, end.z) - effectiveRadius,
            Math.max(start.x, end.x) + effectiveRadius,
            Math.max(start.y, end.y) + effectiveRadius,
            Math.max(start.z, end.z) + effectiveRadius
        );
        
        double distance = RaycastUtil.rayToBoxDistance(rayOrigin, rayDir, axisBox);
        if (distance < Double.POSITIVE_INFINITY) {
            // 进一步检查是否在圆柱范围内（简化实现，使用盒检测）
            return new HitResult(this, distance);
        }
        
        return HitResult.miss();
    }
    
    @Override
    public void onDragStart(Vec3d rayOrigin, Vec3d rayDir) {
        this.dragging = true;
        this.dragStartRayOrigin = rayOrigin;
        this.dragStartRayDir = rayDir;
        // Use axis projection as the drag start point (not the AABB surface hit),
        // so the first onDragUpdate delta is measured from the correct axis position.
        Vec3d axisProjection = calculateAxisProjection(rayOrigin, rayDir);
        this.dragStartHitPoint = (axisProjection != null) ? axisProjection : position;
    }

    @Override
    public Vec3d onDragUpdate(Vec3d rayOrigin, Vec3d rayDir) {
        if (!dragging || dragStartHitPoint == null) {
            return Vec3d.ZERO;
        }
        if (rayOrigin == null || rayDir == null) {
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
        Vec3d dir = signedAxisDirection(rayOrigin);
        double deltaScalar = delta.dotProduct(dir);
        Vec3d projectedDelta = dir.multiply(deltaScalar);
        
        // 更新拖拽起点（用于下一次计算）
        // 将起点钉在轴线上，避免漂移
        double s = currentHit.subtract(position).dotProduct(dir);
        dragStartHitPoint = position.add(dir.multiply(s));
        
        return projectedDelta;
    }
    
    /**
     * 计算射线与轴线的最近点（投影）
     */
    private Vec3d calculateAxisProjection(Vec3d rayOrigin, Vec3d rayDir) {
        Vec3d axisDir = signedAxisDirection(rayOrigin).normalize();
        Vec3d rd = rayDir.normalize();

        // ✅ 计算“射线 vs 无限轴线”的最近点（稳定、可产生真实轴向位移）
        // Ray:  P(t) = rayOrigin + t * rd,   t >= 0
        // Axis: A(s) = position  + s * axisDir, s ∈ R
        Vec3d r = rayOrigin.subtract(position);
        double b = rd.dotProduct(axisDir);
        double denom = 1.0 - b * b; // 因为 rd·rd = axisDir·axisDir = 1

        if (denom > 1e-6) {
            double d = rd.dotProduct(r);
            double e = axisDir.dotProduct(r);

            // 最小二乘解（两条直线最近点的参数）
            double s = (e - b * d) / denom;
            return position.add(axisDir.multiply(s));
        }

        // ✅ 近似平行时回退：用“包含轴线、且尽量朝向相机”的平面做交点，再投影回轴线
        Vec3d n = rd.crossProduct(axisDir);
        if (n.lengthSquared() < 1e-10) {
            return dragStartHitPoint != null ? dragStartHitPoint : position;
        }

        // 这个法线保证平面包含 axisDir（因为 planeNormal ⟂ axisDir）
        Vec3d planeNormal = n.crossProduct(axisDir).normalize();
        Vec3d planePoint = dragStartHitPoint != null ? dragStartHitPoint : position;
        Vec3d intersection = RaycastUtil.rayToPlaneIntersection(rayOrigin, rd, planePoint, planeNormal);
        if (intersection == null) {
            return planePoint;
        }

        double s = intersection.subtract(position).dotProduct(axisDir);
        return position.add(axisDir.multiply(s));
    }

    private Vec3d signedAxisDirection(Vec3d cameraPos) {
        double diff = switch (axis) {
            case X -> cameraPos.x - position.x;
            case Y -> cameraPos.y - position.y;
            case Z -> cameraPos.z - position.z;
        };
        double sign = (Math.abs(diff) < 1e-6) ? 1.0 : (diff >= 0 ? 1.0 : -1.0);
        return axisDirection.multiply(sign);
    }

    private double distanceMultiplier(Vec3d cameraPos) {
        double d = position.distanceTo(cameraPos);
        if (d < MIN_DISTANCE_MULTIPLIER) {
            d = MIN_DISTANCE_MULTIPLIER;
        }
        return d / DISTANCE_SCALE_DIVISOR;
    }
    
    public Axis getAxis() {
        return axis;
    }
}

