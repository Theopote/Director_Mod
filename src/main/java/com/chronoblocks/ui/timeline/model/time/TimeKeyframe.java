package com.chronoblocks.ui.timeline.model.time;

import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;

import java.util.UUID;

/**
 * 时间关键帧
 * 
 * <p>表示时间线上的一个时间关键帧，包含 Minecraft 世界时间、太阳角度等信息。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>控制 Minecraft 世界时间（dayTime ticks）</li>
 *   <li>支持自定义太阳高度角和方位角</li>
 *   <li>支持自然时间模式和自定义太阳模式</li>
 *   <li>可以在时间线上选择和编辑</li>
 *   <li>属性面板可以显示时间相关属性</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimeKeyframe implements TimelineSelectable {
    
    /**
     * 段插值类型（用于世界时间插值）
     */
    public enum SegmentInterpolation {
        DEFAULT,      // 使用轨道默认类型
        LINEAR,       // 线性插值
        CATMULL_ROM,  // Catmull-Rom 样条插值
        BEZIER,       // 贝塞尔曲线插值
        STEP,         // 阶梯插值（保持值）
        HOLD          // 保持（等价 STEP）
    }
    
    /**
     * 角度插值类型（用于太阳角度插值）
     */
    public enum AngleInterpolation {
        DEFAULT,      // 跟随段插值类型
        LINEAR,       // 线性角度插值
        CATMULL_ROM,  // Catmull-Rom 角度插值（平滑过渡）
        BEZIER,       // 贝塞尔角度插值
        STEP          // 阶梯插值（保持角度）
    }
    
    private final UUID id;
    private String name;
    private float time;  // 时间轴上的位置（秒）
    
    // ========== 世界时间相关 ==========
    /** Minecraft 世界时间（tick，0~24000 为一个完整天，但可以超出） */
    private long worldTimeTicks = 6000L; // 默认正午
    
    /** 是否使用自然时间模式（true = 使用 worldTimeTicks，false = 使用自定义太阳角度） */
    private boolean useNaturalTime = true;
    
    // ========== 自定义太阳角度（仅在 useNaturalTime = false 时生效） ==========
    /** 太阳高度角（Elevation，0~90度，0=地平线，90=正头顶） */
    private float sunElevation = 45.0f;
    
    /** 太阳方位角（Azimuth，0~360度，0=正北，90=正东，180=正南，270=正西） */
    private float sunAzimuth = 180.0f; // 默认正南
    
    // ========== 插值设置 ==========
    /** 位置/时间插值类型 */
    private SegmentInterpolation pathInterpolation = SegmentInterpolation.DEFAULT;
    
    /** 角度插值类型 */
    private AngleInterpolation angleInterpolation = AngleInterpolation.DEFAULT;
    
    public TimeKeyframe(String name, float time) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.time = time;
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public String getName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return "Time @ " + String.format("%.2fs", time);
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public float getStartTime() {
        return time;
    }
    
    @Override
    public float getEndTime() {
        return time; // 时间关键帧没有持续时间，只是一个时间点
    }
    
    @Override
    public String getType() {
        return "TimeKeyframe";
    }
    
    // ========== Getters and Setters ==========
    
    public float getTime() {
        return time;
    }
    
    public void setTime(float time) {
        this.time = time;
    }
    
    public long getWorldTimeTicks() {
        return worldTimeTicks;
    }
    
    public void setWorldTimeTicks(long worldTimeTicks) {
        this.worldTimeTicks = Math.max(0L, worldTimeTicks);
    }
    
    public boolean isUseNaturalTime() {
        return useNaturalTime;
    }
    
    public void setUseNaturalTime(boolean useNaturalTime) {
        this.useNaturalTime = useNaturalTime;
    }
    
    /**
     * 获取太阳高度角（覆盖值，仅在 useNaturalTime = false 时生效）
     */
    public float getSunElevation() {
        return sunElevation;
    }
    
    /**
     * 设置太阳高度角（覆盖值，仅在 useNaturalTime = false 时生效）
     */
    public void setSunElevation(float sunElevation) {
        this.sunElevation = Math.max(0.0f, Math.min(90.0f, sunElevation));
    }
    
    /**
     * 获取太阳方位角（覆盖值，仅在 useNaturalTime = false 时生效）
     */
    public float getSunAzimuth() {
        return sunAzimuth;
    }
    
    /**
     * 设置太阳方位角（覆盖值，仅在 useNaturalTime = false 时生效）
     * 自动规范化到 0~360 度范围
     */
    public void setSunAzimuth(float sunAzimuth) {
        // 规范化到 0~360 度范围
        while (sunAzimuth < 0.0f) sunAzimuth += 360.0f;
        while (sunAzimuth >= 360.0f) sunAzimuth -= 360.0f;
        this.sunAzimuth = sunAzimuth;
    }
    
    public SegmentInterpolation getPathInterpolation() {
        return pathInterpolation;
    }
    
    public void setPathInterpolation(SegmentInterpolation pathInterpolation) {
        this.pathInterpolation = pathInterpolation != null ? pathInterpolation : SegmentInterpolation.DEFAULT;
    }
    
    public AngleInterpolation getAngleInterpolation() {
        return angleInterpolation;
    }
    
    public void setAngleInterpolation(AngleInterpolation angleInterpolation) {
        this.angleInterpolation = angleInterpolation != null ? angleInterpolation : AngleInterpolation.DEFAULT;
    }
    
    /**
     * 将 SegmentInterpolation 转换为 CameraKeyframe.SegmentPathType（用于复用插值逻辑）
     */
    public CameraKeyframe.SegmentPathType toSegmentPathType() {
        return switch (pathInterpolation) {
            case LINEAR -> CameraKeyframe.SegmentPathType.LINEAR;
            case CATMULL_ROM -> CameraKeyframe.SegmentPathType.CATMULL_ROM;
            case BEZIER -> CameraKeyframe.SegmentPathType.BEZIER;
            case STEP, HOLD -> CameraKeyframe.SegmentPathType.STEP;
            case DEFAULT -> CameraKeyframe.SegmentPathType.DEFAULT;
        };
    }
    
    /**
     * 将 AngleInterpolation 转换为 CameraKeyframe.RotationInterpolation（用于复用插值逻辑）
     */
    public CameraKeyframe.RotationInterpolation toRotationInterpolation() {
        return switch (angleInterpolation) {
            case LINEAR -> CameraKeyframe.RotationInterpolation.LINEAR;
            case CATMULL_ROM -> CameraKeyframe.RotationInterpolation.CATMULL_ROM;
            case BEZIER -> CameraKeyframe.RotationInterpolation.BEZIER;
            case STEP -> CameraKeyframe.RotationInterpolation.STEP;
            case DEFAULT -> CameraKeyframe.RotationInterpolation.DEFAULT;
        };
    }
}
