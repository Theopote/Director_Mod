package com.chronoblocks.client.gizmo.handles;

import net.minecraft.util.math.Vec3d;
import com.chronoblocks.client.gizmo.Axis;
import com.chronoblocks.client.gizmo.HitResult;
import com.chronoblocks.client.gizmo.util.RaycastUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🧱 ResizeHandle - 缩放手柄
 * 
 * 选区 Gizmo 的"缩放手柄"，位于选区的面或角上。
 * 用户通过鼠标拖拽控制选区边界扩展或收缩。
 * 
 * ✅ 支持：
 *  - 面手柄：沿单一 Axis 调整大小
 *  - 角点手柄：同时沿三个方向缩放
 *  - 射线检测 + 距离投影
 * 
 * @author ChronoBlocks
 * @version 1.0
 */
public class ResizeHandle extends AbstractGizmoHandle {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResizeHandle.class);
    
    /** 手柄类型 */
    public enum Type {
        /** 面手柄（沿单一轴缩放） */
        FACE,
        /** 角点手柄（沿三个轴缩放） */
        CORNER
    }
    
    private final Type type;
    private final Axis[] axes;       // 手柄控制的轴（面=1个轴，角点=3个轴）
    private final double radius;     // 可视半径（用于检测）
    
    public ResizeHandle(String id, Type type, Vec3d position, double radius, Axis... axes) {
        super(id, position);
        this.type = type;
        this.radius = radius;
        this.axes = axes != null && axes.length > 0 ? axes : new Axis[0];
    }
    
    @Override
    public HitResult intersectRay(Vec3d rayOrigin, Vec3d rayDir) {
        // 使用球体检测（粗略检测）
        double distance = RaycastUtil.rayToSphereDistance(rayOrigin, rayDir, position, radius);
        if (distance < Double.POSITIVE_INFINITY) {
            LOGGER.debug("[ResizeHandle] 命中 {} 手柄 (dist={})", type, distance);
            return new HitResult(this, distance);
        }
        return HitResult.miss();
    }
    
    @Override
    public void onDragStart(Vec3d rayOrigin, Vec3d rayDir) {
        super.onDragStart(rayOrigin, rayDir);
        LOGGER.debug("[ResizeHandle] 开始拖拽 {} 手柄 at {}", type, dragStartHitPoint != null ? dragStartHitPoint : position);
    }
    
    @Override
    public Vec3d onDragUpdate(Vec3d rayOrigin, Vec3d rayDir) {
        if (!dragging || dragStartHitPoint == null) {
            return Vec3d.ZERO;
        }
        
        // 计算当前命中点（使用平面投影）
        Vec3d currentHit = calculateDragPoint(rayOrigin, rayDir);
        if (currentHit == null) {
            currentHit = rayOrigin.add(rayDir.multiply(5.0)); // 回退：固定距离
        }
        
        // 计算增量
        Vec3d delta = currentHit.subtract(dragStartHitPoint);
        
        // 根据手柄类型限制增量方向
        Vec3d axisDelta = getAxisDelta(delta);
        
        // 更新拖拽起点（用于下一次计算）
        dragStartHitPoint = currentHit;
        
        return axisDelta;
    }
    
    /**
     * 计算拖拽点（根据手柄类型）
     */
    private Vec3d calculateDragPoint(Vec3d rayOrigin, Vec3d rayDir) {
        if (type == Type.FACE && axes.length > 0) {
            // 面手柄：投影到垂直于轴的平面
            Vec3d axisVec = getAxisVector(axes[0]);
            Vec3d planePoint = position;
            Vec3d planeNormal = axisVec;
            
            return RaycastUtil.rayToPlaneIntersection(rayOrigin, rayDir, planePoint, planeNormal);
        } else {
            // 角点手柄：使用最近的轴方向投影
            // 简化实现：使用固定距离投影
            return null; // 返回 null 触发回退
        }
    }
    
    /**
     * 返回沿轴方向的缩放值（用于传递给 TransformApplier）
     */
    public Vec3d getAxisDelta(Vec3d worldDelta) {
        double dx = 0, dy = 0, dz = 0;
        
        if (axes != null && axes.length > 0) {
            for (Axis a : axes) {
                switch (a) {
                    case X -> dx = worldDelta.x;
                    case Y -> dy = worldDelta.y;
                    case Z -> dz = worldDelta.z;
                }
            }
        } else {
            // 如果没有指定轴，使用完整增量（角点模式）
            dx = worldDelta.x;
            dy = worldDelta.y;
            dz = worldDelta.z;
        }
        
        return new Vec3d(dx, dy, dz);
    }
    
    /**
     * 获取轴向量
     */
    private Vec3d getAxisVector(Axis axis) {
        return switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
        };
    }
    
    // ========== Getters & Setters ==========
    
    public Type getHandleType() {
        return type;
    }
    
    public Axis[] getAxes() {
        return axes != null ? axes.clone() : new Axis[0];
    }
    
    public double getRadius() {
        return radius;
    }
    
    @Override
    public String toString() {
        return String.format("ResizeHandle[type=%s, axes=%d, pos=%s]", 
            type, axes != null ? axes.length : 0, position);
    }
}

