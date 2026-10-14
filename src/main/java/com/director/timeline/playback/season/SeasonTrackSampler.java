package com.director.timeline.playback.season;

import com.director.timeline.model.season.SeasonKeyframe;
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
 * @author Director Team
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
            this.keyframes.sort(Comparator.comparingDouble(SeasonKeyframe::getTime).thenComparing(SeasonKeyframe::getId));
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
    public void setFromTrack(com.director.timeline.model.TimelineTrack track) {
        keyframes.clear();
        if (track == null) return;

        for (com.director.timeline.model.TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof SeasonKeyframe sk) {
                keyframes.add(sk);
            }
        }

        keyframes.sort(Comparator.comparingDouble(SeasonKeyframe::getTime).thenComparing(SeasonKeyframe::getId));
    }

    /**
     * 设置关键帧列表（向后兼容）。
     * 
     * @deprecated 使用 {@link #setFromTrack(com.director.timeline.model.TimelineTrack)} 代替
     */
    @Deprecated
    public void setKeyframes(List<SeasonKeyframe> keyframes) {
        this.keyframes.clear();
        if (keyframes != null) {
            this.keyframes.addAll(keyframes);
            this.keyframes.sort(Comparator.comparingDouble(SeasonKeyframe::getTime).thenComparing(SeasonKeyframe::getId));
        }
    }

    /**
     * 在给定时间采样。
     * 如果没有任何关键帧，返回 disabled()。
     */
    public SeasonSample sampleAt(float timeSeconds) {
        timeSeconds = Float.isFinite(timeSeconds) ? Math.max(0, timeSeconds) : 0;
        SeasonSample out = new SeasonSample();

        if (keyframes.isEmpty()) {
            // 无关键帧 → 默认世界
            out.enabled = false;
            return sanitized(out);
        }

        // 单关键帧 → 直接应用，无需插值
        if (keyframes.size() == 1) {
            return toSample(keyframes.getFirst());
        }

        // 找到 kf0 <= time <= kf1
        SeasonKeyframe kf0 = keyframes.getFirst();
        SeasonKeyframe kf1 = keyframes.getLast();

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
            blend(kf0, kf1, t, k -> k.getHumidity()),
            0f, 1f
        );
        out.snowlineHeight = blend(kf0, kf1, t, k -> k.getSnowlineHeight());

        // ========== 颜色插值 ==========
        out.grassTint.set(
            blend(kf0, kf1, t, k -> k.getGrassTint().x),
            blend(kf0, kf1, t, k -> k.getGrassTint().y),
            blend(kf0, kf1, t, k -> k.getGrassTint().z)
        );

        out.foliageTint.set(
            blend(kf0, kf1, t, k -> k.getFoliageTint().x),
            blend(kf0, kf1, t, k -> k.getFoliageTint().y),
            blend(kf0, kf1, t, k -> k.getFoliageTint().z)
        );

        out.skyTint.set(
            blend(kf0, kf1, t, k -> k.getSkyTint().x),
            blend(kf0, kf1, t, k -> k.getSkyTint().y),
            blend(kf0, kf1, t, k -> k.getSkyTint().z)
        );

        out.fogTint.set(
            blend(kf0, kf1, t, k -> k.getFogTint().x),
            blend(kf0, kf1, t, k -> k.getFogTint().y),
            blend(kf0, kf1, t, k -> k.getFogTint().z)
        );

        out.lightTint.set(
            blend(kf0, kf1, t, k -> k.getLightTint().x),
            blend(kf0, kf1, t, k -> k.getLightTint().y),
            blend(kf0, kf1, t, k -> k.getLightTint().z)
        );

        // ======================================================
        // ❄❄❄ 雪覆盖 / 堆积插值
        // ======================================================
        // 映射：snowCoverageStrength → snowAmount
        out.snowAmount = MathHelper.clamp(
            blend(kf0, kf1, t, k -> k.getSnowAmount()),
            0f, 1f
        );

        // 映射：snowLayerDepth → snowThickness (int)
        // 注意：snowThickness 是 int [0, 10]，需要四舍五入
        float snowThicknessFloat = blend(kf0, kf1, t, k -> k.getSnowThickness());
        out.snowThickness = Math.round(MathHelper.clamp(snowThicknessFloat, 0f, 10f));

        // ================================
        // 🧊 冰冻强度插值
        // ================================
        // 映射：iceCoverageStrength → freezeStrength
        // 冰、雪与颜色遵循相同的关键帧插值模式。
        out.freezeStrength = MathHelper.clamp(
            blend(kf0, kf1, t, k -> k.getFreezeStrength()),
            0f, 1f
        );

        return sanitized(out);
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

        return sanitized(s);
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
            case CATMULL_ROM -> t;
            case DEFAULT -> smoothStep(t); // DEFAULT 使用 SMOOTH
        };
    }

    private float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Hermite form of Catmull-Rom: neighbour values supply the endpoint tangents. */
    private float blend(SeasonKeyframe a, SeasonKeyframe b, float t,
                        java.util.function.ToDoubleFunction<SeasonKeyframe> value) {
        float av = finite(value.applyAsDouble(a)), bv = finite(value.applyAsDouble(b));
        if (a.getInterpolation() != SeasonKeyframe.Interpolation.CATMULL_ROM) return MathHelper.lerp(t, av, bv);
        int i = keyframes.indexOf(a);
        SeasonKeyframe prev = i > 0 ? keyframes.get(i-1) : a;
        SeasonKeyframe next = i+2 < keyframes.size() ? keyframes.get(i+2) : b;
        float dt = b.getTime()-a.getTime();
        float m1 = (bv-finite(value.applyAsDouble(prev))) / Math.max(.0001f,b.getTime()-prev.getTime()) * dt;
        float m2 = (finite(value.applyAsDouble(next))-av) / Math.max(.0001f,next.getTime()-a.getTime()) * dt;
        float tt=t*t, ttt=tt*t;
        return finite((2*ttt-3*tt+1)*av+(ttt-2*tt+t)*m1+(-2*ttt+3*tt)*bv+(ttt-tt)*m2);
    }
    private static float finite(double value) { return Double.isFinite(value) && Math.abs(value)<=Float.MAX_VALUE ? (float)value : 0; }
    private static SeasonSample sanitized(SeasonSample s) {
        s.humidity=MathHelper.clamp(finite(s.humidity),0,1);
        s.snowAmount=MathHelper.clamp(finite(s.snowAmount),0,1);
        s.freezeStrength=MathHelper.clamp(finite(s.freezeStrength),0,1);
        s.snowlineHeight=finite(s.snowlineHeight);
        for(var tint:List.of(s.grassTint,s.foliageTint,s.skyTint,s.fogTint,s.lightTint))
            tint.set(MathHelper.clamp(finite(tint.x),-.99f,3),MathHelper.clamp(finite(tint.y),-.99f,3),MathHelper.clamp(finite(tint.z),-.99f,3));
        return s;
    }

    /**
     * 获取所有关键帧（用于调试）。
     */
    public List<SeasonKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }
}
