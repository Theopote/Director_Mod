package com.chronoblocks.ui.timeline.playback.weather;

import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * WeatherTrackSampler v1.0
 *
 * <p>根据当前时间 sampleTime，从天气轨道中算出天气的最终状态。
 * <p>流程：
 *  1. 找到 sampleTime 前后的两个关键帧
 *  2. 将它们转换成 WeatherSample
 *  3. 根据插值方式进行插值（Linear / Smooth / Step）
 *  4. 输出 WeatherSample
 *
 * <p>特点：
 *  - 当没有关键帧时输出 "默认晴天" 状态
 *  - 当只有 1 个关键帧时直接返回其天气
 *  - 插值逻辑集中在 WeatherSample.lerpFrom()
 *
 * @author ChronoBlocks
 * @version 1.0
 */
public class WeatherTrackSampler {

    /** 要采样的轨道（属 TimelineModel） */
    private final TimelineTrack track;

    /** 轨道中所有天气关键帧的缓存（按时间排序） */
    private List<WeatherKeyframe> keyframes = Collections.emptyList();

    /**
     * 构造函数（接受轨道）。
     */
    public WeatherTrackSampler(TimelineTrack track) {
        this.track = track;
        rebuildKeyframeList();
    }

    // ==========================================================
    // 重建缓存（当轨道关键帧变化、删除、添加时调用）
    // ==========================================================

    /**
     * 重建关键帧列表（从轨道中收集所有 WeatherKeyframe）。
     */
    public void rebuildKeyframeList() {
        if (track == null) {
            this.keyframes = Collections.emptyList();
            return;
        }

        List<WeatherKeyframe> list = new ArrayList<>();
        for (com.chronoblocks.ui.timeline.model.TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof WeatherKeyframe wk) {
                list.add(wk);
            }
        }
        list.sort(Comparator.comparingDouble(WeatherKeyframe::getTime));
        this.keyframes = list;
    }

    /**
     * 清空关键帧列表。
     */
    public void clear() {
        this.keyframes = Collections.emptyList();
    }

    /**
     * 检查是否为空。
     */
    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    // ==========================================================
    // 主采样函数 —— TimelineWeatherController 会每 tick 调用
    // ==========================================================

    /**
     * 在给定时间采样天气状态。
     *
     * @param time 时间（秒）
     * @return 天气采样结果
     */
    public WeatherSample sample(float time) {
        return sampleAt(time);
    }

    /**
     * 在给定时间采样天气状态（向后兼容方法）。
     *
     * @param timeSeconds 时间（秒）
     * @return 天气采样结果
     */
    public WeatherSample sampleAt(float timeSeconds) {
        // 0) 无关键帧 → 返回默认晴天
        if (keyframes.isEmpty()) {
            return defaultClearWeather();
        }

        // 1) 只有 1 个关键帧 → 输出其天气
        if (keyframes.size() == 1) {
            return toSample(keyframes.getFirst());
        }

        // 2) 找到时间段 [kfA, kfB]
        WeatherKeyframe kfA = keyframes.getFirst();
        WeatherKeyframe kfB = keyframes.getLast();

        // 如果时间在最前
        if (timeSeconds <= kfA.getTime()) {
            return toSample(kfA);
        }

        // 如果时间在最后
        if (timeSeconds >= kfB.getTime()) {
            return toSample(kfB);
        }

        // 遍历查找区段
        for (int i = 0; i < keyframes.size() - 1; i++) {
            WeatherKeyframe a = keyframes.get(i);
            WeatherKeyframe b = keyframes.get(i + 1);

            if (timeSeconds >= a.getTime() && timeSeconds <= b.getTime()) {
                return interpolateBetween(a, b, timeSeconds);
            }
        }

        // 理论不会到这里，但为了安全：
        return toSample(kfB);
    }

    // ==========================================================
    // 插值逻辑
    // ==========================================================

    /**
     * 在两个关键帧之间插值。
     */
    private WeatherSample interpolateBetween(WeatherKeyframe a, WeatherKeyframe b, float time) {
        float dt = b.getTime() - a.getTime();
        if (dt <= 0) {
            return toSample(a);
        }

        float t = (time - a.getTime()) / dt;
        t = Math.max(0f, Math.min(1f, t));

        WeatherSample sampleA = toSample(a);
        WeatherSample sampleB = toSample(b);

        WeatherSample out = new WeatherSample();
        TimelineInterpolation interp = a.getInterpolation();   // 插值方式读取前关键帧的定义

        out.lerpFrom(sampleA, sampleB, t, interp);

        return out;
    }

    // ==========================================================
    // 将关键帧转换为运行时操作 Sample
    // ==========================================================

    /**
     * 将关键帧转换为采样（单关键帧时使用）。
     */
    private WeatherSample toSample(WeatherKeyframe kf) {
        WeatherSample out = new WeatherSample();
        out.enabled = true;

        // ========== 核心参数（新版本） ==========
        out.rainIntensity = kf.getRainIntensity();
        out.snowIntensity = kf.getSnowIntensity();
        out.thunderIntensity = kf.getThunderIntensity();
        out.cloudCoverage = kf.getCloudCoverage();
        out.fogDensity = kf.getFogDensity();
        out.windStrength = kf.getWindStrength();
        out.wetness = kf.getWetness();

        // fogTint: 从 fogColor 计算（简化映射，保持原色）
        out.fogTint.set(1f, 1f, 1f); // 默认不改变，实际使用 fogColor
        out.fogColor.set(kf.getFogColor());

        out.type = kf.getWeatherType();

        // 渲染控制字段：从现有字段映射
        out.skyDarkness = kf.getStormDarkness(); // 天空暗度 = 风暴暗度
        out.cloudAmount = kf.getCloudDensity(); // 云量 = 云密度
        out.cloudBrightness = 1f - (kf.getCloudDensity() * 0.3f); // 云越密越暗
        out.fogDarkness = kf.getFogDensity() * 0.3f; // 雾暗度 = 雾密度 * 0.3

        // ========== 扩展参数（向后兼容） ==========
        out.rainStrength = kf.getRainStrength();
        out.rainType = kf.getRainType();
        out.rainParticleDensity = kf.getRainParticleDensity();
        out.puddleLevel = kf.getPuddleLevel();

        out.thunderStrength = kf.getThunderStrength();
        out.lightningFrequency = kf.getLightningFrequency();
        out.stormDarkness = kf.getStormDarkness();

        out.cloudDensity = kf.getCloudDensity();
        out.cloudSpeed = kf.getCloudSpeed();

        out.tempBias = kf.getTempBias();
        out.humidityBias = kf.getHumidityBias();

        out.specialWeatherType = kf.getSpecialWeatherType();
        out.specialWeatherIntensity = kf.getSpecialWeatherIntensity();

        return out;
    }

    // ==========================================================
    // 默认天气 sample（无关键帧时）
    // ==========================================================

    /**
     * 创建默认晴天天气采样。
     */
    private WeatherSample defaultClearWeather() {
        WeatherSample s = new WeatherSample();
        s.enabled = false; // 默认不启用，使用原版天气
        s.type = WeatherKeyframe.WeatherType.CLEAR;
        s.rainIntensity = 0f;
        s.snowIntensity = 0f;
        s.thunderIntensity = 0f;
        s.cloudCoverage = 0.1f;
        s.fogDensity = 0f;
        s.windStrength = 0.05f;
        s.wetness = 0f;
        s.fogTint.set(1f, 1f, 1f);
        s.skyDarkness = 0f;
        s.cloudAmount = 0.1f;
        s.cloudBrightness = 1f;
        s.fogDarkness = 0f;
        return s;
    }

    /**
     * 获取所有关键帧（用于调试）。
     */
    public List<WeatherKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }
}
