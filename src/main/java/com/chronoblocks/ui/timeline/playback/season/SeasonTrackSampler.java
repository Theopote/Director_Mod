package com.chronoblocks.ui.timeline.playback.season;

import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.season.SeasonKeyframe;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SeasonTrackSampler v3.0
 *
 * <p>职责：
 * <ul>
 *   <li>从 Season 轨道（TimelineTrack）中收集 SeasonKeyframe</li>
 *   <li>按时间排序</li>
 *   <li>在任意时间 t 采样出 SeasonSample</li>
 * </ul>
 *
 * <p>支持：
 * <ul>
 *   <li>多关键帧线性 / 平滑 / 曲线插值</li>
 *   <li>雪参数 / 冰冻参数 / 颜色 Tint / 气候 全部插值</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 3.0
 */
public class SeasonTrackSampler {

    private final List<SeasonKeyframe> keyframes = new ArrayList<>();

    /**
     * 清空关键帧列表。
     */
    public void clear() {
        keyframes.clear();
    }

    /**
     * 检查是否为空。
     */
    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    /**
     * 检查是否有关键帧（向后兼容）。
     */
    public boolean hasKeyframes() {
        return !keyframes.isEmpty();
    }

    /**
     * 从轨道收集关键帧。
     */
    public void setFromTrack(TimelineTrack track) {
        keyframes.clear();
        if (track == null) return;

        // 假设 track.getAutomationKeyframes() 返回 List<TimelineSelectable>
        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof SeasonKeyframe sk) {
                keyframes.add(sk);
            }
        }

        keyframes.sort(Comparator.comparingDouble(SeasonKeyframe::getTime));
    }

    /**
     * 设置关键帧列表（向后兼容）。
     * 
     * @deprecated 使用 {@link #setFromTrack(TimelineTrack)} 代替
     */
    @Deprecated
    public void setKeyframes(List<SeasonKeyframe> keyframes) {
        this.keyframes.clear();
        if (keyframes != null) {
            this.keyframes.addAll(keyframes);
            this.keyframes.sort(Comparator.comparingDouble(SeasonKeyframe::getTime));
        }
    }

    /**
     * 在给定时间采样。
     * 如果没有任何关键帧，返回 disabled()。
     */
    public SeasonSample sampleAt(float timeSeconds) {
        if (keyframes.isEmpty()) {
            return SeasonSample.disabled();
        }

        if (keyframes.size() == 1) {
            return toSample(keyframes.getFirst());
        }

        // 左右关键帧查找
        SeasonKeyframe left = keyframes.getFirst();
        SeasonKeyframe right = keyframes.getLast();

        if (timeSeconds <= left.getTime()) {
            return toSample(left);
        }
        if (timeSeconds >= right.getTime()) {
            return toSample(right);
        }

        for (int i = 0; i < keyframes.size() - 1; i++) {
            SeasonKeyframe a = keyframes.get(i);
            SeasonKeyframe b = keyframes.get(i + 1);
            if (timeSeconds >= a.getTime() && timeSeconds <= b.getTime()) {
                float t = (timeSeconds - a.getTime()) / (b.getTime() - a.getTime());
                return interpolate(a, b, t);
            }
        }

        // 理论上不会走到这里
        return toSample(right);
    }

    /**
     * 在给定时间采样（向后兼容方法名）。
     * 
     * @deprecated 使用 {@link #sampleAt(float)} 代替
     */
    @Deprecated
    public java.util.Optional<SeasonSample> sample(float timeSeconds) {
        SeasonSample result = sampleAt(timeSeconds);
        if (result.enabled) {
            return java.util.Optional.of(result);
        } else {
            return java.util.Optional.empty();
        }
    }

    // ------------------------------------------------------------------------
    // 插值逻辑
    // ------------------------------------------------------------------------

    private SeasonSample toSample(SeasonKeyframe kf) {
        SeasonSample s = new SeasonSample();
        s.enabled = true;

        s.humidity = kf.getHumidity();

        s.snowAmount = kf.getSnowAmount();
        s.snowlineHeight = kf.getSnowlineHeight();
        s.snowThickness = kf.getSnowThickness();
        s.snowMeltSpeed = kf.getSnowMeltSpeed();

        s.freezeStrength = kf.getFreezeStrength();
        s.freezeMeltSpeed = kf.getFreezeMeltSpeed();

        s.grassTint.set(kf.getGrassTint());
        s.foliageTint.set(kf.getFoliageTint());
        s.skyTint.set(kf.getSkyTint());
        s.fogTint.set(kf.getFogTint());
        s.lightTint.set(kf.getLightTint());

        return s;
    }

    private SeasonSample interpolate(SeasonKeyframe a, SeasonKeyframe b, float t) {
        SeasonSample s = new SeasonSample();
        s.enabled = true;

        t = clamp01(t);

        // 使用左侧关键帧的插值模式
        SeasonKeyframe.Interpolation mode = a.getInterpolation();
        float wt = switch (mode) {
            case DEFAULT -> // DEFAULT 使用 SMOOTH
                    smoothStep(t);
            case LINEAR -> t;
            case SMOOTH -> smoothStep(t);
            case CATMULL_ROM -> smoothStep(t); // 这里预留给未来的真正 Catmull-Rom 曲线
        };

        // 标量线性插值
        s.humidity = lerp(a.getHumidity(), b.getHumidity(), wt);

        s.snowAmount = lerp(a.getSnowAmount(), b.getSnowAmount(), wt);
        s.snowlineHeight = lerp(a.getSnowlineHeight(), b.getSnowlineHeight(), wt);
        s.snowThickness = Math.round(lerp(a.getSnowThickness(), b.getSnowThickness(), wt));
        s.snowMeltSpeed = lerp(a.getSnowMeltSpeed(), b.getSnowMeltSpeed(), wt);

        s.freezeStrength = lerp(a.getFreezeStrength(), b.getFreezeStrength(), wt);
        s.freezeMeltSpeed = lerp(a.getFreezeMeltSpeed(), b.getFreezeMeltSpeed(), wt);

        // 向量插值
        lerpVec(a.getGrassTint(), b.getGrassTint(), wt, s.grassTint);
        lerpVec(a.getFoliageTint(), b.getFoliageTint(), wt, s.foliageTint);
        lerpVec(a.getSkyTint(), b.getSkyTint(), wt, s.skyTint);
        lerpVec(a.getFogTint(), b.getFogTint(), wt, s.fogTint);
        lerpVec(a.getLightTint(), b.getLightTint(), wt, s.lightTint);

        return s;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float smoothStep(float t) {
        // 标准 SmoothStep：3t^2 - 2t^3
        return t * t * (3f - 2f * t);
    }

    private static void lerpVec(Vector3f a, Vector3f b, float t, Vector3f out) {
        out.set(
                lerp(a.x, b.x, t),
                lerp(a.y, b.y, t),
                lerp(a.z, b.z, t)
        );
    }

    /**
     * 获取所有关键帧（用于调试）。
     */
    public List<SeasonKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }
}
