package com.chronoblocks.ui.timeline.model.camera.path;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * OrbitPathClip - 围绕路径片段
 * 
 * <p>实现"围绕中心点旋转"的摄像机路径。
 * 用户指定中心点、半径、高度、起止角度等参数，系统自动生成围绕一圈的路径。
 * 
 * <p>参数说明：
 * <ul>
 *   <li>center: 围绕中心（世界坐标）</li>
 *   <li>lookAt: 注视点（通常 = center，也可偏移）</li>
 *   <li>radius: 半径（方块数量）</li>
 *   <li>height: 摄像机高度（Y 坐标）</li>
 *   <li>startAngle: 起始角度（度，0-360）</li>
 *   <li>endAngle: 结束角度（度，0-360）</li>
 *   <li>clockwise: 是否顺时针</li>
 * </ul>
 * 
 * <p>数学实现：
 * <pre>
 * angle = lerp(startAngle, endAngle, t)
 * x = center.x + cos(angle) * radius
 * z = center.z + sin(angle) * radius
 * y = height
 * </pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class OrbitPathClip extends CameraPathClip {
    
    private Vec3d center;       // 围绕中心
    private Vec3d lookAt;       // 注视点（通常 = center）
    
    private float radius;       // 半径（方块数）
    private float height;       // 摄像机高度（Y）
    
    private float startAngle;   // 起始角度（度）
    private float endAngle;     // 结束角度（度）
    
    private boolean clockwise = true;  // 是否顺时针
    
    /**
     * 构造函数
     * 
     * @param name 路径片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     * @param center 围绕中心（世界坐标）
     * @param radius 半径（方块数）
     * @param height 摄像机高度（Y 坐标）
     */
    public OrbitPathClip(String name, float startTime, float endTime,
                        Vec3d center, float radius, float height) {
        super(name, startTime, endTime);
        this.center = center != null ? center : Vec3d.ZERO;
        this.lookAt = this.center;  // 默认注视中心
        this.radius = Math.max(0.1f, radius);
        this.height = height;
        this.startAngle = 0f;
        this.endAngle = 360f;
    }
    
    @Override
    public CameraPathType getPathType() {
        return CameraPathType.ORBIT;
    }
    
    @Override
    public CameraPathSample sample(float timeSeconds) {
        if (!isActiveAt(timeSeconds)) {
            // 不在激活时间范围内，返回边界值
            if (timeSeconds < getStartTime()) {
                return sampleAtNormalizedTime(0f);
            } else {
                return sampleAtNormalizedTime(1f);
            }
        }
        
        float t = getNormalizedTime(timeSeconds);
        return sampleAtNormalizedTime(t);
    }
    
    /**
     * 在归一化时间 [0,1] 处采样
     */
    private CameraPathSample sampleAtNormalizedTime(float t) {
        // 角度插值
        float angle = lerp(startAngle, endAngle, t);
        
        // 顺时针/逆时针
        if (clockwise) {
            angle = -angle;  // 顺时针为负角度（数学上）
        }
        
        // 转换为弧度
        double rad = Math.toRadians(angle);
        
        // 计算位置
        double x = center.x + Math.cos(rad) * radius;
        double z = center.z + Math.sin(rad) * radius;
        double y = height;
        
        Vec3d position = new Vec3d(x, y, z);
        return new CameraPathSample(position, lookAt);
    }
    
    // ========== Getter / Setter ==========
    
    public Vec3d getCenter() {
        return center;
    }
    
    public void setCenter(Vec3d center) {
        this.center = center != null ? center : Vec3d.ZERO;
    }
    
    public Vec3d getLookAt() {
        return lookAt;
    }
    
    public void setLookAt(Vec3d lookAt) {
        this.lookAt = lookAt != null ? lookAt : this.center;
    }
    
    public float getRadius() {
        return radius;
    }
    
    public void setRadius(float radius) {
        this.radius = Math.max(0.1f, radius);
    }
    
    public float getHeight() {
        return height;
    }
    
    public void setHeight(float height) {
        this.height = height;
    }
    
    public float getStartAngle() {
        return startAngle;
    }
    
    public void setStartAngle(float startAngle) {
        this.startAngle = normalizeAngle(startAngle);
    }
    
    public float getEndAngle() {
        return endAngle;
    }
    
    public void setEndAngle(float endAngle) {
        this.endAngle = normalizeAngle(endAngle);
    }
    
    public boolean isClockwise() {
        return clockwise;
    }
    
    public void setClockwise(boolean clockwise) {
        this.clockwise = clockwise;
    }
    
    @Override
    public CameraPathClip copy() {
        OrbitPathClip copy = new OrbitPathClip(
                getName() + " (Copy)",
                getStartTime(),
                getEndTime(),
                center,
                radius,
                height
        );
        copy.setLookAt(lookAt);
        copy.setStartAngle(startAngle);
        copy.setEndAngle(endAngle);
        copy.setClockwise(clockwise);
        copy.setEnabled(isEnabled());
        return copy;
    }
    
    // ========== 工具方法 ==========
    
    private static float lerp(float a, float b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return a + (b - a) * t;
    }
    
    /**
     * 将角度归一化到 [0, 360) 范围
     */
    private static float normalizeAngle(float angle) {
        angle = angle % 360f;
        if (angle < 0) {
            angle += 360f;
        }
        return angle;
    }
    
    @Override
    public String toString() {
        return String.format("OrbitPathClip{name='%s', time=[%.2f, %.2f], center=(%.2f, %.2f, %.2f), radius=%.2f, height=%.2f, angle=[%.1f°, %.1f°], %s}",
                getName(), getStartTime(), getEndTime(),
                center.x, center.y, center.z,
                radius, height,
                startAngle, endAngle,
                clockwise ? "CW" : "CCW");
    }
}
