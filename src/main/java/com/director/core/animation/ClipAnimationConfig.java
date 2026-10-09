package com.director.core.animation;

import com.director.core.container.AnimationMode;
import com.director.core.animation.sequence.SequenceMode;
import com.director.core.animation.sequence.SequenceParameters;
import com.director.core.animation.effect.EffectInstance;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
 * @author Director Team
 * @version 2.0
 * @since 1.0 (迁移自 animation.timeline 包)
 */
public final class ClipAnimationConfig implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private AnimationMode mode;
    public enum AnimationScope { PER_BLOCK, WHOLE_CONTAINER }
    private AnimationScope scope = AnimationScope.PER_BLOCK;
    private WholeContainerAnimationConfig wholeContainer = new WholeContainerAnimationConfig();
    /**
     * 动画阶段：
     * - ENTER：出现片段（默认，向后兼容旧存档）
     * - EXIT：擦除片段（方块先执行退出动画，再被移除/置 AIR）
     */
    public enum AnimationPhase {
        ENTER,
        EXIT
    }

    private AnimationPhase phase;
    private float duration;
    private float delay;
    private String easing; // 缓动函数名称（如 "EASE_OUT_BOUNCE"）
    private String ordering; // 保留向后兼容，但建议使用 sequenceMode
    
    // ========== 序列策略配置（新增）==========
    private SequenceMode sequenceMode;
    private SequenceParameters sequenceParameters;
    // 遮挡时是否允许透明渲染（片段级开关）
    private boolean occlusionTransparencyEnabled = true;

    static {
        new SequenceParameters();
    }

    private final Map<String, Object> params = new HashMap<>();
    
    // ========== 特效系统配置 ==========
    /** 挂在此 Clip 上的所有特效实例 */
    private final List<EffectInstance> effects = new ArrayList<>();

    private ClipAnimationConfig(AnimationMode mode,
                                float duration,
                                float delay,
                                String easing,
                                String ordering) {
        this.mode = Objects.requireNonNullElse(mode, AnimationMode.INSTANT);
        this.phase = AnimationPhase.ENTER;
        this.duration = Math.max(0.05f, duration);
        this.delay = Math.max(0.0f, delay);
        // 统一内部存储为 EasingFunction 枚举名（仍然兼容旧存档 "Linear"）
        this.easing = easing != null ? easing : com.director.core.animation.easing.EasingFunction.LINEAR.name();
        this.ordering = ordering != null ? ordering : "Sequential";
        this.sequenceMode = SequenceMode.EVALUATOR_BASED;
        this.sequenceParameters = new SequenceParameters();
    }

    /**
     * 创建默认配置
     * 
     * @return 默认配置实例（瞬间出现，时长2秒）
     */
    public static ClipAnimationConfig defaultConfig() {
        return new ClipAnimationConfig(AnimationMode.INSTANT, 2.0f, 0.0f, com.director.core.animation.easing.EasingFunction.LINEAR.name(), "Sequential");
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
     * 复制配置（包括特效列表）
     * 
     * @return 新的配置实例，包含相同的值
     */
    public ClipAnimationConfig copy() {
        ClipAnimationConfig clone = new ClipAnimationConfig(mode, duration, delay, easing, ordering);
        clone.params.putAll(this.params);
        clone.phase = this.phase;
        clone.scope = getScope();
        clone.wholeContainer = getWholeContainer().copy();
        clone.sequenceMode = this.sequenceMode;
        clone.sequenceParameters = this.sequenceParameters != null ? this.sequenceParameters.copy() : new SequenceParameters();
        clone.occlusionTransparencyEnabled = this.occlusionTransparencyEnabled;
        
        // 复制特效列表
        for (EffectInstance effect : this.effects) {
            clone.effects.add(effect.copy());
        }
        
        return clone;
    }

    // ========== Getters and Setters ==========

    public AnimationMode getMode() {
        return mode;
    }

    public AnimationScope getScope() {
        return scope != null ? scope : AnimationScope.PER_BLOCK;
    }

    public void setScope(AnimationScope scope) {
        this.scope = scope != null ? scope : AnimationScope.PER_BLOCK;
    }

    public boolean isWholeContainer() {
        return getScope() == AnimationScope.WHOLE_CONTAINER;
    }

    public WholeContainerAnimationConfig getWholeContainer() {
        if (wholeContainer == null) wholeContainer = new WholeContainerAnimationConfig();
        return wholeContainer;
    }

    public void setWholeContainer(WholeContainerAnimationConfig value) {
        wholeContainer = value != null ? value.copy() : new WholeContainerAnimationConfig();
    }

    public void setMode(AnimationMode mode) {
        this.mode = Objects.requireNonNullElse(mode, AnimationMode.INSTANT);
    }

    public AnimationPhase getPhase() {
        return phase != null ? phase : AnimationPhase.ENTER;
    }

    public void setPhase(AnimationPhase phase) {
        this.phase = phase != null ? phase : AnimationPhase.ENTER;
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        // 下限 0.05s 防止除零；上限 300s 防止手动输入极端值导致动画卡在中间状态
        this.duration = Math.max(0.05f, Math.min(duration, 300.0f));
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
        this.easing = easing != null ? easing : com.director.core.animation.easing.EasingFunction.LINEAR.name();
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
     * @return 序列模式（始终为 EVALUATOR_BASED）
     */
    public SequenceMode getSequenceMode() {
        return sequenceMode != null ? sequenceMode : SequenceMode.EVALUATOR_BASED;
    }
    
    /**
     * 设置序列模式
     * 
     * @param sequenceMode 序列模式（只接受 EVALUATOR_BASED）
     */
    public void setSequenceMode(SequenceMode sequenceMode) {
        this.sequenceMode = sequenceMode != null ? sequenceMode : SequenceMode.EVALUATOR_BASED;
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

    /**
     * 是否启用遮挡时透明渲染（片段级）
     */
    public boolean isOcclusionTransparencyEnabled() {
        return occlusionTransparencyEnabled;
    }

    /**
     * 设置遮挡时透明渲染开关
     */
    public void setOcclusionTransparencyEnabled(boolean enabled) {
        this.occlusionTransparencyEnabled = enabled;
    }
    
    // ========== 特效系统访问方法 ==========
    
    /**
     * 获取特效列表
     * 
     * @return 特效实例列表
     */
    public List<EffectInstance> getEffects() {
        return effects;
    }
    
    /**
     * 获取特效列表的只读视图
     * 
     * @return 不可修改的特效列表视图
     */
    public List<EffectInstance> getEffectsView() {
        return Collections.unmodifiableList(effects);
    }
    
    /**
     * 添加特效实例
     * 
     * @param instance 特效实例
     */
    public void addEffect(EffectInstance instance) {
        if (instance != null) {
            effects.add(instance);
        }
    }
    
    /**
     * 简单辅助方法，方便在代码 / 预设中挂特效（链式调用）
     * 
     * @param effect 特效实例
     * @return this（用于链式调用）
     */
    public ClipAnimationConfig addEffectChain(EffectInstance effect) {
        addEffect(effect);
        return this;
    }
    
    /**
     * 移除特效实例
     * 
     * @param instance 要移除的特效实例
     */
    public void removeEffect(EffectInstance instance) {
        effects.remove(instance);
    }
    
    /**
     * 清空所有特效
     */
    public void clearEffects() {
        effects.clear();
    }
    
    @Override
    public ClipAnimationConfig clone() {
        return copy();
    }
}
