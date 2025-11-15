package com.chronoblocks.animation.timeline;

import com.chronoblocks.core.container.AnimationMode;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@code ClipAnimationConfig} 描述时间线片段的动画行为。
 * 所有运行时播放都应读取该对象的内容。
 */
public final class ClipAnimationConfig implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private AnimationMode mode;
    private float duration;
    private float delay;
    private String easing;
    private String ordering;
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
    }

    public static ClipAnimationConfig defaultConfig() {
        return new ClipAnimationConfig(AnimationMode.INSTANT, 2.0f, 0.0f, "Linear", "Sequential");
    }

    public static ClipAnimationConfig fromPreset(AnimationMode mode,
                                                 float suggestedDuration,
                                                 float delay,
                                                 String easing,
                                                 String ordering) {
        return new ClipAnimationConfig(mode, suggestedDuration, delay, easing, ordering);
    }

    public ClipAnimationConfig copy() {
        ClipAnimationConfig clone = new ClipAnimationConfig(mode, duration, delay, easing, ordering);
        clone.params.putAll(this.params);
        return clone;
    }

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

    @Override
    public ClipAnimationConfig clone() {
        return copy();
    }
}


