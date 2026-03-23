package com.director.core.container;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@code ContainerAnimationPreset} 用于描述容器在新建时间线片段时所使用的默认动画配置。
 * <p>
 * 该预设不会直接参与运行时播放，只作为 TimelineClip 初始化的种子数据。
 * </p>
 */
public final class ContainerAnimationPreset implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private AnimationMode mode;
    private float suggestedDuration;
    private float delay;
    private String easing;
    private String ordering;
    private final Map<String, Object> params = new HashMap<>();

    public ContainerAnimationPreset() {
        this(AnimationMode.INSTANT, 2.0f, 0.0f, com.director.core.animation.easing.EasingFunction.LINEAR.name(), "Sequential");
    }

    public ContainerAnimationPreset(AnimationMode mode,
                                    float suggestedDuration,
                                    float delay,
                                    String easing,
                                    String ordering) {
        this.mode = Objects.requireNonNullElse(mode, AnimationMode.INSTANT);
        this.suggestedDuration = Math.max(0.05f, suggestedDuration);
        this.delay = Math.max(0.0f, delay);
        this.easing = easing != null ? easing : com.director.core.animation.easing.EasingFunction.LINEAR.name();
        this.ordering = ordering != null ? ordering : "Sequential";
    }

    public static ContainerAnimationPreset defaultPreset() {
        return new ContainerAnimationPreset();
    }

    public ContainerAnimationPreset copy() {
        ContainerAnimationPreset clone = new ContainerAnimationPreset(mode, suggestedDuration, delay, easing, ordering);
        clone.params.putAll(this.params);
        return clone;
    }

    public AnimationMode getMode() {
        return mode;
    }

    public void setMode(AnimationMode mode) {
        this.mode = Objects.requireNonNullElse(mode, AnimationMode.INSTANT);
    }

    public float getSuggestedDuration() {
        return suggestedDuration;
    }

    public void setSuggestedDuration(float suggestedDuration) {
        this.suggestedDuration = Math.max(0.05f, suggestedDuration);
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

    @Override
    public ContainerAnimationPreset clone() {
        return copy();
    }
}

