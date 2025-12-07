package com.chronoblocks.ui.timeline.playback.season;

import com.chronoblocks.ui.timeline.model.season.SeasonKeyframe;
import com.chronoblocks.ui.timeline.model.season.SeasonKeyframe.Interpolation;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * SeasonTrackSampler v1.1
 * 
 * <p>负责根据时间线时间（seconds）采样季节轨道，生成 SeasonSample。
 * 
 * <p>特点：
 * <ul>
 *   <li>支持 Linear / Smooth / Catmull-Rom 插值</li>
 *   <li>支持 DEFAULT 插值类型（使用轨道默认）</li>
 *   <li>支持单关键帧（无插值）</li>
 *   <li>完整插值所有季节参数（温度、湿度、雪线、颜色等）</li>
 *   <li>实现完整的 Catmull-Rom 样条插值（使用4个关键帧）</li>
 *   <li>允许未来扩展更多季节数据</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.1 - 改进算法、完整 Catmull-Rom 实现、更清晰的代码结构
 */
public class SeasonTrackSampler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SeasonTrackSampler.class);
    
    /** 当没有关键帧时返回默认采样 */
    private static final SeasonSample DEFAULT_SAMPLE = SeasonSample.defaultSample();
    
    private List<SeasonKeyframe> keyframes = new ArrayList<>();
    
    /**
     * 设置关键帧列表
     */
    public void setKeyframes(List<SeasonKeyframe> keyframes) {
        this.keyframes = new ArrayList<>(keyframes);
        this.keyframes.sort(Comparator.comparing(SeasonKeyframe::getTime));
        LOGGER.debug("[SeasonTrackSampler] 关键帧数量: {}", keyframes.size());
    }
    
    /**
     * 检查是否有关键帧。
     * 
     * <p>供 {@link TimelineSeasonController#hasOverride()} 使用，判断是否有季节覆盖。
     */
    public boolean hasKeyframes() {
        return !keyframes.isEmpty();
    }
    
    /**
     * 在指定时间采样季节参数
     * 
     * @param time 时间（秒）
     * @return 采样结果，如果无关键帧则返回 Optional.empty()
     */
    public Optional<SeasonSample> sample(float time) {
        if (keyframes.isEmpty()) {
            return Optional.empty();
        }
        
        SeasonSample result = sampleInternal(keyframes, time);
        return Optional.of(result);
    }
    
    // =====================================================================
    // 核心采样逻辑（内部方法）
    // =====================================================================
    
    /**
     * 内部采样方法（不返回 Optional，用于简化调用）
     */
    private static SeasonSample sampleInternal(List<SeasonKeyframe> frames, float t) {
        if (frames == null || frames.isEmpty()) {
            return DEFAULT_SAMPLE;
        }
        
        if (frames.size() == 1) {
            return sampleFromSingle(frames.get(0));
        }
        
        // 找到前后关键帧
        int idx = findKeyframeIndex(frames, t);
        SeasonKeyframe k0 = frames.get(idx);
        SeasonKeyframe k1 = frames.get(Math.min(idx + 1, frames.size() - 1));
        
        if (k0 == k1) {
            return sampleFromSingle(k0);
        }
        
        float span = k1.getTime() - k0.getTime();
        if (span <= 0.0001f) {
            return sampleFromSingle(k0);
        }
        
        float u = (t - k0.getTime()) / span;
        Interpolation mode = k0.getInterpolation();
        
        return switch (mode) {
            case DEFAULT -> {
                // DEFAULT 使用轨道默认插值类型（这里使用 CATMULL_ROM）
                yield catmullRom(frames, idx, t);
            }
            case LINEAR -> lerp(k0, k1, u);
            case SMOOTH -> smooth(k0, k1, u);
            case CATMULL_ROM -> catmullRom(frames, idx, t);
        };
    }
    
    // =====================================================================
    // 单关键帧
    // =====================================================================
    
    /**
     * 从单个关键帧生成采样（无插值）
     */
    private static SeasonSample sampleFromSingle(SeasonKeyframe k) {
        return new SeasonSample(
                k.getTemperature(),
                k.getHumidity(),
                k.getSnowlineHeight(),
                k.getGrassTint(),
                k.getFoliageTint(),
                k.getSkyTint(),
                k.getFogTint(),
                k.getLightTint()
        );
    }
    
    // =====================================================================
    // 关键帧查找
    // =====================================================================
    
    /**
     * 找到时间 t 所在的关键帧区间索引。
     * 返回左侧关键帧的索引，使得 frames[idx] <= t < frames[idx+1]（如果存在）。
     * 
     * @param frames 已排序的关键帧列表
     * @param t 当前时间（秒）
     * @return 关键帧索引
     */
    private static int findKeyframeIndex(List<SeasonKeyframe> frames, float t) {
        int size = frames.size();
        
        // 如果时间在第一个关键帧之前，返回第一个
        if (t <= frames.get(0).getTime()) {
            return 0;
        }
        
        // 如果时间在最后一个关键帧之后，返回最后一个
        if (t >= frames.get(size - 1).getTime()) {
            return size - 1;
        }
        
        // 查找区间
        for (int i = 0; i < size - 1; i++) {
            if (t < frames.get(i + 1).getTime()) {
                return i;
            }
        }
        
        return size - 1;
    }
    
    // =====================================================================
    // 插值实现
    // =====================================================================
    
    /**
     * 线性插值
     */
    private static SeasonSample lerp(SeasonKeyframe a, SeasonKeyframe b, float u) {
        float iu = 1f - u;
        
        return new SeasonSample(
                a.getTemperature() * iu + b.getTemperature() * u,
                a.getHumidity() * iu + b.getHumidity() * u,
                a.getSnowlineHeight() * iu + b.getSnowlineHeight() * u,
                lerpVec(a.getGrassTint(), b.getGrassTint(), u),
                lerpVec(a.getFoliageTint(), b.getFoliageTint(), u),
                lerpVec(a.getSkyTint(), b.getSkyTint(), u),
                lerpVec(a.getFogTint(), b.getFogTint(), u),
                lerpVec(a.getLightTint(), b.getLightTint(), u)
        );
    }
    
    /**
     * 平滑插值（SmoothStep - 缓入缓出）
     */
    private static SeasonSample smooth(SeasonKeyframe a, SeasonKeyframe b, float u) {
        // SmoothStep（缓入缓出）
        float s = u * u * (3f - 2f * u);
        return lerp(a, b, s);
    }
    
    // =====================================================================
    // Catmull-Rom 样条
    // =====================================================================
    
    /**
     * Catmull-Rom 样条插值（使用4个关键帧）
     * 
     * <p>需要前后各一个关键帧作为控制点，实现平滑的曲线过渡。
     * 如果边界没有足够的关键帧，会重复使用边界关键帧。
     */
    private static SeasonSample catmullRom(List<SeasonKeyframe> frames, int idx, float t) {
        // 获取4个控制点：p0, p1, p2, p3
        // p1 和 p2 是插值区间的两端
        SeasonKeyframe p0 = idx > 0 ? frames.get(idx - 1) : frames.get(idx);
        SeasonKeyframe p1 = frames.get(idx);
        SeasonKeyframe p2 = frames.get(Math.min(idx + 1, frames.size() - 1));
        SeasonKeyframe p3 = idx + 2 < frames.size() ? frames.get(Math.min(idx + 2, frames.size() - 1)) : p2;
        
        float t1 = p1.getTime();
        float t2 = p2.getTime();
        float u = (t - t1) / (t2 - t1 + 1e-6f); // 添加小值避免除零
        
        return new SeasonSample(
                catmull(p0.getTemperature(), p1.getTemperature(), p2.getTemperature(), p3.getTemperature(), u),
                catmull(p0.getHumidity(), p1.getHumidity(), p2.getHumidity(), p3.getHumidity(), u),
                catmull(p0.getSnowlineHeight(), p1.getSnowlineHeight(), p2.getSnowlineHeight(), p3.getSnowlineHeight(), u),
                catmullVec(p0.getGrassTint(), p1.getGrassTint(), p2.getGrassTint(), p3.getGrassTint(), u),
                catmullVec(p0.getFoliageTint(), p1.getFoliageTint(), p2.getFoliageTint(), p3.getFoliageTint(), u),
                catmullVec(p0.getSkyTint(), p1.getSkyTint(), p2.getSkyTint(), p3.getSkyTint(), u),
                catmullVec(p0.getFogTint(), p1.getFogTint(), p2.getFogTint(), p3.getFogTint(), u),
                catmullVec(p0.getLightTint(), p1.getLightTint(), p2.getLightTint(), p3.getLightTint(), u)
        );
    }
    
    // =====================================================================
    // 基础数学工具
    // =====================================================================
    
    /**
     * Vector3f 线性插值
     */
    private static Vector3f lerpVec(Vector3f a, Vector3f b, float u) {
        float iu = 1f - u;
        return new Vector3f(
                a.x * iu + b.x * u,
                a.y * iu + b.y * u,
                a.z * iu + b.z * u
        );
    }
    
    /**
     * Catmull-Rom 样条插值（标量）
     * 
     * <p>使用标准的 Catmull-Rom 公式，实现平滑的曲线插值。
     */
    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (
                2 * p1 +
                (-p0 + p2) * t +
                (2*p0 - 5*p1 + 4*p2 - p3) * t2 +
                (-p0 + 3*p1 - 3*p2 + p3) * t3
        );
    }
    
    /**
     * Catmull-Rom 样条插值（Vector3f）
     */
    private static Vector3f catmullVec(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float u) {
        return new Vector3f(
                catmull(p0.x, p1.x, p2.x, p3.x, u),
                catmull(p0.y, p1.y, p2.y, p3.y, u),
                catmull(p0.z, p1.z, p2.z, p3.z, u)
        );
    }
    
    /**
     * 获取所有关键帧（用于调试）
     */
    public List<SeasonKeyframe> getKeyframes() {
        return new ArrayList<>(keyframes);
    }
    
    /**
     * 检查是否有关键帧
     */
    public boolean isEmpty() {
        return keyframes.isEmpty();
    }
}

