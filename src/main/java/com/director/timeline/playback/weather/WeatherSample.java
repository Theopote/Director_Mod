package com.director.timeline.playback.weather;

import com.director.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.weather.WeatherKeyframe;
import org.joml.Vector3f;

/**
 * WeatherSample v1.0
 *
 * <p>由 WeatherTrackSampler 在某一时间采样得到的天气状态。
 * 用于驱动 Minecraft 世界的即时天气（雨、雪、雷、雾、云、风等）。
 *
 * <p>设计原则：
 * - 所有参数标准化为 [0,1] 区间，方便插值
 * - 不负责逻辑，纯数据结构
 * - 完全独立于 SeasonSample（短期 vs 长期）
 *
 * <p>用于：
 * - TimelineWeatherController.apply(sample)
 * - Mixin / 渲染系统：雾、云、闪电、降水粒子等
 *
 * @author Director
 * @version 1.0
 */
public class WeatherSample {

    // ========== 核心天气参数（新版本设计） ==========

    /** 雨强度：0 = 无雨，1 = 暴雨 */
    public float rainIntensity = 0f;

    /** 雪强度：0 = 无雪，1 = 暴雪 */
    public float snowIntensity = 0f;

    /** 雷暴强度：0 = 无雷，1 = 频繁闪电 */
    public float thunderIntensity = 0f;

    /** 云量：0 = 晴朗，1 = 厚云 */
    public float cloudCoverage = 0f;

    /** 雾浓度：0 = 无雾，1 = 非常浓的雾 */
    public float fogDensity = 0f;

    /** 风力：0 = 无风，1 = 狂风 */
    public float windStrength = 0f;

    /** 风方向（角度，度）：0° = +X（东），90° = +Z（南），180° = -X（西），270° = -Z（北） */
    public float windDirectionDegrees = 0f;

    /** 地面湿度：0 = 干燥，1 = 很湿（用于反光/水坑） */
    public float wetness = 0f;

    /**
     * 雾颜色 tint：
     * (1,1,1) = 原色
     * >1 = 加亮
     * <1 = 变暗/偏色
     */
    public final Vector3f fogTint = new Vector3f(1f, 1f, 1f);

    /** 当前天气 preset（用于 UI 或 debug） */
    public WeatherKeyframe.WeatherType type = WeatherKeyframe.WeatherType.CLEAR;

    /** 天空暗度：0 = 正常, 1 = 完全黑 */
    public float skyDarkness = 0f;

    /** 云量：0 = 无云，1 = 满天云 */
    public float cloudAmount = 0f;

    /** 云亮度：0 = 暗，1 = 亮 */
    public float cloudBrightness = 1f;

    /** 雾暗度：0 = 正常，1 = 完全黑 */
    public float fogDarkness = 0f;

    // ========== 扩展属性（保持向后兼容） ==========

    /** 是否启用天气覆盖（false 表示完全使用原版行为） */
    public boolean enabled = false;

    /** 雨强度 [0-1]（旧属性，与 rainIntensity 同步） */
    public float rainStrength = 0.0f;

    /** 降水类型（雨/雪/夹雪） */
    public WeatherKeyframe.RainType rainType = WeatherKeyframe.RainType.NONE;

    /** 雨滴粒子数量 [0-1] */
    public float rainParticleDensity = 0.0f;

    /** 地面积水程度 [0-1]（与 wetness 相关但更具体） */
    public float puddleLevel = 0.0f;

    /** 雷声量级 [0-1]（旧属性，与 thunderIntensity 同步） */
    public float thunderStrength = 0.0f;

    /** 闪电频率（每秒） */
    public float lightningFrequency = 0.0f;

    /** 只视觉雷暴（导演安全模式）：true = 只视觉效果，false = 生成真实闪电实体 */
    public boolean visualOnlyThunder = true;

    /** 天空变暗程度 [0-1] */
    public float stormDarkness = 0.0f;

    /** 云层密度 [0-1]（旧属性，与 cloudCoverage 同步） */
    public float cloudDensity = 0.0f;

    /** 云移动速度（倍数） */
    public float cloudSpeed = 1.0f;

    /** 雾颜色 RGB（绝对颜色值，与 fogTint 不同） */
    public final Vector3f fogColor = new Vector3f(0.7f, 0.7f, 0.8f);

    /** 温度偏移 */
    public float tempBias = 0.0f;

    /** 湿度偏移 */
    public float humidityBias = 0.0f;

    /** 特殊天气事件类型 */
    public WeatherKeyframe.SpecialWeatherType specialWeatherType = WeatherKeyframe.SpecialWeatherType.NONE;

    /** 特殊天气强度 [0-1] */
    public float specialWeatherIntensity = 0.0f;

    // ==========================================================
    // 工具方法
    // ==========================================================

    /**
     * 在两个采样之间插值（新版本方法）。
     * 
     * @param a 起始采样
     * @param b 结束采样
     * @param t 插值系数 [0,1]
     * @param interpolation 插值类型
     */
    public void lerpFrom(WeatherSample a, WeatherSample b, float t, TimelineInterpolation interpolation) {
        if (a == null || !a.enabled) {
            if (b != null && b.enabled) {
                copyFrom(b);
            } else {
                copyFrom(disabled());
            }
            return;
        }
        if (b == null || !b.enabled) {
            copyFrom(a);
            return;
        }

        if (interpolation == TimelineInterpolation.STEP) {
            copyFrom(t < 0.5f ? a : b);
            return;
        }

        if (interpolation == TimelineInterpolation.SMOOTH) {
            // Smoothstep
            t = t * t * (3f - 2f * t);
        }

        // 核心参数插值
        rainIntensity = lerp(a.rainIntensity, b.rainIntensity, t);
        snowIntensity = lerp(a.snowIntensity, b.snowIntensity, t);
        thunderIntensity = lerp(a.thunderIntensity, b.thunderIntensity, t);
        cloudCoverage = lerp(a.cloudCoverage, b.cloudCoverage, t);
        fogDensity = lerp(a.fogDensity, b.fogDensity, t);
        windStrength = lerp(a.windStrength, b.windStrength, t);
        windDirectionDegrees = lerpAngle(a.windDirectionDegrees, b.windDirectionDegrees, t);
        wetness = lerp(a.wetness, b.wetness, t);
        fogTint.x = lerp(a.fogTint.x, b.fogTint.x, t);
        fogTint.y = lerp(a.fogTint.y, b.fogTint.y, t);
        fogTint.z = lerp(a.fogTint.z, b.fogTint.z, t);
        type = t < 0.5f ? a.type : b.type;

        // 新增渲染控制字段
        skyDarkness = lerp(a.skyDarkness, b.skyDarkness, t);
        cloudAmount = lerp(a.cloudAmount, b.cloudAmount, t);
        cloudBrightness = lerp(a.cloudBrightness, b.cloudBrightness, t);
        fogDarkness = lerp(a.fogDarkness, b.fogDarkness, t);

        // 扩展参数插值
        rainStrength = lerp(a.rainStrength, b.rainStrength, t); // 同步 rainIntensity
        rainParticleDensity = lerp(a.rainParticleDensity, b.rainParticleDensity, t);
        puddleLevel = lerp(a.puddleLevel, b.puddleLevel, t);
        thunderStrength = lerp(a.thunderStrength, b.thunderStrength, t); // 同步 thunderIntensity
        lightningFrequency = lerp(a.lightningFrequency, b.lightningFrequency, t);
        stormDarkness = lerp(a.stormDarkness, b.stormDarkness, t);
        cloudDensity = lerp(a.cloudDensity, b.cloudDensity, t); // 同步 cloudCoverage
        cloudSpeed = lerp(a.cloudSpeed, b.cloudSpeed, t);
        fogColor.x = lerp(a.fogColor.x, b.fogColor.x, t);
        fogColor.y = lerp(a.fogColor.y, b.fogColor.y, t);
        fogColor.z = lerp(a.fogColor.z, b.fogColor.z, t);
        tempBias = lerp(a.tempBias, b.tempBias, t);
        humidityBias = lerp(a.humidityBias, b.humidityBias, t);
        specialWeatherIntensity = lerp(a.specialWeatherIntensity, b.specialWeatherIntensity, t);

        // 枚举类型：根据 t 选择
        rainType = (t < 0.5f) ? a.rainType : b.rainType;
        specialWeatherType = (t < 0.5f) ? a.specialWeatherType : b.specialWeatherType;

        // ✅ 布尔值：取最近关键帧的值（不插值）
        visualOnlyThunder = (t < 0.5f) ? a.visualOnlyThunder : b.visualOnlyThunder;

        enabled = true;
    }

    /** 复制所有字段 */
    public void copyFrom(WeatherSample s) {
        if (s == null) {
            copyFrom(disabled());
            return;
        }

        enabled = s.enabled;
        rainIntensity = s.rainIntensity;
        snowIntensity = s.snowIntensity;
        thunderIntensity = s.thunderIntensity;
        cloudCoverage = s.cloudCoverage;
        fogDensity = s.fogDensity;
        windStrength = s.windStrength;
        windDirectionDegrees = s.windDirectionDegrees;
        wetness = s.wetness;
        fogTint.set(s.fogTint);
        type = s.type;

        skyDarkness = s.skyDarkness;
        cloudAmount = s.cloudAmount;
        cloudBrightness = s.cloudBrightness;
        fogDarkness = s.fogDarkness;

        rainStrength = s.rainStrength;
        rainType = s.rainType;
        rainParticleDensity = s.rainParticleDensity;
        puddleLevel = s.puddleLevel;
        thunderStrength = s.thunderStrength;
        lightningFrequency = s.lightningFrequency;
        visualOnlyThunder = s.visualOnlyThunder;
        stormDarkness = s.stormDarkness;
        cloudDensity = s.cloudDensity;
        cloudSpeed = s.cloudSpeed;
        fogColor.set(s.fogColor);
        tempBias = s.tempBias;
        humidityBias = s.humidityBias;
        specialWeatherType = s.specialWeatherType;
        specialWeatherIntensity = s.specialWeatherIntensity;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * 角度插值（处理 360° 环绕，选择最短路径）。
     * 例如：350° 到 10° 会插值到 0° 而不是 180°
     */
    private float lerpAngle(float a, float b, float t) {
        // 归一化到 [0, 360)
        while (a < 0f) a += 360f;
        while (a >= 360f) a -= 360f;
        while (b < 0f) b += 360f;
        while (b >= 360f) b -= 360f;

        // 计算两个方向的距离
        float diff = b - a;
        if (diff > 180f) {
            diff -= 360f; // 走反向更短
        } else if (diff < -180f) {
            diff += 360f; // 走反向更短
        }

        float result = a + diff * t;
        // 归一化结果
        while (result < 0f) result += 360f;
        while (result >= 360f) result -= 360f;
        return result;
    }

    /**
     * 创建一个禁用状态的采样（不应用任何天气效果）。
     */
    public static WeatherSample disabled() {
        WeatherSample s = new WeatherSample();
        s.enabled = false;
        return s;
    }

    /**
     * 深拷贝当前采样（兼容旧版本）。
     */
    public WeatherSample copy() {
        WeatherSample s = new WeatherSample();
        s.copyFrom(this);
        return s;
    }

    @Override
    public String toString() {
        return "WeatherSample{" +
                "enabled=" + enabled +
                ", rainIntensity=" + rainIntensity +
                ", snowIntensity=" + snowIntensity +
                ", thunderIntensity=" + thunderIntensity +
                ", cloudCoverage=" + cloudCoverage +
                ", fogDensity=" + fogDensity +
                ", windStrength=" + windStrength +
                ", wetness=" + wetness +
                ", fogTint=" + fogTint +
                ", type=" + type +
                ", rainType=" + rainType +
                ", specialWeatherType=" + specialWeatherType +
                '}';
    }
}
