package com.chronoblocks.core.animation;

import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.animation.sequence.SequenceMode;
import com.chronoblocks.core.animation.sequence.SequenceParameters;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@code ClipAnimationConfig} 描述时间线片段的动画行为。
 * 
 * <p>此配置类用于控制片段中方块的出现/消失动画效果。
 * 所有运行时播放都应读取该对象的内容。
 * 
 * <p>与新方块动画引擎（BlockAnimationEngine）直接相关：
 * <ul>
 *   <li>AnimationMode：选择使用哪个动画引擎</li>
 *   <li>Duration：动画效果的持续时间</li>
 *   <li>Delay：动画开始前的延迟</li>
 *   <li>Params：传递给动画引擎的额外参数</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 * @since 1.0 (迁移自 animation.timeline 包)
 */
public final class ClipAnimationConfig implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private AnimationMode mode;
    private float duration;
    private float delay;
    private String easing;
    private String ordering; // 保留向后兼容，但建议使用 sequenceMode
    
    // ========== 序列策略配置（新增）==========
    private SequenceMode sequenceMode = SequenceMode.ONE_BY_ONE;
    private SequenceParameters sequenceParameters = new SequenceParameters();
    
    private final Map<String, Object> params = new HashMap<>();

    private ClipAnimationConfig(AnimationMode mode,
                                float duration,
                                float delay,
                                String easing,
                                String ordering) {
        this.mode = Objects.requireNonNullElse(mode, AnimationMode.INSTANT);
        this.duration = Math.max(0.05f, duration);
        this.delay = Math.max(0.0f, delay);
        this.easing = easing != null ? easing : "Linear";
        this.ordering = ordering != null ? ordering : "Sequential";
        this.sequenceMode = SequenceMode.ONE_BY_ONE;
        this.sequenceParameters = new SequenceParameters();
    }

    /**
     * 创建默认配置
     * 
     * @return 默认配置实例（瞬间出现，时长2秒）
     */
    public static ClipAnimationConfig defaultConfig() {
        return new ClipAnimationConfig(AnimationMode.INSTANT, 2.0f, 0.0f, "Linear", "Sequential");
    }

    /**
     * 从预设创建配置
     * 
     * @param mode 动画模式
     * @param suggestedDuration 建议的动画时长
     * @param delay 延迟
     * @param easing 缓动曲线
     * @param ordering 播放顺序
     * @return 配置实例
     */
    public static ClipAnimationConfig fromPreset(AnimationMode mode,
                                                 float suggestedDuration,
                                                 float delay,
                                                 String easing,
                                                 String ordering) {
        return new ClipAnimationConfig(mode, suggestedDuration, delay, easing, ordering);
    }

    /**
     * 复制配置
     * 
     * @return 新的配置实例，包含相同的值
     */
    public ClipAnimationConfig copy() {
        ClipAnimationConfig clone = new ClipAnimationConfig(mode, duration, delay, easing, ordering);
        clone.params.putAll(this.params);
        clone.sequenceMode = this.sequenceMode;
        clone.sequenceParameters = this.sequenceParameters != null ? this.sequenceParameters.copy() : new SequenceParameters();
        return clone;
    }

    // ========== Getters and Setters ==========

    public AnimationMode getMode() {
        return mode;
    }

    public void setMode(AnimationMode mode) {
        this.mode = Objects.requireNonNullElse(mode, AnimationMode.INSTANT);
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = Math.max(0.05f, duration);
    }

    public float getDelay() {
        return delay;
    }

    public void setDelay(float delay) {
        this.delay = Math.max(0.0f, delay);
    }

    public String getEasing() {
        return easing;
    }

    public void setEasing(String easing) {
        this.easing = easing != null ? easing : "Linear";
    }

    public String getOrdering() {
        return ordering;
    }

    public void setOrdering(String ordering) {
        this.ordering = ordering != null ? ordering : "Sequential";
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParam(String key, Object value) {
        if (key == null || key.isEmpty()) return;
        if (value == null) {
            params.remove(key);
        } else {
            params.put(key, value);
        }
    }

    public Object getParam(String key) {
        return params.get(key);
    }
    
    // ========== 序列策略配置（新增）==========
    
    /**
     * 获取序列模式
     * 
     * @return 序列模式
     */
    public SequenceMode getSequenceMode() {
        return sequenceMode != null ? sequenceMode : SequenceMode.ONE_BY_ONE;
    }
    
    /**
     * 设置序列模式
     * 
     * @param sequenceMode 序列模式
     */
    public void setSequenceMode(SequenceMode sequenceMode) {
        this.sequenceMode = sequenceMode != null ? sequenceMode : SequenceMode.ONE_BY_ONE;
    }
    
    /**
     * 获取序列参数
     * 
     * @return 序列参数
     */
    public SequenceParameters getSequenceParameters() {
        if (sequenceParameters == null) {
            sequenceParameters = new SequenceParameters();
        }
        return sequenceParameters;
    }
    
    /**
     * 设置序列参数
     * 
     * @param sequenceParameters 序列参数
     */
    public void setSequenceParameters(SequenceParameters sequenceParameters) {
        this.sequenceParameters = sequenceParameters != null ? sequenceParameters.copy() : new SequenceParameters();
    }
    
    @Override
    public ClipAnimationConfig clone() {
        return copy();
    }
}

