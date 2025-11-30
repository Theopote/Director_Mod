package com.chronoblocks.ui.timeline.model;

import java.util.Objects;

/**
 * 可见性关键帧
 * 
 * <p>用于在时间线上标记片段的可见性状态变化（透明度、隐藏/显示等）
 * 
 * <p>关键帧属性：
 * <ul>
 *   <li>time: 关键帧时间（相对于片段开始时间，秒）</li>
 *   <li>alpha: 透明度 [0..1]</li>
 *   <li>mode: 可见性模式（NORMAL / HIDE / SHOW / GHOST）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class VisibilityKeyframe implements Comparable<VisibilityKeyframe> {
    
    /**
     * 可见性模式
     */
    public enum VisibilityMode {
        /** 正常状态（使用 alpha 值） */
        NORMAL,
        
        /** 完全隐藏 */
        HIDE,
        
        /** 完全显示（alpha = 1.0） */
        SHOW,
        
        /** 半透明/幽灵模式（使用 alpha 值，通常用于淡化效果） */
        GHOST
    }
    
    /** 关键帧时间（相对于片段开始时间，秒） */
    private final float time;
    
    /** 透明度 [0..1]，1.0 = 完全不透明 */
    private float alpha;
    
    /** 可见性模式 */
    private VisibilityMode mode;
    
    /**
     * 创建可见性关键帧
     * 
     * @param time 关键帧时间（相对于片段开始时间，秒）
     * @param alpha 透明度 [0..1]
     * @param mode 可见性模式
     */
    public VisibilityKeyframe(float time, float alpha, VisibilityMode mode) {
        if (time < 0) {
            throw new IllegalArgumentException("Time must be >= 0");
        }
        this.time = time;
        this.alpha = clamp01(alpha);
        this.mode = Objects.requireNonNullElse(mode, VisibilityMode.NORMAL);
        
        // 根据模式调整 alpha
        if (this.mode == VisibilityMode.HIDE) {
            this.alpha = 0.0f;
        } else if (this.mode == VisibilityMode.SHOW) {
            this.alpha = 1.0f;
        }
    }
    
    /**
     * 创建默认关键帧（正常状态，完全不透明）
     */
    public static VisibilityKeyframe normal(float time) {
        return new VisibilityKeyframe(time, 1.0f, VisibilityMode.NORMAL);
    }
    
    /**
     * 创建隐藏关键帧
     */
    public static VisibilityKeyframe hide(float time) {
        return new VisibilityKeyframe(time, 0.0f, VisibilityMode.HIDE);
    }
    
    /**
     * 创建显示关键帧
     */
    public static VisibilityKeyframe show(float time) {
        return new VisibilityKeyframe(time, 1.0f, VisibilityMode.SHOW);
    }
    
    /**
     * 创建半透明关键帧
     */
    public static VisibilityKeyframe ghost(float time, float alpha) {
        return new VisibilityKeyframe(time, alpha, VisibilityMode.GHOST);
    }
    
    // ========== Getters and Setters ==========
    
    public float getTime() {
        return time;
    }
    
    public float getAlpha() {
        return alpha;
    }
    
    public void setAlpha(float alpha) {
        if (mode == VisibilityMode.HIDE) {
            this.alpha = 0.0f;
        } else if (mode == VisibilityMode.SHOW) {
            this.alpha = 1.0f;
        } else {
            this.alpha = clamp01(alpha);
        }
    }
    
    public VisibilityMode getMode() {
        return mode;
    }
    
    public void setMode(VisibilityMode mode) {
        this.mode = Objects.requireNonNullElse(mode, VisibilityMode.NORMAL);
        
        // 根据新模式调整 alpha
        if (this.mode == VisibilityMode.HIDE) {
            this.alpha = 0.0f;
        } else if (this.mode == VisibilityMode.SHOW) {
            this.alpha = 1.0f;
        }
    }
    
    /**
     * 获取该关键帧的实际透明度（考虑模式）
     * 
     * @return 实际透明度 [0..1]
     */
    public float getEffectiveAlpha() {
        return switch (mode) {
            case HIDE -> 0.0f;
            case SHOW -> 1.0f;
            default -> alpha;
        };
    }
    
    /**
     * 复制关键帧
     */
    public VisibilityKeyframe copy() {
        return new VisibilityKeyframe(time, alpha, mode);
    }
    
    // ========== Comparable ==========
    
    @Override
    public int compareTo(VisibilityKeyframe other) {
        return Float.compare(this.time, other.time);
    }
    
    // ========== equals & hashCode ==========
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VisibilityKeyframe that = (VisibilityKeyframe) o;
        return Float.compare(that.time, time) == 0 &&
               Float.compare(that.alpha, alpha) == 0 &&
               mode == that.mode;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(time, alpha, mode);
    }
    
    @Override
    public String toString() {
        return String.format("VisibilityKeyframe{time=%.2f, alpha=%.2f, mode=%s}", 
                time, alpha, mode);
    }
    
    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}

