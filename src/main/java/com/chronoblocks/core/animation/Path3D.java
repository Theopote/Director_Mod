package com.chronoblocks.core.animation;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D 路径定义 v4.5 - 用于路径动画
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>Catmull-Rom 样条插值</li>
 *   <li>自定义控制点</li>
 *   <li>切线计算（用于自动旋转）</li>
 *   <li>路径长度计算</li>
 *   <li>均匀采样</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * Path3D path = new Path3D();
 * path.addControlPoint(new Vec3d(0, 0, 0));
 * path.addControlPoint(new Vec3d(10, 5, 0));
 * path.addControlPoint(new Vec3d(20, 0, 0));
 * 
 * // 采样路径上的点
 * Vec3d position = path.sample(0.5f);  // 50% 位置
 * Vec3d tangent = path.getTangent(0.5f);  // 切线方向
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class Path3D {
    
    // ==================== 控制点 ====================
    
    /** 路径控制点列表 */
    private final List<Vec3d> controlPoints = new ArrayList<>();
    
    // ==================== 路径设置 ====================
    
    /** 插值类型 */
    private InterpolationType interpolationType = InterpolationType.BEZIER;
    
    /** 是否闭合路径 */
    private boolean closed = false;
    
    /** 路径张力（Catmull-Rom 参数，0 = 直线，1 = 圆滑） */
    private float tension = 0.5f;
    
    // ==================== 缓存 ====================
    
    /** 缓存的路径长度 */
    private float cachedLength = -1.0f;
    
    /** 缓存是否有效 */
    private boolean cacheValid = false;
    
    // ==================== 构造函数 ====================
    
    public Path3D() {
    }
    
    public Path3D(List<Vec3d> controlPoints) {
        if (controlPoints != null) {
            this.controlPoints.addAll(controlPoints);
            invalidateCache();
        }
    }
    
    // ==================== 控制点管理 ====================
    
    /**
     * 添加控制点
     */
    public void addControlPoint(Vec3d point) {
        if (point != null) {
            controlPoints.add(point);
            invalidateCache();
        }
    }
    
    /**
     * 插入控制点
     */
    public void insertControlPoint(int index, Vec3d point) {
        if (point != null && index >= 0 && index <= controlPoints.size()) {
            controlPoints.add(index, point);
            invalidateCache();
        }
    }
    
    /**
     * 移除控制点
     */
    public boolean removeControlPoint(int index) {
        if (index >= 0 && index < controlPoints.size()) {
            controlPoints.remove(index);
            invalidateCache();
            return true;
        }
        return false;
    }
    
    /**
     * 更新控制点
     */
    public void setControlPoint(int index, Vec3d point) {
        if (point != null && index >= 0 && index < controlPoints.size()) {
            controlPoints.set(index, point);
            invalidateCache();
        }
    }
    
    /**
     * 获取控制点
     */
    public Vec3d getControlPoint(int index) {
        if (index >= 0 && index < controlPoints.size()) {
            return controlPoints.get(index);
        }
        return Vec3d.ZERO;
    }
    
    /**
     * 获取所有控制点
     */
    public List<Vec3d> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }
    
    /**
     * 获取控制点数量
     */
    public int getControlPointCount() {
        return controlPoints.size();
    }
    
    /**
     * 清空所有控制点
     */
    public void clearControlPoints() {
        controlPoints.clear();
        invalidateCache();
    }
    
    // ==================== 路径采样 ====================
    
    /**
     * 在路径上采样点
     * 
     * @param t 进度 [0.0, 1.0]
     * @return 路径上的点
     */
    public Vec3d sample(float t) {
        if (controlPoints.isEmpty()) {
            return Vec3d.ZERO;
        }
        
        if (controlPoints.size() == 1) {
            return controlPoints.get(0);
        }
        
        t = clamp01(t);
        
        // 计算在哪个段上
        int segmentCount = closed ? controlPoints.size() : controlPoints.size() - 1;
        float segmentFloat = t * segmentCount;
        int segment = Math.min((int) segmentFloat, segmentCount - 1);
        float localT = segmentFloat - segment;
        
        // 获取四个控制点（Catmull-Rom 需要）
        Vec3d p0 = getPointSafe(segment - 1);
        Vec3d p1 = getPointSafe(segment);
        Vec3d p2 = getPointSafe(segment + 1);
        Vec3d p3 = getPointSafe(segment + 2);
        
        // 根据插值类型计算
        return switch (interpolationType) {
            case LINEAR -> lerpVec3(p1, p2, localT);
            case BEZIER, EASE_IN_OUT -> catmullRomInterpolate(p0, p1, p2, p3, localT);
            default -> catmullRomInterpolate(p0, p1, p2, p3, localT);
        };
    }
    
    /**
     * 获取路径上的切线（用于自动旋转）
     * 
     * @param t 进度 [0.0, 1.0]
     * @return 归一化的切线向量
     */
    public Vec3d getTangent(float t) {
        float delta = 0.001f;
        Vec3d p1 = sample(Math.max(0, t - delta));
        Vec3d p2 = sample(Math.min(1, t + delta));
        
        Vec3d tangent = p2.subtract(p1);
        double length = tangent.length();
        
        if (length < 1e-6) {
            return new Vec3d(1, 0, 0);  // 默认方向
        }
        
        return tangent.normalize();
    }
    
    /**
     * 计算路径总长度
     */
    public float calculateLength() {
        if (cacheValid) {
            return cachedLength;
        }
        
        if (controlPoints.size() < 2) {
            cachedLength = 0.0f;
            cacheValid = true;
            return 0.0f;
        }
        
        // 使用数值积分估算长度
        int samples = 100;
        float length = 0.0f;
        Vec3d prevPoint = sample(0);
        
        for (int i = 1; i <= samples; i++) {
            float t = i / (float) samples;
            Vec3d currentPoint = sample(t);
            length += (float) prevPoint.distanceTo(currentPoint);
            prevPoint = currentPoint;
        }
        
        cachedLength = length;
        cacheValid = true;
        
        return length;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 安全获取控制点（处理越界和闭合路径）
     */
    private Vec3d getPointSafe(int index) {
        if (controlPoints.isEmpty()) {
            return Vec3d.ZERO;
        }
        
        if (closed) {
            // 闭合路径：循环索引
            index = ((index % controlPoints.size()) + controlPoints.size()) % controlPoints.size();
        } else {
            // 开放路径：钳制到端点
            index = Math.max(0, Math.min(controlPoints.size() - 1, index));
        }
        
        return controlPoints.get(index);
    }
    
    /**
     * Catmull-Rom 样条插值
     */
    private Vec3d catmullRomInterpolate(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        
        // Catmull-Rom 矩阵
        float q0 = -tension * t3 + 2 * tension * t2 - tension * t;
        float q1 = (2 - tension) * t3 + (tension - 3) * t2 + 1;
        float q2 = (tension - 2) * t3 + (3 - 2 * tension) * t2 + tension * t;
        float q3 = tension * t3 - tension * t2;
        
        double x = q0 * p0.x + q1 * p1.x + q2 * p2.x + q3 * p3.x;
        double y = q0 * p0.y + q1 * p1.y + q2 * p2.y + q3 * p3.y;
        double z = q0 * p0.z + q1 * p1.z + q2 * p2.z + q3 * p3.z;
        
        return new Vec3d(x, y, z);
    }
    
    /**
     * 线性插值
     */
    private Vec3d lerpVec3(Vec3d a, Vec3d b, float t) {
        return new Vec3d(
            lerp(a.x, b.x, t),
            lerp(a.y, b.y, t),
            lerp(a.z, b.z, t)
        );
    }
    
    private double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
    
    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
    
    /**
     * 使缓存失效
     */
    private void invalidateCache() {
        cacheValid = false;
        cachedLength = -1.0f;
    }
    
    // ==================== Getter/Setter ====================
    
    public InterpolationType getInterpolationType() {
        return interpolationType;
    }
    
    public void setInterpolationType(InterpolationType type) {
        this.interpolationType = type != null ? type : InterpolationType.BEZIER;
        invalidateCache();
    }
    
    public boolean isClosed() {
        return closed;
    }
    
    public void setClosed(boolean closed) {
        this.closed = closed;
        invalidateCache();
    }
    
    public float getTension() {
        return tension;
    }
    
    public void setTension(float tension) {
        this.tension = Math.max(0f, Math.min(1f, tension));
        invalidateCache();
    }
    
    /**
     * 获取路径长度（使用缓存）
     */
    public float getLength() {
        return calculateLength();
    }
}

