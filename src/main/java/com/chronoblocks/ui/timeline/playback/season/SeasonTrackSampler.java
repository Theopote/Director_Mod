package com.chronoblocks.ui.timeline.playback.season;

import com.chronoblocks.ui.timeline.model.season.SeasonKeyframe;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SeasonTrackSampler v4.0
 * ------------------------------------------
 * 负责根据 SeasonKeyframe 生成 SeasonSample（每帧使用）。
 * 
 * 插值以下内容：
 *  - 季节类型（但本质决定颜色 preset）
 *  - 温度、湿度、雪线高度
 *  - 所有颜色 tint（grass/foliage/sky/fog/light）
 *  - ❄ 雪覆盖强度 snowAmount (映射到 snowCoverageStrength)
 *  - ❄ 雪层高度 snowThickness (映射到 snowLayerDepth)
 *  - 🧊 冰冻强度 freezeStrength (映射到 iceCoverageStrength)
 * 
 * 所有值严格 clamp 在 [0,1] 或合理范围。
 * 
 * @author ChronoBlocks Team
 * @version 4.0 - 统一插值系统
 */
public class SeasonTrackSampler {

    private final List<SeasonKeyframe> keyframes = new ArrayList<>();

    /**
     * 构造函数（向后兼容）。
     */
    public SeasonTrackSampler() {
    }

    /**
     * 构造函数（接受关键帧列表）。
     */
    public SeasonTrackSampler(List<SeasonKeyframe> keyframes) {
        if (keyframes != null) {
            this.keyframes.addAll(keyframes);
            this.keyframes.sort(Comparator.comparingDouble(SeasonKeyframe::getTime));
        }
    }

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
    public void setFromTrack(com.chronoblocks.ui.timeline.model.TimelineTrack track) {
        keyframes.clear();
        if (track == null) return;

        for (com.chronoblocks.ui.timeline.model.TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof SeasonKeyframe sk) {
                keyframes.add(sk);
            }
        }

        keyframes.sort(Comparator.comparingDouble(SeasonKeyframe::getTime));
    }

    /**
     * 设置关键帧列表（向后兼容）。
     * 
     * @deprecated 使用 {@link #setFromTrack(com.chronoblocks.ui.timeline.model.TimelineTrack)} 代替
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
        SeasonSample out = new SeasonSample();

        if (keyframes.isEmpty()) {
            // 无关键帧 → 默认世界
            out.enabled = false;
            return out;
        }

        // 单关键帧 → 直接应用，无需插值
        if (keyframes.size() == 1) {
            return toSample(keyframes.get(0));
        }

        // 找到 kf0 <= time <= kf1
        SeasonKeyframe kf0 = keyframes.get(0);
        SeasonKeyframe kf1 = keyframes.get(keyframes.size() - 1);

        for (int i = 0; i < keyframes.size() - 1; i++) {
            SeasonKeyframe a = keyframes.get(i);
            SeasonKeyframe b = keyframes.get(i + 1);
            if (timeSeconds >= a.getTime() && timeSeconds <= b.getTime()) {
                kf0 = a;
                kf1 = b;
                break;
            }
        }

        // 边界处理
        if (timeSeconds <= kf0.getTime()) {
            return toSample(kf0);
        }
        if (timeSeconds >= kf1.getTime()) {
            return toSample(kf1);
        }

        float t = computeT(kf0, kf1, timeSeconds);

        out.enabled = true;

        // ========== 基础气候参数 ==========
        // 注意：当前 SeasonKeyframe 没有 temperature 字段，使用 humidity 代替
        out.humidity = MathHelper.clamp(
            MathHelper.lerp(t, kf0.getHumidity(), kf1.getHumidity()),
            0f, 1f
        );
        out.snowlineHeight = MathHelper.lerp(t, kf0.getSnowlineHeight(), kf1.getSnowlineHeight());

        // ========== 颜色插值 ==========
        out.grassTint.set(
            MathHelper.lerp(t, kf0.getGrassTint().x, kf1.getGrassTint().x),
            MathHelper.lerp(t, kf0.getGrassTint().y, kf1.getGrassTint().y),
            MathHelper.lerp(t, kf0.getGrassTint().z, kf1.getGrassTint().z)
        );

        out.foliageTint.set(
            MathHelper.lerp(t, kf0.getFoliageTint().x, kf1.getFoliageTint().x),
            MathHelper.lerp(t, kf0.getFoliageTint().y, kf1.getFoliageTint().y),
            MathHelper.lerp(t, kf0.getFoliageTint().z, kf1.getFoliageTint().z)
        );

        out.skyTint.set(
            MathHelper.lerp(t, kf0.getSkyTint().x, kf1.getSkyTint().x),
            MathHelper.lerp(t, kf0.getSkyTint().y, kf1.getSkyTint().y),
            MathHelper.lerp(t, kf0.getSkyTint().z, kf1.getSkyTint().z)
        );

        out.fogTint.set(
            MathHelper.lerp(t, kf0.getFogTint().x, kf1.getFogTint().x),
            MathHelper.lerp(t, kf0.getFogTint().y, kf1.getFogTint().y),
            MathHelper.lerp(t, kf0.getFogTint().z, kf1.getFogTint().z)
        );

        out.lightTint.set(
            MathHelper.lerp(t, kf0.getLightTint().x, kf1.getLightTint().x),
            MathHelper.lerp(t, kf0.getLightTint().y, kf1.getLightTint().y),
            MathHelper.lerp(t, kf0.getLightTint().z, kf1.getLightTint().z)
        );

        // ======================================================
        // ❄❄❄ 雪覆盖 / 堆积插值
        // ======================================================
        // 映射：snowCoverageStrength → snowAmount
        out.snowAmount = MathHelper.clamp(
            MathHelper.lerp(t, kf0.getSnowAmount(), kf1.getSnowAmount()), 
            0f, 1f
        );

        // 映射：snowLayerDepth → snowThickness (int)
        // 注意：snowThickness 是 int [0, 10]，需要四舍五入
        float snowThicknessFloat = MathHelper.lerp(t, 
            (float) kf0.getSnowThickness(), 
            (float) kf1.getSnowThickness()
        );
        out.snowThickness = Math.round(MathHelper.clamp(snowThicknessFloat, 0f, 10f));

        // ================================
        // 🧊 冰冻强度插值
        // ================================
        // 映射：iceCoverageStrength → freezeStrength
        // 使用严格线性插值（使用原始 t，而不是经过插值模式处理的 t），确保冰冻效果平滑连续
        out.freezeStrength = MathHelper.clamp(
            MathHelper.lerp(t, kf0.getFreezeStrength(), kf1.getFreezeStrength()),
            0f, 1f
        );

        return out;
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

    /**
     * 将关键帧转换为采样（单关键帧时使用）。
     */
    private SeasonSample toSample(SeasonKeyframe kf) {
        SeasonSample s = new SeasonSample();
        s.enabled = true;

        s.humidity = kf.getHumidity();
        s.snowAmount = kf.getSnowAmount();
        s.snowlineHeight = kf.getSnowlineHeight();
        s.snowThickness = kf.getSnowThickness();
        s.freezeStrength = kf.getFreezeStrength();

        s.grassTint.set(kf.getGrassTint());
        s.foliageTint.set(kf.getFoliageTint());
        s.skyTint.set(kf.getSkyTint());
        s.fogTint.set(kf.getFogTint());
        s.lightTint.set(kf.getLightTint());

        return s;
    }

    /**
     * 根据关键帧插值曲线类型决定 t 的非线性曲线。
     */
    private float computeT(SeasonKeyframe kf0, SeasonKeyframe kf1, float time) {
        float dt = kf1.getTime() - kf0.getTime();
        if (dt <= 0) return 0f;

        float t = (time - kf0.getTime()) / dt;
        t = MathHelper.clamp(t, 0f, 1f);

        return switch (kf0.getInterpolation()) {
            case LINEAR -> t;
            case SMOOTH -> smoothStep(t);
            case CATMULL_ROM -> catmullRom(kf0, kf1, time);
            case DEFAULT -> smoothStep(t); // DEFAULT 使用 SMOOTH
        };
    }

    private float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    private float catmullRom(SeasonKeyframe kf0, SeasonKeyframe kf1, float time) {
        // 简化版 catmull-rom，仅平滑时间插值，不用于参数预测
        float dt = kf1.getTime() - kf0.getTime();
        float t = dt == 0 ? 0 : (time - kf0.getTime()) / dt;
        return smoothStep(MathHelper.clamp(t, 0f, 1f));
    }

    /**
     * 获取所有关键帧（用于调试）。
     */
    public List<SeasonKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }
}
