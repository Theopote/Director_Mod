package com.chronoblocks.core.animation.effect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 绑定在 ClipAnimationConfig 上的特效实例
 * 
 * <p>类似"这段动画里挂一个 DustBurst，延迟 0.1s，持续 0.3s，强度=1.5…"
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class EffectInstance {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectInstance.class);
    
    private final UUID id;                // UI 用的稳定 ID
    
    /** 特效类型（枚举） */
    private EffectType type;
    
    /** 是否启用 */
    private boolean enabled = true;
    
    /** 是否标记为删除（UI 用，用于延迟删除） */
    private boolean markedForRemoval = false;
    
    /** 相对于单个方块动画起点的偏移（归一化，0..1） */
    private float startOffset = 0.0f;
    
    /** 特效自身持续时长（归一化，0..1），<=0 表示继承动画 duration */
    private float duration = 0.0f;
    
    /** 参数字典，用于不同 EffectType 自己解析 */
    private final Map<String, Object> params = new HashMap<>();

    /**
     * 特效作用阶段：
     * - ENTER：仅在“出现片段”生效
     * - EXIT：仅在“擦除片段”生效
     * - BOTH：两者都生效（默认，保持旧存档行为）
     */
    public enum EffectPhase {
        ENTER,
        EXIT,
        BOTH
    }

    private EffectPhase phase = EffectPhase.BOTH;

    /**
     * 触发模式：
     * - CONTINUOUS：时间窗口内每帧都会调用（适合持续光效/持续粒子）
     * - ONE_SHOT：进入时间窗口时只触发一次（适合爆裂/闪白光等）
     */
    public enum TriggerMode {
        CONTINUOUS,
        ONE_SHOT
    }

    private TriggerMode triggerMode = TriggerMode.CONTINUOUS;
    
    public EffectInstance(@NotNull EffectType type) {
        this.id = UUID.randomUUID();
        this.type = type;
    }
    
    // --------- 基本字段 ---------
    
    public UUID getId() {
        return id;
    }
    
    @NotNull
    public EffectType getType() {
        return type;
    }
    
    public void setType(@NotNull EffectType type) {
        this.type = type;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isMarkedForRemoval() {
        return markedForRemoval;
    }
    
    public void setMarkedForRemoval(boolean markedForRemoval) {
        this.markedForRemoval = markedForRemoval;
    }
    
    public float getStartOffset() {
        return startOffset;
    }
    
    public void setStartOffset(float startOffset) {
        this.startOffset = Math.max(0.0f, startOffset);
    }
    
    public float getDuration() {
        return duration;
    }
    
    public void setDuration(float duration) {
        this.duration = duration;
    }

    @NotNull
    public EffectPhase getPhase() {
        return phase != null ? phase : EffectPhase.BOTH;
    }

    public void setPhase(@Nullable EffectPhase phase) {
        this.phase = phase != null ? phase : EffectPhase.BOTH;
    }

    @NotNull
    public TriggerMode getTriggerMode() {
        return triggerMode != null ? triggerMode : TriggerMode.CONTINUOUS;
    }

    public void setTriggerMode(@Nullable TriggerMode triggerMode) {
        this.triggerMode = triggerMode != null ? triggerMode : TriggerMode.CONTINUOUS;
    }
    
    @NotNull
    public Map<String, Object> getParams() {
        return params;
    }
    
    // --------- 参数工具方法 ---------
    
    public float getFloat(String key, float defaultValue) {
        Object v = params.get(key);
        switch (v) {
            case null -> {
                return defaultValue;
            }
            case Number n -> {
                return n.floatValue();
            }
            case String s -> {
                try {
                    return Float.parseFloat(s);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Effect param [{}] parseFloat 失败: {}", key, s);
                    return defaultValue;
                }
            }
            default -> {
            }
        }

        LOGGER.warn("Effect param [{}] 类型不匹配: {}", key, v.getClass().getSimpleName());
        return defaultValue;
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        Object v = params.get(key);
        return switch (v) {
            case null -> defaultValue;
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            default -> defaultValue;
        };
    }
    
    @Nullable
    public String getString(String key, @Nullable String defaultValue) {
        Object v = params.get(key);
        return v == null ? defaultValue : v.toString();
    }
    
    public void setFloat(String key, float value) {
        params.put(key, value);
    }
    
    public void setBoolean(String key, boolean value) {
        params.put(key, value);
    }
    
    public void setString(String key, String value) {
        params.put(key, value);
    }
    
    /**
     * 创建副本（用于复制粘贴等操作）
     */
    public EffectInstance copy() {
        EffectInstance copy = new EffectInstance(this.type);
        copy.enabled = this.enabled;
        copy.startOffset = this.startOffset;
        copy.duration = this.duration;
        copy.phase = this.phase;
        copy.triggerMode = this.triggerMode;
        copy.params.putAll(this.params);
        return copy;
    }
}

