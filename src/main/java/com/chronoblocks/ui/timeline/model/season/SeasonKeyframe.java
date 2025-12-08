package com.chronoblocks.ui.timeline.model.season;

import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * SeasonKeyframe v1.1
 * 
 * <p>季节关键帧（Season Track 上的关键帧）。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>作为"长期气候趋势"的关键帧（与 Weather Track 的短期天气事件区分开）</li>
 *   <li>控制温度、湿度、雪线高度等季节性参数</li>
 *   <li>提供草地 / 树叶 / 天空 / 雾 / 光照颜色的整体色调偏移（Tint）</li>
 *   <li>支持多种插值方式（Linear / Smooth / Catmull-Rom）</li>
 *   <li>支持预设季节类型（春夏秋冬 + 自定义）</li>
 * </ul>
 * 
 * <p>与 Weather Track 的区别：
 * <ul>
 *   <li>季节轨道：控制长期趋势（渐变）</li>
 *   <li>天气轨道：控制短期事件（下雨、雷暴等）</li>
 *   <li>优先级：Weather Track > Season Track > Minecraft 原版</li>
 * </ul>
 * 
 * <p>注意：颜色偏移（tint）作为相对偏移值使用，渲染公式为：
 * <pre>
 * finalColor = originalColor * (1.0 + tint)
 * </pre>
 * <p>例如：tint = 0.1 表示增加 10%，tint = -0.1 表示减少 10%，tint = 0.0 表示保持原色。
 * 
 * @author ChronoBlocks Team
 * @version 1.1 - 改进文档、添加 Comparable、改进预设值
 */
public class SeasonKeyframe implements TimelineSelectable, Comparable<SeasonKeyframe> {
    
    /**
     * 季节类型
     */
    public enum SeasonType {
        DEFAULT("默认"),  // 默认（游戏原始状态）
        SPRING("春季"),
        SUMMER("夏季"),
        AUTUMN("秋季"),
        WINTER("冬季"),
        CUSTOM("自定义");
        
        private final String displayName;
        
        SeasonType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 插值类型（用于季节趋势插值）
     */
    public enum Interpolation {
        DEFAULT("默认"),      // 使用 SeasonTrack 默认插值类型
        LINEAR("线性"),       // 线性插值
        SMOOTH("平滑"),       // 缓入缓出（平滑插值）
        CATMULL_ROM("曲线 (Catmull-Rom)");  // Catmull-Rom 样条插值
        
        private final String displayName;
        
        Interpolation(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // 给 UI 用的 label 数组（在 adapter 中直接引用）
    public static final String[] SEASON_TYPE_LABELS = buildSeasonTypeLabels();
    public static final String[] INTERPOLATION_LABELS = buildInterpolationLabels();
    
    private static String[] buildSeasonTypeLabels() {
        SeasonType[] types = SeasonType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }
    
    private static String[] buildInterpolationLabels() {
        Interpolation[] types = Interpolation.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }
    
    private final UUID id;
    private String name;
    private float time;  // 时间轴上的位置（秒）
    
    // ========== 季节类型 ==========
    private SeasonType seasonType = SeasonType.DEFAULT;
    
    // ========== 气候趋势参数（非即时天气） ==========
    
    /**
     * 温度趋势 [-1.0, +1.0]，仅作为"抽象参数"提供给渲染/天气系统使用。
     * 例如：
     *  - 冬天 ≈ -0.8
     *  - 秋天 ≈ -0.2
     *  - 春天 ≈ +0.2
     *  - 夏天 ≈ +0.6
     */
    private float temperature = 0.0f;
    
    /**
     * 湿度趋势 [0.0, 1.0]。
     * 可以影响雾气、云量、天气概率等，但不直接触发下雨。
     */
    private float humidity = 0.5f;
    
    /**
     * 雪线高度（Y 坐标），高于该高度的方块可视上更"积雪 / 冰冷"。
     * 实际生效由渲染层或方块渲染 Mixin 使用。
     */
    private float snowlineHeight = 90.0f;
    
    /**
     * 雪量 [0.0, 1.0]，用于控制地表的积雪层数。
     * 0.0 = 无雪，1.0 = 最大积雪（7层雪）。
     */
    private float snowAmount = 0.0f;
    
    /**
     * 坡度影响因子 [0.0, 1.0]，控制陡坡上积雪的减少程度。
     * 0.0 = 坡度不影响积雪，1.0 = 陡坡完全不积雪。
     * v4.0 新增字段。
     */
    private float snowSlopeFactor = 0.5f;
    
    /**
     * 积雪融化速度 [0.1, 5.0]，用于控制积雪融化的快慢。
     * 1.0 = 正常速度，< 1.0 = 融化更慢，> 1.0 = 融化更快。
     * v4.0 新增字段。
     */
    private float snowMeltSpeed = 1.0f;
    
    /**
     * 冰冻强度 [0.0, 1.0]，控制水面冻结的程度和范围。
     * 0.0 = 不冻结，1.0 = 最大冻结强度。
     * v4.0 新增字段。
     */
    private float freezeStrength = 0.0f;
    
    /**
     * 冻结温度阈值 [-1.0, 1.0]，低于此温度开始冻结。
     * 默认 -0.2，表示温度低于 -0.2 时开始冻结水面。
     * v4.0 新增字段。
     */
    private float freezeThreshold = -0.2f;
    
    /**
     * 冰块融化速度 [0.1, 5.0]，用于控制冰块融化的快慢。
     * 1.0 = 正常速度，< 1.0 = 融化更慢，> 1.0 = 融化更快。
     * v4.0 新增字段。
     */
    private float freezeMeltSpeed = 1.0f;
    
    // ========== 颜色趋势（作为偏移值，使用公式：finalColor = originalColor * (1.0 + tint)） ==========
    
    /** 草地颜色偏移（0.0 = 无变化，0.1 = +10%，-0.1 = -10%） */
    private final Vector3f grassTint = new Vector3f(0.0f, 0.0f, 0.0f);
    
    /** 树叶颜色偏移（0.0 = 无变化，0.1 = +10%，-0.1 = -10%） */
    private final Vector3f foliageTint = new Vector3f(0.0f, 0.0f, 0.0f);
    
    /** 天空颜色偏移（0.0 = 无变化，0.1 = +10%，-0.1 = -10%） */
    private final Vector3f skyTint = new Vector3f(0.0f, 0.0f, 0.0f);
    
    /** 雾颜色偏移（0.0 = 无变化，0.1 = +10%，-0.1 = -10%） */
    private final Vector3f fogTint = new Vector3f(0.0f, 0.0f, 0.0f);
    
    /** 全局光照颜色偏移（0.0 = 无变化，0.1 = +10%，-0.1 = -10%） */
    private final Vector3f lightTint = new Vector3f(0.0f, 0.0f, 0.0f);
    
    // ========== 插值设置 ==========
    private Interpolation interpolation = Interpolation.SMOOTH;  // v4.0 默认改为平滑
    
    // ========== 构造函数 ==========
    
    public SeasonKeyframe(String name, float time) {
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
        return "Season @" + String.format("%.2fs", time);
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
        // 季节关键帧是一个时间点，没有持续时间
        return time;
    }

    @Override
    public String getType() {
        return "SeasonKeyframe";
    }
    
    // ========== Comparable 实现（按时间排序） ==========
    
    @Override
    public int compareTo(@NotNull SeasonKeyframe other) {
        int timeCompare = Float.compare(this.time, other.time);
        if (timeCompare != 0) {
            return timeCompare;
        }
        // 时间相同时，用 ID 作为次排序键，保证 TreeSet 等结构中不重复
        return this.id.compareTo(other.id);
    }
    
    // ========== 基础属性 Getter / Setter ==========
    
    /**
     * 获取关键帧时间（秒）
     */
    public float getTime() {
        return time;
    }
    
    /**
     * 设置关键帧时间（秒）
     */
    public void setTime(float time) {
        this.time = time;
    }
    
    public SeasonType getSeasonType() {
        return seasonType;
    }

    public float getTemperature() {
        return temperature;
    }
    
    /**
     * 设置温度趋势（建议范围 [-1, 1]，但不强制）。
     */
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
    
    public float getHumidity() {
        return humidity;
    }
    
    /**
     * 设置湿度趋势（建议范围 [0, 1]，但不强制）。
     */
    public void setHumidity(float humidity) {
        this.humidity = humidity;
    }
    
    public float getSnowlineHeight() {
        return snowlineHeight;
    }
    
    public void setSnowlineHeight(float snowlineHeight) {
        this.snowlineHeight = snowlineHeight;
    }
    
    public float getSnowAmount() {
        return snowAmount;
    }
    
    /**
     * 设置雪量 [0.0, 1.0]，用于控制地表的积雪层数。
     */
    public void setSnowAmount(float snowAmount) {
        this.snowAmount = Math.max(0.0f, Math.min(1.0f, snowAmount));
    }
    
    // ========== v4.0 新增：积雪控制参数 ==========
    
    public float getSnowSlopeFactor() {
        return snowSlopeFactor;
    }
    
    public void setSnowSlopeFactor(float snowSlopeFactor) {
        this.snowSlopeFactor = Math.max(0.0f, Math.min(1.0f, snowSlopeFactor));
    }
    
    public float getSnowMeltSpeed() {
        return snowMeltSpeed;
    }
    
    public void setSnowMeltSpeed(float snowMeltSpeed) {
        this.snowMeltSpeed = Math.max(0.1f, Math.min(5.0f, snowMeltSpeed));
    }
    
    // ========== v4.0 新增：冰冻控制参数 ==========
    
    public float getFreezeStrength() {
        return freezeStrength;
    }
    
    public void setFreezeStrength(float freezeStrength) {
        this.freezeStrength = Math.max(0.0f, Math.min(1.0f, freezeStrength));
    }
    
    public float getFreezeThreshold() {
        return freezeThreshold;
    }
    
    public void setFreezeThreshold(float freezeThreshold) {
        this.freezeThreshold = Math.max(-1.0f, Math.min(1.0f, freezeThreshold));
    }
    
    public float getFreezeMeltSpeed() {
        return freezeMeltSpeed;
    }
    
    public void setFreezeMeltSpeed(float freezeMeltSpeed) {
        this.freezeMeltSpeed = Math.max(0.1f, Math.min(5.0f, freezeMeltSpeed));
    }
    
    // ========== 颜色偏移 Getter（返回引用，注意复制） ==========
    
    public Vector3f getGrassTint() {
        return grassTint;
    }
    
    public void setGrassTint(Vector3f tint) {
        if (tint != null) {
            this.grassTint.set(tint);
        }
    }
    
    public Vector3f getFoliageTint() {
        return foliageTint;
    }
    
    public void setFoliageTint(Vector3f tint) {
        if (tint != null) {
            this.foliageTint.set(tint);
        }
    }
    
    public Vector3f getSkyTint() {
        return skyTint;
    }
    
    public void setSkyTint(Vector3f tint) {
        if (tint != null) {
            this.skyTint.set(tint);
        }
    }
    
    public Vector3f getFogTint() {
        return fogTint;
    }
    
    public void setFogTint(Vector3f tint) {
        if (tint != null) {
            this.fogTint.set(tint);
        }
    }
    
    public Vector3f getLightTint() {
        return lightTint;
    }
    
    public void setLightTint(Vector3f tint) {
        if (tint != null) {
            this.lightTint.set(tint);
        }
    }
    
    // ========== 插值设置 ==========
    
    public Interpolation getInterpolation() {
        return interpolation;
    }
    
    public void setInterpolation(Interpolation interpolation) {
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
    }
    
    // ========== 预设季节逻辑（可选，供 UI 使用） ==========
    
    /**
     * 根据 seasonType 应用一组合理的默认参数。
     * <p>注意：不会修改当前的 time / name，只修改气候和颜色相关字段。</p>
     */
    public void applySeasonPreset(SeasonType type) {
        this.seasonType = Objects.requireNonNull(type, "type");
        applySeasonDefaults();
    }
    
    /**
     * 根据季节类型应用默认值（内部方法，由 applySeasonPreset 调用）
     */
    private void applySeasonDefaults() {
        switch (seasonType) {
            case DEFAULT:
                // 默认：所有值设为游戏原始状态（无季节偏移）
                temperature = 0.0f;
                humidity = 0.5f;
                snowlineHeight = 200f;  // v4.0 默认值
                snowAmount = 0.0f;
                snowSlopeFactor = 0.6f;
                snowMeltSpeed = 1.0f;
                freezeStrength = 0.0f;
                freezeThreshold = -0.4f;  // v4.0 默认值
                freezeMeltSpeed = 1.0f;
                
                // 所有 tint 为 0（无变化）
                grassTint.set(0.0f, 0.0f, 0.0f);
                foliageTint.set(0.0f, 0.0f, 0.0f);
                skyTint.set(0.0f, 0.0f, 0.0f);
                fogTint.set(0.0f, 0.0f, 0.0f);
                lightTint.set(0.0f, 0.0f, 0.0f);
                break;
                
            case SPRING:
                temperature = 0.1f;  // v4.0 值
                humidity = 0.7f;
                snowlineHeight = 170f;
                snowAmount = 0.1f;
                snowSlopeFactor = 0.7f;
                snowMeltSpeed = 1.5f;
                freezeStrength = 0.0f;
                freezeThreshold = -0.3f;
                freezeMeltSpeed = 1.5f;
                
                // 春天：清新、明亮、偏绿色调
                grassTint.set(0.1f, 0.15f, 0.05f);
                foliageTint.set(0.1f, 0.1f, 0.0f);
                skyTint.set(0.05f, 0.05f, 0.0f);
                fogTint.set(0.0f, 0.05f, 0.0f);
                lightTint.set(0.05f, 0.05f, 0.0f);
                break;
                
            case SUMMER:
                temperature = 0.6f;
                humidity = 0.6f;
                snowlineHeight = 256f;
                snowAmount = 0.0f;
                snowSlopeFactor = 0.5f;
                snowMeltSpeed = 2.0f;
                freezeStrength = 0.0f;
                freezeThreshold = -0.2f;
                freezeMeltSpeed = 2.0f;
                
                // 夏天：深绿色、明亮、饱和
                grassTint.set(0.15f, 0.2f, 0.05f);
                foliageTint.set(0.15f, 0.2f, 0.05f);
                skyTint.set(0.0f, 0.05f, 0.1f);
                fogTint.set(0.0f, 0.0f, 0.0f);
                lightTint.set(0.1f, 0.08f, 0.0f);
                break;
                
            case AUTUMN:
                temperature = -0.1f;
                humidity = 0.5f;
                snowlineHeight = 220f;
                snowAmount = 0.0f;
                snowSlopeFactor = 0.6f;
                snowMeltSpeed = 1.2f;
                freezeStrength = 0.0f;
                freezeThreshold = -0.4f;
                freezeMeltSpeed = 1.2f;
                
                // 秋天：红/橙/黄色，暖色调
                grassTint.set(0.0f, -0.05f, -0.1f);
                foliageTint.set(0.1f, -0.05f, -0.15f);  // 偏黄偏橙
                skyTint.set(0.05f, 0.0f, -0.05f);
                fogTint.set(0.05f, 0.02f, 0.0f);
                lightTint.set(0.1f, 0.05f, -0.02f);
                break;
                
            case WINTER:
                temperature = -0.7f;  // v4.0 值
                humidity = 0.4f;
                snowlineHeight = 80f;
                snowAmount = 1.0f;
                snowSlopeFactor = 0.3f;
                snowMeltSpeed = 0.6f;
                freezeStrength = 0.8f;
                freezeThreshold = -0.2f;
                freezeMeltSpeed = 0.6f;
                
                // 冬天：冷色调、灰白色、偏蓝
                grassTint.set(-0.2f, -0.2f, -0.2f);
                foliageTint.set(-0.3f, -0.3f, -0.3f);
                skyTint.set(0.0f, 0.0f, 0.1f);
                fogTint.set(0.0f, 0.05f, 0.1f);
                lightTint.set(-0.05f, -0.05f, 0.05f);
                break;
                
            case CUSTOM:
                // 自定义模式：不强制修改当前参数，只更新 seasonType
                // 交给用户在属性面板上手动调整
                break;
        }
    }
    
    // ========== v4.0 新增：电影预设 ==========
    
    /**
     * 应用电影冷色调预设（整体偏蓝、低饱和度、清冷氛围）。
     */
    public void applyFilmPresetCold() {
        this.seasonType = SeasonType.CUSTOM;
        
        // 冷色调：偏蓝、降低饱和度
        skyTint.set(0.0f, 0.0f, 0.15f);      // 强烈偏蓝
        fogTint.set(0.0f, 0.05f, 0.15f);     // 冷色调雾
        lightTint.set(-0.05f, -0.05f, 0.1f); // 冷色调光照
    }
    
    /**
     * 应用电影暖色调预设（整体偏黄、暖光、金色氛围）。
     */
    public void applyFilmPresetWarm() {
        this.seasonType = SeasonType.CUSTOM;
        
        // 暖色调：偏黄、金色
        skyTint.set(0.1f, 0.05f, -0.05f);     // 暖色调天空
        fogTint.set(0.1f, 0.05f, -0.05f);     // 金色雾
        lightTint.set(0.15f, 0.1f, 0.0f);     // 暖色调光照（金色阳光）
    }
    
    // ========== 调试输出 ==========
    
    @Override
    public String toString() {
        return "SeasonKeyframe{" +
                "id=" + id +
                ", time=" + String.format("%.3f", time) +
                ", seasonType=" + seasonType +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", snowlineHeight=" + snowlineHeight +
                '}';
    }
}

