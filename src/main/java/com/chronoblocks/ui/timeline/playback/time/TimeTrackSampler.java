package com.chronoblocks.ui.timeline.playback.time;

import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.model.time.TimeKeyframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * TimeTrackSampler v1.0
 *
 * <p>负责对单个时间轨道进行采样。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>维护一组 TimeKeyframe（通常来自某条 TIME 轨道）</li>
 *   <li>根据关键帧的插值类型进行世界时间和太阳角度的插值</li>
 *   <li>处理时间循环（24000 tick → 0 tick）和角度循环（359° → 1°）</li>
 *   <li>提供 sample(time) 方法，返回 TimeSample</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>只负责采样逻辑，不关心轨道管理</li>
 *   <li>支持自然时间模式和自定义太阳模式</li>
 * </ul>
 */
public final class TimeTrackSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeTrackSampler.class);

    /** 排序后的关键帧列表 */
    private final List<TimeKeyframe> keyframes = new ArrayList<>();
    
    /** Minecraft 一天的总 tick 数 */
    private static final long DAY_LENGTH_TICKS = 24000L;
    
    /** 角度循环的周期（度） */
    private static final float ANGLE_CYCLE = 360.0f;

    public TimeTrackSampler() {
    }

    /**
     * 设置关键帧列表（会自动排序）
     */
    public void setKeyframes(Collection<TimeKeyframe> frames) {
        keyframes.clear();
        if (frames != null && !frames.isEmpty()) {
            keyframes.addAll(frames);
            keyframes.sort(Comparator.comparingDouble(TimeKeyframe::getTime));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[TimeTrackSampler] 关键帧数量: {}", keyframes.size());
            }
        }
    }

    /**
     * 获取当前关键帧列表（只读）
     */
    public List<TimeKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    /**
     * 在指定时间采样时间状态
     * 
     * @param timeSeconds 时间（秒）
     * @return 如果成功采样则返回 TimeSample，否则返回 empty
     */
    public Optional<TimeSample> sample(float timeSeconds) {
        if (keyframes.isEmpty()) {
            return Optional.empty();
        }

        if (keyframes.size() == 1) {
            return Optional.of(toSample(keyframes.getFirst()));
        }

        // 找到 time 所在的关键帧区间 [prev, next]
        int nextIndex = findNextKeyframeIndex(timeSeconds);
        int prevIndex = nextIndex - 1;

        if (prevIndex < 0) {
            // 在第一个关键帧之前，直接使用第一个
            return Optional.of(toSample(keyframes.getFirst()));
        }
        if (nextIndex >= keyframes.size()) {
            // 超出最后一帧之后，使用最后一帧
            return Optional.of(toSample(keyframes.getLast()));
        }

        TimeKeyframe k0 = keyframes.get(prevIndex);
        TimeKeyframe k1 = keyframes.get(nextIndex);

        float t0 = k0.getTime();
        float t1 = k1.getTime();
        if (t1 <= t0 + 1e-6f) {
            return Optional.of(toSample(k0));
        }

        float localT = (timeSeconds - t0) / (t1 - t0);
        localT = Math.max(0.0f, Math.min(1.0f, localT));

        // 获取插值类型
        CameraKeyframe.SegmentPathType segmentType = resolveSegmentType(k0);

        // STEP：保持前一个关键帧的值
        if (segmentType == CameraKeyframe.SegmentPathType.STEP) {
            return Optional.of(toSample(k0));
        }

        // 根据插值类型进行采样
        return switch (segmentType) {
            case LINEAR -> Optional.of(sampleLinear(k0, k1, localT));
            case CATMULL_ROM -> Optional.of(sampleCatmullRom(prevIndex, nextIndex, localT));
            case BEZIER -> Optional.of(sampleLinear(k0, k1, localT)); // TODO: 实现 Bezier 插值
            default -> Optional.of(sampleLinear(k0, k1, localT)); // 默认使用线性
        };
    }

    // ======================= 采样实现 =======================

    private int findNextKeyframeIndex(float timeSeconds) {
        for (int i = 0; i < keyframes.size(); i++) {
            if (timeSeconds <= keyframes.get(i).getTime()) {
                return i;
            }
        }
        return keyframes.size();
    }

    /**
     * 线性插值
     */
    private TimeSample sampleLinear(TimeKeyframe k0, TimeKeyframe k1, float t) {
        // 世界时间插值（处理循环）
        long worldTime = lerpTime(k0.getWorldTimeTicks(), k1.getWorldTimeTicks(), t);
        
        // 如果两个关键帧都是自然时间模式，或者都是自定义太阳模式，则统一处理
        if (k0.isUseNaturalTime() && k1.isUseNaturalTime()) {
            // 两者都是自然时间模式，直接插值时间
            return TimeSample.fromNaturalTime(worldTime);
        } else if (!k0.isUseNaturalTime() && !k1.isUseNaturalTime()) {
            // 两者都是自定义太阳模式，插值太阳角度
            float sunElevation = lerpFloat(k0.getSunElevation(), k1.getSunElevation(), t);
            float sunAzimuth = lerpAngle(k0.getSunAzimuth(), k1.getSunAzimuth(), t);
            return TimeSample.fromCustomSun(worldTime, sunElevation, sunAzimuth);
        } else {
            // 混合模式：如果前一个是自然时间，后一个是自定义，则使用后一个
            // 如果前一个是自定义，后一个是自然时间，则保持前一个的自定义值
            if (!k0.isUseNaturalTime()) {
                // 保持前一个的自定义太阳角度
                return TimeSample.fromCustomSun(worldTime, k0.getSunElevation(), k0.getSunAzimuth());
            } else {
                // 使用后一个的自定义太阳角度
                return TimeSample.fromCustomSun(worldTime, k1.getSunElevation(), k1.getSunAzimuth());
            }
        }
    }

    /**
     * Catmull-Rom 样条插值
     */
    private TimeSample sampleCatmullRom(int prevIndex, int nextIndex, float t) {
        // 找到前后控制点
        int i0 = Math.max(0, prevIndex - 1);
        int i3 = Math.min(keyframes.size() - 1, nextIndex + 1);

        TimeKeyframe p0 = keyframes.get(i0);
        TimeKeyframe p1 = keyframes.get(prevIndex);
        TimeKeyframe p2 = keyframes.get(nextIndex);
        TimeKeyframe p3 = keyframes.get(i3);

        // 世界时间插值（Catmull-Rom）
        long worldTime = catmullRomTime(
                p0.getWorldTimeTicks(),
                p1.getWorldTimeTicks(),
                p2.getWorldTimeTicks(),
                p3.getWorldTimeTicks(),
                t
        );

        // 简化：如果都是自然时间模式，返回自然时间
        if (p1.isUseNaturalTime() && p2.isUseNaturalTime()) {
            return TimeSample.fromNaturalTime(worldTime);
        } else {
            // 使用线性插值太阳角度（Catmull-Rom 角度插值更复杂，这里简化）
            float sunElevation = lerpFloat(p1.getSunElevation(), p2.getSunElevation(), t);
            float sunAzimuth = lerpAngle(p1.getSunAzimuth(), p2.getSunAzimuth(), t);
            return TimeSample.fromCustomSun(worldTime, sunElevation, sunAzimuth);
        }
    }

    /**
     * 将关键帧转换为采样结果
     */
    private TimeSample toSample(TimeKeyframe keyframe) {
        if (keyframe.isUseNaturalTime()) {
            return TimeSample.fromNaturalTime(keyframe.getWorldTimeTicks());
        } else {
            return TimeSample.fromCustomSun(
                    keyframe.getWorldTimeTicks(),
                    keyframe.getSunElevation(),
                    keyframe.getSunAzimuth()
            );
        }
    }

    // ======================= 辅助方法 =======================

    /**
     * 解析段插值类型
     */
    private CameraKeyframe.SegmentPathType resolveSegmentType(TimeKeyframe k0) {
        CameraKeyframe.SegmentPathType type = k0.toSegmentPathType();
        if (type == CameraKeyframe.SegmentPathType.DEFAULT) {
            // 默认使用 Catmull-Rom
            return CameraKeyframe.SegmentPathType.CATMULL_ROM;
        }
        return type;
    }

    /**
     * 线性插值世界时间（处理 24000 → 0 的循环）
     */
    private long lerpTime(long t0, long t1, float t) {
        // 计算差值，处理循环
        long diff = t1 - t0;
        long halfCycle = DAY_LENGTH_TICKS / 2;
        
        // 如果差值大于半天，可能走反向更短
        if (diff > halfCycle) {
            diff -= DAY_LENGTH_TICKS;
        } else if (diff < -halfCycle) {
            diff += DAY_LENGTH_TICKS;
        }
        
        long result = t0 + (long)(diff * t);
        // 规范化到 0~24000 范围（但不强制，允许超出）
        while (result < 0) result += DAY_LENGTH_TICKS;
        while (result >= DAY_LENGTH_TICKS) result -= DAY_LENGTH_TICKS;
        
        return result;
    }

    /**
     * Catmull-Rom 插值世界时间
     */
    private long catmullRomTime(long p0, long p1, long p2, long p3, float t) {
        // 简化：先规范化到 0~24000，然后插值，最后规范化结果
        p0 = normalizeTime(p0);
        p1 = normalizeTime(p1);
        p2 = normalizeTime(p2);
        p3 = normalizeTime(p3);
        
        // Catmull-Rom 系数
        float t2 = t * t;
        float t3 = t2 * t;
        
        float c0 = -0.5f * t3 + t2 - 0.5f * t;
        float c1 = 1.5f * t3 - 2.5f * t2 + 1.0f;
        float c2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
        float c3 = 0.5f * t3 - 0.5f * t2;
        
        // 计算差值（处理循环）
        float v0 = lerpTimeFloat(p1, p0);
        float v1 = p1;
        float v2 = p2;
        float v3 = lerpTimeFloat(p2, p3);
        
        float result = c0 * v0 + c1 * v1 + c2 * v2 + c3 * v3;
        return normalizeTime((long)result);
    }

    /**
     * 规范化时间到 0~24000 范围
     */
    private long normalizeTime(long time) {
        time = time % DAY_LENGTH_TICKS;
        if (time < 0) time += DAY_LENGTH_TICKS;
        return time;
    }

    /**
     * 处理时间循环的浮点差值
     */
    private float lerpTimeFloat(long from, long to) {
        long diff = to - from;
        if (diff > DAY_LENGTH_TICKS / 2) diff -= DAY_LENGTH_TICKS;
        if (diff < -DAY_LENGTH_TICKS / 2) diff += DAY_LENGTH_TICKS;
        return from + diff;
    }

    /**
     * 线性插值角度（处理 359° → 1° 的循环）
     */
    private float lerpAngle(float a0, float a1, float t) {
        float diff = a1 - a0;
        
        // 处理角度循环：选择最短路径
        if (diff > 180.0f) {
            diff -= ANGLE_CYCLE;
        } else if (diff < -180.0f) {
            diff += ANGLE_CYCLE;
        }
        
        float result = a0 + diff * t;
        
        // 规范化到 0~360
        while (result < 0.0f) result += ANGLE_CYCLE;
        while (result >= ANGLE_CYCLE) result -= ANGLE_CYCLE;
        
        return result;
    }

    /**
     * 线性插值浮点数
     */
    private float lerpFloat(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ======================= 调试方法 =======================

    /**
     * 获取所有关键帧的采样结果（用于调试和可视化）
     * 
     * @return 所有关键帧对应的 TimeSample 列表
     */
    public List<TimeSample> getSamplesForDebug() {
        List<TimeSample> samples = new ArrayList<>();
        for (TimeKeyframe keyframe : keyframes) {
            samples.add(toSample(keyframe));
        }
        return samples;
    }
}
