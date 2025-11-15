package com.chronoblocks.client.gizmo.handles;

import net.minecraft.util.math.Vec3d;
import com.chronoblocks.client.gizmo.Axis;
import com.chronoblocks.client.gizmo.util.RaycastUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎯 RotationHandle - 旋转手柄
 * 
 * 提供 Gizmo 旋转手柄的射线检测与角度计算逻辑。
 * 可用于动画目标旋转或角度可视化。
 * 
 * 工作原理：
 * - 定义一个旋转圆环（以中心点和法线轴为特征）
 * - 射线检测环所在平面
 * - 计算起始角与当前角的差值
 * 
 * 注意：Minecraft 方块不可旋转（除非方向方块或 NBT 控制），
 * 所以旋转 Gizmo 仅用于计算动画旋转目标（非即时方块旋转）。
 * 
 * @author ChronoBlocks
 * @version 1.0
 */
public class RotationHandle {
    private static final Logger LOGGER = LoggerFactory.getLogger(RotationHandle.class);
    
    private final Axis axis;          // 旋转轴
    private Vec3d center;             // 环中心
    private double radius;            // 环半径
    private boolean active;           // 是否被激活
    private double startAngle;        // 起始角（弧度）
    private double currentAngle;      // 当前角（弧度）
    
    public RotationHandle(Vec3d center, double radius, Axis axis) {
        this.center = center;
        this.radius = radius;
        this.axis = axis;
        this.active = false;
        this.startAngle = 0.0;
        this.currentAngle = 0.0;
    }
    
    /**
     * 检测射线是否命中旋转环
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     * @return 命中点的距离（如果命中），否则返回 Double.POSITIVE_INFINITY
     */
    public double intersectRay(Vec3d rayOrigin, Vec3d rayDir) {
        Vec3d normal = getAxisNormal();
        
        // 计算射线与旋转平面的交点
        Vec3d intersection = RaycastUtil.rayToPlaneIntersection(rayOrigin, rayDir, center, normal);
        if (intersection == null) {
            return Double.POSITIVE_INFINITY;
        }
        
        // 计算交点到中心的距离
        Vec3d toCenter = intersection.subtract(center);
        double dist = toCenter.length();
        
        // 检查是否在环的范围内（允许一定误差）
        double tolerance = 0.15; // 环的厚度容差
        if (Math.abs(dist - radius) < tolerance) {
            double distance = rayOrigin.subtract(intersection).length();
            LOGGER.debug("[RotationHandle] 命中旋转环 (轴: {}, 距离误差: {})", axis, Math.abs(dist - radius));
            return distance;
        }
        
        return Double.POSITIVE_INFINITY;
    }
    
    /**
     * 开始旋转
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向
     */
    public void beginRotation(Vec3d rayOrigin, Vec3d rayDir) {
        Vec3d normal = getAxisNormal();
        Vec3d intersection = RaycastUtil.rayToPlaneIntersection(rayOrigin, rayDir, center, normal);
        
        if (intersection != null) {
            this.active = true;
            this.startAngle = computeAngle(intersection);
            this.currentAngle = startAngle;
            LOGGER.debug("[RotationHandle] 开始旋转 (轴: {}, 起始角: {}°)", axis, Math.toDegrees(startAngle));
        }
    }
    
    /**
     * 更新旋转角度
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向
     */
    public void updateRotation(Vec3d rayOrigin, Vec3d rayDir) {
        if (!active) return;
        
        Vec3d normal = getAxisNormal();
        Vec3d intersection = RaycastUtil.rayToPlaneIntersection(rayOrigin, rayDir, center, normal);
        
        if (intersection != null) {
            this.currentAngle = computeAngle(intersection);
        }
    }
    
    /**
     * 返回当前旋转角度增量（弧度制）
     * 
     * @return 角度增量（弧度）
     */
    public double getDeltaAngle() {
        return currentAngle - startAngle;
    }
    
    /**
     * 返回当前旋转角度增量（度制）
     * 
     * @return 角度增量（度）
     */
    public double getDeltaAngleDegrees() {
        return Math.toDegrees(getDeltaAngle());
    }
    
    /**
     * 停止旋转
     */
    public void endRotation() {
        if (active) {
            LOGGER.debug("[RotationHandle] 结束旋转 (轴: {}, 总增量: {}°)", axis, getDeltaAngleDegrees());
        }
        this.active = false;
        this.startAngle = 0.0;
        this.currentAngle = 0.0;
    }
    
    /**
     * 计算点在旋转平面内的极角（atan2）
     */
    private double computeAngle(Vec3d hitPoint) {
        Vec3d local = hitPoint.subtract(center);
        
        // 根据旋转轴选择投影平面
        return switch (axis) {
            case X -> {
                // 绕 X 轴旋转：投影到 YZ 平面
                yield Math.atan2(local.z, local.y);
            }
            case Y -> {
                // 绕 Y 轴旋转：投影到 XZ 平面
                yield Math.atan2(local.x, local.z);
            }
            case Z -> {
                // 绕 Z 轴旋转：投影到 XY 平面
                yield Math.atan2(local.y, local.x);
            }
        };
    }
    
    /**
     * 获取轴的法线向量
     */
    private Vec3d getAxisNormal() {
        return switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
        };
    }
    
    // ========= Getters & Setters =========
    
    public Axis getAxis() {
        return axis;
    }
    
    public Vec3d getCenter() {
        return center;
    }
    
    public void setCenter(Vec3d center) {
        this.center = center;
    }
    
    public double getRadius() {
        return radius;
    }
    
    public void setRadius(double radius) {
        this.radius = radius;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public double getStartAngle() {
        return startAngle;
    }
    
    public double getCurrentAngle() {
        return currentAngle;
    }
}

