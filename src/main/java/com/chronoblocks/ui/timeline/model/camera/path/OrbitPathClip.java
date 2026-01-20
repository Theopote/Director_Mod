package com.chronoblocks.ui.timeline.model.camera.path;

import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OrbitPathClip.class);
    
    /**
     * 高度模式枚举
     */
    public enum HeightMode {
        /** 保持当前高度（首次进入 clip 时缓存） */
        KEEP_CURRENT,
        /** 使用绝对高度值 */
        ABSOLUTE
    }
    
    private Vec3d center;       // 围绕中心
    private Vec3d lookAt;       // 注视点（通常 = center）
    
    private float radius;       // 半径（方块数）
    private HeightMode heightMode = HeightMode.ABSOLUTE;  // 高度模式
    private float height;       // 摄像机高度（Y，仅在 ABSOLUTE 模式下使用）
    private Float cachedHeight = null;  // 缓存的当前高度（KEEP_CURRENT 模式使用）
    
    private float startAngle;   // 起始角度（度）
    private float endAngle;     // 结束角度（度）
    
    private boolean clockwise = true;  // 是否顺时针
    
    /** ⭐ 是否在进入 PathClip 时自动对齐起始角度到当前摄像机位置 */
    private boolean alignStartToCurrentPosition = true;
    
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
        // ⭐ 如果是 KEEP_CURRENT 模式且尚未缓存高度，尝试从当前摄像机获取
        if (heightMode == HeightMode.KEEP_CURRENT && cachedHeight == null) {
            try {
                com.chronoblocks.ui.timeline.playback.camera.CameraRuntime runtime = 
                    com.chronoblocks.ui.timeline.playback.camera.CameraRuntime.getInstance();
                com.chronoblocks.ui.timeline.playback.camera.CameraSample currentSample = runtime.getCurrentSample();
                if (currentSample != null) {
                    cachedHeight = (float) currentSample.position().y;
                }
            } catch (Exception e) {
                // 如果获取失败，使用默认高度（center.y + 5）
                cachedHeight = (float) (center.y + 5.0);
            }
        }
        
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
        
        // ⭐ 根据高度模式决定 Y 坐标
        double y;
        if (heightMode == HeightMode.KEEP_CURRENT) {
            // 使用缓存的高度
            if (cachedHeight != null) {
                y = cachedHeight;
            } else {
                // 如果缓存失败，使用 center.y + 5 作为后备
                y = center.y + 5.0;
            }
        } else {
            // 使用绝对高度
            y = height;
        }
        
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
    
    public HeightMode getHeightMode() {
        return heightMode;
    }
    
    public void setHeightMode(HeightMode heightMode) {
        this.heightMode = heightMode != null ? heightMode : HeightMode.ABSOLUTE;
        // 切换到 KEEP_CURRENT 时，清除缓存（下次采样时会重新获取）
        if (this.heightMode == HeightMode.KEEP_CURRENT) {
            cachedHeight = null;
        }
    }
    
    public float getHeight() {
        return height;
    }
    
    public void setHeight(float height) {
        this.height = height;
    }
    
    /**
     * 获取当前缓存的高度（KEEP_CURRENT 模式使用）
     */
    public Float getCachedHeight() {
        return cachedHeight;
    }
    
    /**
     * 手动设置缓存高度（用于从 PropertyAdapter 设置）
     */
    public void setCachedHeight(Float cachedHeight) {
        this.cachedHeight = cachedHeight;
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
    
    /**
     * 是否在进入 PathClip 时自动对齐起始角度到当前摄像机位置
     */
    public boolean isAlignStartToCurrentPosition() {
        return alignStartToCurrentPosition;
    }
    
    /**
     * 设置是否在进入 PathClip 时自动对齐起始角度到当前摄像机位置
     */
    public void setAlignStartToCurrentPosition(boolean align) {
        this.alignStartToCurrentPosition = align;
    }
    
    /**
     * ⭐ 当时间轴"刚进入"该 OrbitPathClip 时调用
     * 
     * <p>如果启用了对齐功能，会根据当前摄像机位置自动计算起始角度，
     * 实现与前一段路径的无缝接续。
     * 
     * <p>对齐逻辑：
     * <ul>
     *   <li>从当前摄像机位置反推出在圆上的相位角</li>
     *   <li>将起始角度设置为该相位角</li>
     *   <li>保持扫过角度（sweep）不变，调整结束角度</li>
     * </ul>
     */
    @Override
    public void onEnter(CameraPathEnterContext ctx) {
        if (!alignStartToCurrentPosition) {
            return;
        }
        
        if (ctx == null || ctx.position == null) {
            return;
        }
        
        Vec3d pos = ctx.position;
        Vec3d c = center;
        
        // 计算当前位置在圆上的相位角
        double dx = pos.x - c.x;
        double dz = pos.z - c.z;
        
        // 防止半径为 0 或刚好在中心（无法确定角度）
        double distSq = dx * dx + dz * dz;
        if (distSq < 1e-6) {
            // 如果摄像机在中心点，无法确定角度，保持原值
            return;
        }
        
        // 将当前摄像机位置映射为 Orbit 起始角
        // 注意：atan2(dz, dx) 返回 [-π, π]，转换为 [0, 360)
        float angleDeg = (float) Math.toDegrees(Math.atan2(dz, dx));
        if (angleDeg < 0) {
            angleDeg += 360f;
        }
        
        // 保存当前的扫过角度（sweep），保持时长语义不变
        float currentSweep = endAngle - startAngle;
        if (currentSweep < 0) {
            currentSweep += 360f;
        }
        
        // 保存旧的角度值（用于调试日志）
        float oldStartAngle = startAngle;
        float oldEndAngle = endAngle;
        
        // ⭐ 特殊处理：如果是完整一圈（360°），保持完整一圈
        if (Math.abs(currentSweep - 360f) < 0.1f) {
            // 完整一圈，保持 360° 扫过角度
            setStartAngle(angleDeg);
            float newEndAngle = angleDeg + 360f;
            // 如果超过 360，归一化到 [0, 360] 范围（但保持 360° 作为有效值）
            if (newEndAngle > 360f) {
                newEndAngle = 360f;  // 保持 360° 作为结束角度
            }
            setEndAngle(newEndAngle);
        } else {
            // 部分扫过，保持扫过角度不变
            setStartAngle(angleDeg);
            float newEndAngle = angleDeg + currentSweep;
            // 如果超过 360，归一化到 [0, 360] 范围
            if (newEndAngle > 360f) {
                newEndAngle = newEndAngle % 360f;
            }
            setEndAngle(newEndAngle);
        }
        
        // ⭐ 调试日志：记录角度对齐
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[OrbitPathClip] onEnter() 对齐角度: startAngle {}° -> {}°, endAngle {}° -> {}°, sweep={}°", 
                oldStartAngle, startAngle, oldEndAngle, endAngle, currentSweep);
        }
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
        copy.setHeightMode(heightMode);
        copy.setHeight(height);
        if (cachedHeight != null) {
            copy.setCachedHeight(cachedHeight);
        }
        copy.setAlignStartToCurrentPosition(alignStartToCurrentPosition);
        copy.setEnabled(isEnabled());
        return copy;
    }
    
    // ========== 工具方法 ==========
    
    private static float lerp(float a, float b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return a + (b - a) * t;
    }
    
    /**
     * 将角度归一化到 [0, 360] 范围
     * 
     * <p>注意：允许 360° 作为有效值（完整一圈），而不是归一化为 0°
     */
    private static float normalizeAngle(float angle) {
        // ⭐ 特殊处理：360° 是有效值，不应该被归一化为 0°
        if (angle == 360f) {
            return 360f;
        }
        
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
