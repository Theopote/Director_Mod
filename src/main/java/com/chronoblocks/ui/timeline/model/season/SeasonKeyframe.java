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
 *   <li>控制积雪覆盖度、积雪厚度、冰覆盖度等季节性参数</li>
 *   <li>提供草地 / 树叶 / 天空 / 雾 / 光照颜色的整体色调偏移（Tint）</li>
 *   <li>支持多种插值方式（Linear / Smooth / Catmull-Rom）实现季节过渡动画</li>
 *   <li>支持预设季节类型（春夏秋冬 + 自定义）</li>
 * </ul>
 * 
 * <p>季节过渡机制：
 * <ul>
 *   <li>关键帧之间的参数（积雪覆盖度、积雪厚度、冰覆盖度等）会自动插值</li>
 *   <li>例如：从秋季（无积雪/无冰）到冬季（有积雪/有冰）时，会逐渐过渡</li>
 *   <li>从冬季到春季时，积雪和冰会逐渐消失</li>
 *   <li>过渡速度由关键帧之间的时间距离和插值方式决定</li>
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
     * 雪覆盖度 [0.0, 1.0]，表示覆盖的概率。
     * 0.0 = 没有覆盖，1.0 = 几乎全覆盖。
     */
    private float snowAmount = 0.80f;
    
    /**
     * 积雪厚度 [0, 10]，用于控制积雪的厚度（层数）。
     * 0 = 无厚度，10 = 最大厚度（8层雪+2层）。
     * 季节过渡通过关键帧之间的插值实现，无需单独的融化速度参数。
     */
    private int snowThickness = 0;
    
    /**
     * 冰覆盖度 [0.0, 1.0]，表示覆盖的概率。
     * 0.0 = 没有覆盖，1.0 = 几乎全覆盖。
     * 季节过渡通过关键帧之间的插值实现，无需单独的融化速度参数。
     */
    private float freezeStrength = 0.80f;
    
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

    public float getHumidity() {
        return humidity;
    }

    public void setHumidity(float humidity) {
        this.humidity = Math.max(0.0f, Math.min(1.0f, humidity));
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
     * 设置雪覆盖度 [0.0, 1.0]，表示覆盖的概率。
     */
    public void setSnowAmount(float snowAmount) {
        this.snowAmount = Math.max(0.0f, Math.min(1.0f, snowAmount));
    }
    
    // ========== v4.0 新增：积雪控制参数 ==========
    
    public int getSnowThickness() {
        return snowThickness;
    }
    
    public void setSnowThickness(int snowThickness) {
        this.snowThickness = Math.max(0, Math.min(10, snowThickness));
    }
    
    // ========== v4.0 新增：冰冻控制参数 ==========
    
    public float getFreezeStrength() {
        return freezeStrength;
    }
    
    /**
     * 设置冰覆盖度 [0.0, 1.0]，表示覆盖的概率。
     */
    public void setFreezeStrength(float freezeStrength) {
        this.freezeStrength = Math.max(0.0f, Math.min(1.0f, freezeStrength));
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
                humidity = 0.5f;
                snowlineHeight = 200f;  // v4.0 默认值
                snowAmount = 0.80f;
                snowThickness = 0;
                freezeStrength = 0.80f;
                
                // 所有 tint 为 0（无变化）
                grassTint.set(0.0f, 0.0f, 0.0f);
                foliageTint.set(0.0f, 0.0f, 0.0f);
                skyTint.set(0.0f, 0.0f, 0.0f);
                fogTint.set(0.0f, 0.0f, 0.0f);
                lightTint.set(0.0f, 0.0f, 0.0f);
                break;
                
            case SPRING:
                humidity = 0.7f;
                snowlineHeight = 170f;
                snowAmount = 0.0f;  // 春季默认无积雪
                snowThickness = 0;
                freezeStrength = 0.0f;  // 春季默认无冰冻
                
                // 春天：清新、明亮、偏绿色调
                grassTint.set(0.1f, 0.15f, 0.05f);
                foliageTint.set(0.1f, 0.1f, 0.0f);
                skyTint.set(0.05f, 0.05f, 0.0f);
                fogTint.set(0.0f, 0.05f, 0.0f);
                lightTint.set(0.05f, 0.05f, 0.0f);
                break;
                
            case SUMMER:
                humidity = 0.6f;
                snowlineHeight = 256f;
                snowAmount = 0.0f;
                snowThickness = 0;
                freezeStrength = 0.0f;
                
                // 夏天：深绿色、明亮、饱和
                grassTint.set(0.15f, 0.2f, 0.05f);
                foliageTint.set(0.15f, 0.2f, 0.05f);
                skyTint.set(0.0f, 0.05f, 0.1f);
                fogTint.set(0.0f, 0.0f, 0.0f);
                lightTint.set(0.1f, 0.08f, 0.0f);
                break;
                
            case AUTUMN:
                humidity = 0.5f;
                snowlineHeight = 220f;
                snowAmount = 0.0f;  // 秋季默认无积雪
                snowThickness = 0;
                freezeStrength = 0.0f;  // 秋季默认无冰冻
                
                // 秋天：红/橙/黄色，暖色调
                grassTint.set(0.0f, -0.05f, -0.1f);
                foliageTint.set(0.1f, -0.05f, -0.15f);  // 偏黄偏橙
                skyTint.set(0.05f, 0.0f, -0.05f);
                fogTint.set(0.05f, 0.02f, 0.0f);
                lightTint.set(0.1f, 0.05f, -0.02f);
                break;
                
            case WINTER:
                humidity = 0.4f;
                snowlineHeight = 80f;
                snowAmount = 0.9f;
                snowThickness = 3;  // 冬季默认积雪厚度为3层
                freezeStrength = 0.9f;
                
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

    // ========== 气候预设（长期趋势） ==========

    public void applyClimatePresetTemperate() {
        this.seasonType = SeasonType.CUSTOM;
        humidity = 0.60f;
        snowlineHeight = 170f;
        snowAmount = 0.20f;
        snowThickness = 1;
        freezeStrength = 0.20f;
        grassTint.set(0.05f, 0.08f, 0.02f);
        foliageTint.set(0.04f, 0.06f, 0.02f);
        skyTint.set(0.02f, 0.02f, 0.05f);
        fogTint.set(0.01f, 0.02f, 0.03f);
        lightTint.set(0.03f, 0.03f, 0.02f);
    }

    public void applyClimatePresetContinental() {
        this.seasonType = SeasonType.CUSTOM;
        humidity = 0.35f;
        snowlineHeight = 120f;
        snowAmount = 0.50f;
        snowThickness = 2;
        freezeStrength = 0.60f;
        grassTint.set(-0.05f, -0.03f, -0.02f);
        foliageTint.set(-0.08f, -0.06f, -0.04f);
        skyTint.set(0.0f, 0.02f, 0.08f);
        fogTint.set(0.0f, 0.03f, 0.08f);
        lightTint.set(-0.02f, -0.02f, 0.04f);
    }

    public void applyClimatePresetMaritime() {
        this.seasonType = SeasonType.CUSTOM;
        humidity = 0.75f;
        snowlineHeight = 200f;
        snowAmount = 0.10f;
        snowThickness = 0;
        freezeStrength = 0.15f;
        grassTint.set(0.02f, 0.06f, 0.04f);
        foliageTint.set(0.02f, 0.05f, 0.03f);
        skyTint.set(0.03f, 0.05f, 0.08f);
        fogTint.set(0.03f, 0.05f, 0.08f);
        lightTint.set(0.02f, 0.03f, 0.04f);
    }

    public void applyClimatePresetHighland() {
        this.seasonType = SeasonType.CUSTOM;
        humidity = 0.50f;
        snowlineHeight = 90f;
        snowAmount = 0.70f;
        snowThickness = 3;
        freezeStrength = 0.70f;
        grassTint.set(-0.10f, -0.10f, -0.12f);
        foliageTint.set(-0.12f, -0.12f, -0.15f);
        skyTint.set(0.0f, 0.03f, 0.12f);
        fogTint.set(0.0f, 0.05f, 0.12f);
        lightTint.set(-0.04f, -0.04f, 0.06f);
    }

    public void applyClimatePresetMonsoonWet() {
        this.seasonType = SeasonType.CUSTOM;
        humidity = 0.90f;
        snowlineHeight = 220f;
        snowAmount = 0.05f;
        snowThickness = 0;
        freezeStrength = 0.05f;
        grassTint.set(0.08f, 0.12f, 0.04f);
        foliageTint.set(0.06f, 0.10f, 0.03f);
        skyTint.set(0.02f, 0.03f, 0.05f);
        fogTint.set(0.04f, 0.06f, 0.08f);
        lightTint.set(0.01f, 0.02f, 0.03f);
    }

    public void applyClimatePresetDesertDry() {
        this.seasonType = SeasonType.CUSTOM;
        humidity = 0.10f;
        snowlineHeight = 256f;
        snowAmount = 0.0f;
        snowThickness = 0;
        freezeStrength = 0.0f;
        grassTint.set(-0.10f, -0.15f, -0.20f);
        foliageTint.set(-0.12f, -0.18f, -0.22f);
        skyTint.set(0.08f, 0.04f, -0.02f);
        fogTint.set(0.10f, 0.06f, -0.02f);
        lightTint.set(0.12f, 0.08f, 0.02f);
    }
    
    // ========== 调试输出 ==========
    
    @Override
    public String toString() {
        return "SeasonKeyframe{" +
                "id=" + id +
                ", time=" + String.format("%.3f", time) +
                ", seasonType=" + seasonType +
                ", humidity=" + humidity +
                ", snowlineHeight=" + snowlineHeight +
                '}';
    }
}

