package com.director.ui.timeline.model.weather;

import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.TimelineSelectable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * WeatherKeyframe v1.0
 *
 * <p>时间线上的「天气关键帧」模型，用于 Weather Track（天气轨道）。</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>表示某一时刻的短期天气状态（与 Season Track 的长期气候趋势区分开）</li>
 *   <li>用于驱动：雨强度、雷暴强度、云量、雾、风、地面湿润度等</li>
 *   <li>支持简单预设（晴天 / 小雨 / 暴雨 / 雷暴 / 大雪 / 大雾 / 自定义）</li>
 * </ul>
 *
 * <h3>与 Season 的关系</h3>
 * <ul>
 *   <li>Season 决定「这段时间总体偏多雨 / 偏干燥」，类似气候背景</li>
 *   <li>WeatherKeyframe 决定「此刻在下雨 / 打雷 / 起雾」，类似具体天气事件</li>
 *   <li>优先级：Weather Track > Season Track > Minecraft 原版</li>
 * </ul>
 *
 * <p>实现 {@link TimelineSelectable}，可以作为自动化关键帧出现在时间线交互系统中。</p>
 *
 * @author Director Team
 * @version 1.0
 */
public class WeatherKeyframe implements TimelineSelectable, Comparable<WeatherKeyframe> {

    // ========== 枚举类型 ==========

    /**
     * 天气类型预设。
     */
    public enum WeatherType {
        CLEAR("晴天"),
        LIGHT_RAIN("小雨"),
        HEAVY_RAIN("暴雨"),
        STORM("雷暴"),
        SNOW("大雪"),
        SLEET("雨夹雪"),
        CUSTOM("自定义");

        private final String displayName;

        WeatherType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 降水类型
     */
    public enum RainType {
        NONE("无"),
        RAIN("雨"),
        SNOW("雪"),
        SLEET("夹雪");

        private final String displayName;

        RainType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 特殊天气事件类型
     */
    public enum SpecialWeatherType {
        NONE("无"),
        SANDSTORM("沙暴"),
        BLIZZARD("暴雪"),
        FOGBURST("浓雾"),
        HEATWAVE("热浪");

        private final String displayName;

        SpecialWeatherType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ========== 基础字段 ==========

    /** 唯一 ID，用于在轨道中标识和删除该关键帧 */
    private final UUID id;

    /** 关键帧显示名称（例如 "Light Rain", "Storm"） */
    private String name;

    /** 关键帧时间（秒）—— 对应时间线上的位置 */
    private float time;

    /** 天气类型预设（晴天/小雨/暴雨/雷暴/大雪/大雾/自定义） */
    private WeatherType weatherType;

    /** 插值类型，用于 WeatherTrackSampler 在关键帧之间插值的方式 */
    private TimelineInterpolation interpolation;

    // ========== ① 降水相关 ==========

    /** 雨强度：0 = 无雨，1 = 暴雨 */
    private float rainStrength = 0.0f;

    /** 雪强度：0 = 无雪，1 = 暴雪（用于支持雨夹雪时雨雪独立调整） */
    private float snowStrength = 0.0f;

    /** 降水类型（雨/雪/夹雪） */
    private RainType rainType = RainType.NONE;

    /** 雨滴粒子数量 [0-1] */
    private float rainParticleDensity = 0.0f;

    /** 地面积水程度 [0-1]（Shader可用） */
    private float puddleLevel = 0.0f;

    // ========== ② 雷暴系统 ==========

    /** 雷声量级 [0-1] */
    private float thunderStrength = 0.0f;

    /** 闪电频率（每秒） */
    private float lightningFrequency = 0.0f;

    /** 只视觉雷暴（导演安全模式）：true = 只视觉效果，false = 生成真实闪电实体 */
    private boolean visualOnlyThunder = true;

    /** 风强度 [0-1]（吹动树叶、粒子方向） */
    private float windStrength = 0.0f;

    /** 风方向（角度，度）：0° = +X（东），90° = +Z（南），180° = -X（西），270° = -Z（北） */
    private float windDirectionDegrees = 0.0f;

    /** 天空变暗程度 [0-1] */
    private float stormDarkness = 0.0f;

    // ========== ③ 静态天气（Cloud） ==========

    /** 云层密度 [0-1] */
    private float cloudDensity = 0.5f;

    /** 云移动速度（倍数） */
    private float cloudSpeed = 1.0f;

    /** 云亮度 [0.3-1.2]：控制云的颜色亮度，0.3 = 很暗，1.0 = 正常，1.2 = 很亮 */
    private float cloudBrightness = 1.0f;

    /** 大气雾浓度 [0-1] */
    private float fogDensity = 0.0f;

    /**
     * 雾颜色 RGB（绝对颜色值，与 Season 的 tint 体系不同）：
     * <ul>
     *   <li>(0.7, 0.7, 0.8) = 默认灰蓝色雾</li>
     *   <li>Weather Track 的雾颜色优先级高于 Season Track</li>
     * </ul>
     */
    private final Vector3f fogColor = new Vector3f(0.7f, 0.7f, 0.8f);

    // ========== ④ 温度湿度偏移 ==========

    /** 温度偏移（影响雨/雪阈值） */
    private float tempBias = 0.0f;

    /** 湿度偏移 */
    private float humidityBias = 0.0f;

    // ========== ⑤ 特殊天气事件 ==========

    /** 特殊天气事件类型 */
    private SpecialWeatherType specialWeatherType = SpecialWeatherType.NONE;

    /** 特殊天气强度 [0-1] */
    private float specialWeatherIntensity = 0.0f;

    // ========== 构造 ==========

    /**
     * 创建一个默认「晴天」关键帧。
     *
     * @param time 关键帧时间（秒）
     */
    public WeatherKeyframe(float time) {
        this(UUID.randomUUID(), "Weather @" + String.format("%.2f", time), time,
                WeatherType.CLEAR, TimelineInterpolation.SMOOTH);
    }

    /**
     * 创建一个具有指定类型的天气关键帧。
     *
     * @param name         关键帧名称
     * @param time         时间（秒）
     * @param weatherType  天气类型预设
     * @param interpolation 插值类型
     */
    public WeatherKeyframe(String name, float time, WeatherType weatherType, TimelineInterpolation interpolation) {
        this(UUID.randomUUID(), name, time, weatherType, interpolation);
    }

    /**
     * 兼容旧版本的构造函数。
     *
     * @param name 关键帧名称
     * @param time 时间（秒）
     */
    public WeatherKeyframe(String name, float time) {
        this(UUID.randomUUID(), name, time, WeatherType.CLEAR, TimelineInterpolation.SMOOTH);
    }

    /**
     * 完整构造函数（内部/工具使用）。
     */
    public WeatherKeyframe(UUID id, String name, float time,
                           WeatherType weatherType,
                           TimelineInterpolation interpolation) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.time = time;
        this.weatherType = Objects.requireNonNull(weatherType, "weatherType");
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");

        // 根据预设类型应用默认天气参数
        applyWeatherDefaults();
    }

    // ========== TimelineSelectable 实现 ==========

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return "Weather @" + String.format("%.2fs", time);
    }

    /**
     * TimelineSelectable 中时间字段统一叫 startTime。
     */
    @Override
    public float getStartTime() {
        return time;
    }

    /**
     * 对于「点关键帧」，结束时间与开始时间相同。
     */
    @Override
    public float getEndTime() {
        return time;
    }

    @Override
    public String getType() {
        return "WeatherKeyframe";
    }

    // ========== 比较与调试 ==========

    @Override
    public int compareTo(@NotNull WeatherKeyframe other) {
        int timeCompare = Float.compare(this.time, other.time);
        if (timeCompare != 0) {
            return timeCompare;
        }
        // 时间相同时，用 ID 作为次排序键，保证 TreeSet 等结构中不重复
        return this.id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return "WeatherKeyframe{" +
                "id=" + id +
                ", time=" + String.format("%.3f", time) +
                ", type=" + weatherType +
                ", rain=" + rainStrength +
                ", rainType=" + rainType +
                ", snow=" + (rainType == RainType.SNOW ? rainStrength : 0.0f) +
                ", thunder=" + thunderStrength +
                ", cloud=" + cloudDensity +
                ", fog=" + fogDensity +
                ", wind=" + windStrength +
                ", wet=" + puddleLevel +
                ", special=" + specialWeatherType +
                '}';
    }

    // ========== 基础 getter / setter ==========

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

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

    public WeatherType getWeatherType() {
        return weatherType;
    }

    public void setWeatherType(WeatherType weatherType) {
        WeatherType oldType = this.weatherType;
        this.weatherType = Objects.requireNonNull(weatherType, "weatherType");
        // ⭐ 只有在从非CUSTOM切换到CUSTOM时，不应用预设（保持当前值）
        // 从CUSTOM切换到其他预设时，应用预设值
        // 从其他预设切换到其他预设时，应用新预设值
        if (oldType != WeatherType.CUSTOM && weatherType != WeatherType.CUSTOM) {
            // 切换到新预设，应用新预设的默认值
            applyWeatherDefaults();
        }
        // 如果切换到CUSTOM，不调用applyWeatherDefaults()，保持当前值
    }

    public TimelineInterpolation getInterpolation() {
        return interpolation;
    }

    public void setInterpolation(TimelineInterpolation interpolation) {
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
    }

    // ========== 具体天气参数 getter/setter（全部 clamp 到合法范围） ==========

    public float getRainStrength() {
        return rainStrength;
    }

    public void setRainStrength(float rainStrength) {
        this.rainStrength = clamp01(rainStrength);
    }

    public float getSnowStrength() {
        return snowStrength;
    }

    public void setSnowStrength(float snowStrength) {
        this.snowStrength = clamp01(snowStrength);
    }

    public RainType getRainType() {
        return rainType;
    }

    public void setRainType(RainType rainType) {
        this.rainType = rainType != null ? rainType : RainType.NONE;
    }

    public float getRainParticleDensity() {
        return rainParticleDensity;
    }

    public void setRainParticleDensity(float rainParticleDensity) {
        this.rainParticleDensity = clamp01(rainParticleDensity);
    }

    public float getPuddleLevel() {
        return puddleLevel;
    }

    public void setPuddleLevel(float puddleLevel) {
        this.puddleLevel = clamp01(puddleLevel);
    }

    public float getThunderStrength() {
        return thunderStrength;
    }

    public void setThunderStrength(float thunderStrength) {
        this.thunderStrength = clamp01(thunderStrength);
    }

    public float getLightningFrequency() {
        return lightningFrequency;
    }

    public void setLightningFrequency(float lightningFrequency) {
        this.lightningFrequency = Math.max(0.0f, lightningFrequency);
    }

    /**
     * 获取是否只使用视觉雷暴（导演安全模式）。
     * true = 只视觉效果（闪白、雷声、天空亮度变化），不生成真实闪电实体
     * false = 生成真实 LightningEntity（可能破坏世界）
     */
    public boolean isVisualOnlyThunder() {
        return visualOnlyThunder;
    }

    /**
     * 设置是否只使用视觉雷暴（导演安全模式）。
     * 默认 true（导演友好、安全）
     */
    public void setVisualOnlyThunder(boolean visualOnlyThunder) {
        this.visualOnlyThunder = visualOnlyThunder;
    }

    public float getWindStrength() {
        return windStrength;
    }

    public void setWindStrength(float windStrength) {
        this.windStrength = clamp01(windStrength);
    }

    /**
     * 获取风方向（角度，度）。
     * 范围：[0, 360)，0° = +X（东），90° = +Z（南），180° = -X（西），270° = -Z（北）
     */
    public float getWindDirectionDegrees() {
        return windDirectionDegrees;
    }

    /**
     * 设置风方向（角度，度）。
     * 范围：[0, 360)，会自动归一化到该范围
     */
    public void setWindDirectionDegrees(float windDirectionDegrees) {
        // 归一化到 [0, 360)
        while (windDirectionDegrees < 0f) windDirectionDegrees += 360f;
        while (windDirectionDegrees >= 360f) windDirectionDegrees -= 360f;
        this.windDirectionDegrees = windDirectionDegrees;
    }

    public float getStormDarkness() {
        return stormDarkness;
    }

    public void setStormDarkness(float stormDarkness) {
        this.stormDarkness = clamp01(stormDarkness);
    }

    public float getCloudDensity() {
        return cloudDensity;
    }

    public void setCloudDensity(float cloudDensity) {
        this.cloudDensity = clamp01(cloudDensity);
    }

    public float getCloudSpeed() {
        return cloudSpeed;
    }

    public void setCloudSpeed(float cloudSpeed) {
        this.cloudSpeed = Math.max(0.0f, cloudSpeed);
    }

    /**
     * 获取云亮度（用于 WeatherSample.cloudBrightness）。
     * 范围：[0.3, 1.2]，默认 1.0
     */
    public float getCloudBrightness() {
        return cloudBrightness;
    }

    /**
     * 设置云亮度。
     * 范围：[0.3, 1.2]，0.3 = 很暗，1.0 = 正常，1.2 = 很亮
     */
    public void setCloudBrightness(float cloudBrightness) {
        this.cloudBrightness = Math.max(0.3f, Math.min(1.2f, cloudBrightness));
    }

    public float getFogDensity() {
        return fogDensity;
    }

    public void setFogDensity(float fogDensity) {
        this.fogDensity = clamp01(fogDensity);
    }

    public Vector3f getFogColor() {
        return fogColor;
    }

    public void setFogColor(float r, float g, float b) {
        this.fogColor.set(
                Math.max(0.0f, Math.min(1.0f, r)),
                Math.max(0.0f, Math.min(1.0f, g)),
                Math.max(0.0f, Math.min(1.0f, b))
        );
    }

    public float getTempBias() {
        return tempBias;
    }

    public void setTempBias(float tempBias) {
        this.tempBias = Math.max(-1.0f, Math.min(1.0f, tempBias));
    }

    public float getHumidityBias() {
        return humidityBias;
    }

    public void setHumidityBias(float humidityBias) {
        this.humidityBias = Math.max(-1.0f, Math.min(1.0f, humidityBias));
    }

    public SpecialWeatherType getSpecialWeatherType() {
        return specialWeatherType;
    }

    public void setSpecialWeatherType(SpecialWeatherType specialWeatherType) {
        this.specialWeatherType = specialWeatherType != null ? specialWeatherType : SpecialWeatherType.NONE;
    }

    public float getSpecialWeatherIntensity() {
        return specialWeatherIntensity;
    }

    public void setSpecialWeatherIntensity(float specialWeatherIntensity) {
        this.specialWeatherIntensity = clamp01(specialWeatherIntensity);
    }

    // ========== 新版本 WeatherSample 兼容方法 ==========

    /**
     * 获取雨强度（用于 WeatherSample.rainIntensity）。
     * 如果 rainType 是 RAIN 或 SLEET，返回 rainStrength，否则返回 0。
     */
    public float getRainIntensity() {
        return (rainType == RainType.RAIN || rainType == RainType.SLEET) ? rainStrength : 0f;
    }

    /**
     * 获取雪强度（用于 WeatherSample.snowIntensity）。
     * 如果 rainType 是 SNOW 或 SLEET，返回 snowStrength，否则返回 0。
     */
    public float getSnowIntensity() {
        return (rainType == RainType.SNOW || rainType == RainType.SLEET) ? snowStrength : 0f;
    }

    /**
     * 获取雷暴强度（用于 WeatherSample.thunderIntensity）。
     * 直接返回 thunderStrength。
     */
    public float getThunderIntensity() {
        return thunderStrength;
    }

    /**
     * 获取云量（用于 WeatherSample.cloudCoverage）。
     * 直接返回 cloudDensity。
     */
    public float getCloudCoverage() {
        return cloudDensity;
    }

    /**
     * 获取地面湿度（用于 WeatherSample.wetness）。
     * 直接返回 puddleLevel。
     */
    public float getWetness() {
        return puddleLevel;
    }

    // ========== 预设逻辑 ==========

    /**
     * 根据 weatherType 应用一组合理的默认参数。
     * 不改变 time / name，只修改天气相关字段。
     */
    public void applyWeatherPreset(WeatherType type) {
        this.weatherType = Objects.requireNonNull(type, "type");
        applyWeatherDefaults();
    }

    /**
     * 内部方法：按当前 weatherType 写入默认值。
     */
    private void applyWeatherDefaults() {
        switch (weatherType) {
            case CLEAR -> {
                rainStrength = 0.0f;
                rainType = RainType.NONE;
                rainParticleDensity = 0.0f;
                puddleLevel = 0.0f;
                thunderStrength = 0.0f;
                lightningFrequency = 0.0f;
                windStrength = 0.1f;
                stormDarkness = 0.0f;
                cloudDensity = 0.2f;
                cloudSpeed = 1.0f;
                cloudBrightness = 1.0f;
                fogDensity = 0.0f;
                fogColor.set(0.7f, 0.7f, 0.8f);
                tempBias = 0.0f;
                humidityBias = 0.0f;
                specialWeatherType = SpecialWeatherType.NONE;
                specialWeatherIntensity = 0.0f;
            }
            case LIGHT_RAIN -> {
                rainStrength = 0.4f;
                rainType = RainType.RAIN;
                rainParticleDensity = 0.3f;
                puddleLevel = 0.5f;
                thunderStrength = 0.0f;
                lightningFrequency = 0.0f;
                windStrength = 0.3f;
                windDirectionDegrees = 45.0f; // 默认东南风
                stormDarkness = 0.2f;
                cloudDensity = 0.7f;
                cloudSpeed = 1.2f;
                cloudBrightness = 0.8f;
                fogDensity = 0.2f;
                fogColor.set(0.65f, 0.65f, 0.75f);
                tempBias = 0.0f;
                humidityBias = 0.2f;
                specialWeatherType = SpecialWeatherType.NONE;
                specialWeatherIntensity = 0.0f;
            }
            case HEAVY_RAIN -> {
                rainStrength = 0.9f;
                rainType = RainType.RAIN;
                rainParticleDensity = 0.8f;
                puddleLevel = 1.0f;
                thunderStrength = 0.3f;
                lightningFrequency = 0.5f;
                windStrength = 0.6f;
                windDirectionDegrees = 90.0f; // 默认南风
                stormDarkness = 0.5f;
                cloudDensity = 1.0f;
                cloudSpeed = 1.5f;
                cloudBrightness = 0.6f;
                fogDensity = 0.4f;
                fogColor.set(0.6f, 0.6f, 0.7f);
                tempBias = -0.1f;
                humidityBias = 0.5f;
                specialWeatherType = SpecialWeatherType.NONE;
                specialWeatherIntensity = 0.0f;
            }
            case STORM -> {
                rainStrength = 1.0f;
                rainType = RainType.RAIN;
                rainParticleDensity = 1.0f;
                puddleLevel = 1.0f;
                thunderStrength = 1.0f;
                lightningFrequency = 2.0f;
                windStrength = 1.0f;
                windDirectionDegrees = 135.0f; // 默认西南风（暴风）
                stormDarkness = 0.8f;
                cloudDensity = 1.0f;
                cloudSpeed = 2.0f;
                cloudBrightness = 0.5f;
                fogDensity = 0.6f;
                fogColor.set(0.5f, 0.5f, 0.6f);
                tempBias = -0.2f;
                humidityBias = 0.8f;
                specialWeatherType = SpecialWeatherType.NONE;
                specialWeatherIntensity = 0.0f;
            }
            case SNOW -> {
                rainStrength = 0.0f;
                snowStrength = 1.0f;
                rainType = RainType.SNOW;
                rainParticleDensity = 0.9f;
                puddleLevel = 0.6f;
                thunderStrength = 0.0f;
                lightningFrequency = 0.0f;
                windStrength = 0.3f;
                windDirectionDegrees = 270.0f; // 默认北风（雪）
                stormDarkness = 0.3f;
                cloudDensity = 0.9f;
                cloudSpeed = 0.8f;
                cloudBrightness = 0.7f;
                fogDensity = 0.4f;
                fogColor.set(0.75f, 0.75f, 0.85f);
                tempBias = -0.5f;
                humidityBias = 0.3f;
                specialWeatherType = SpecialWeatherType.NONE;
                specialWeatherIntensity = 0.0f;
            }
            case SLEET -> {
                rainStrength = 0.7f;
                snowStrength = 0.7f;  // ✅ 雨夹雪：雨和雪都有强度
                rainType = RainType.SLEET;
                rainParticleDensity = 0.6f;
                puddleLevel = 0.5f;
                thunderStrength = 0.0f;
                lightningFrequency = 0.0f;
                windStrength = 0.4f;
                windDirectionDegrees = 225.0f; // 默认西北风（雨夹雪）
                stormDarkness = 0.4f;
                cloudDensity = 0.85f;
                cloudSpeed = 1.0f;
                cloudBrightness = 0.7f;
                fogDensity = 0.35f;
                fogColor.set(0.7f, 0.7f, 0.8f);
                tempBias = -0.2f;
                humidityBias = 0.4f;
                specialWeatherType = SpecialWeatherType.NONE;
                specialWeatherIntensity = 0.0f;
            }
            case CUSTOM -> {
                // 自定义：不强行改写当前值，只保证 clamp 范围合法
                rainStrength = clamp01(rainStrength);
                snowStrength = clamp01(snowStrength);
                rainParticleDensity = clamp01(rainParticleDensity);
                puddleLevel = clamp01(puddleLevel);
                thunderStrength = clamp01(thunderStrength);
                lightningFrequency = Math.max(0.0f, lightningFrequency);
                windStrength = clamp01(windStrength);
                stormDarkness = clamp01(stormDarkness);
                cloudDensity = clamp01(cloudDensity);
                cloudSpeed = Math.max(0.0f, cloudSpeed);
                fogDensity = clamp01(fogDensity);
                tempBias = Math.max(-1.0f, Math.min(1.0f, tempBias));
                humidityBias = Math.max(-1.0f, Math.min(1.0f, humidityBias));
                specialWeatherIntensity = clamp01(specialWeatherIntensity);
                if (fogColor.lengthSquared() == 0.0f) {
                    fogColor.set(0.7f, 0.7f, 0.8f);
                }
            }
        }
    }

    // ========== 工具方法 ==========

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        return Math.min(v, 1.0f);
    }
}
