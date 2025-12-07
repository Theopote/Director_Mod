package com.chronoblocks.ui.timeline.playback.season;

import org.joml.Vector3f;

/**
 * SeasonSample v1.1
 * 
 * <p>这是 SeasonTrackSampler 在时间 t 采样后得到的结果数据结构。
 * 
 * <p>它描述了当前世界应该呈现的"气候趋势"：
 * <ul>
 *   <li>温度趋势（temperature）</li>
 *   <li>湿度趋势（humidity）</li>
 *   <li>雪线高度（snowlineHeight）</li>
 *   <li>草、树叶、天空、雾、光照的颜色偏移</li>
 * </ul>
 * 
 * <p>注意：此处的颜色 tint 是"乘数值"，而不是绝对颜色。渲染时应该用：</p>
 * 
 * <pre>
 * finalColor = originalBiomeColor * tint;
 * </pre>
 * 
 * <p>其中：
 * <ul>
 *   <li>tint = 1.0 表示保持原色</li>
 *   <li>tint < 1.0 表示变暗（例如 0.8 = 80% 亮度）</li>
 *   <li>tint > 1.0 表示增强（例如 1.2 = 120% 亮度，会被限制到 255）</li>
 * </ul>
 * 
 * <p>该对象是只读结构体，没有引用共享问题（内部自动复制 Vector3f）。</p>
 * 
 * @author ChronoBlocks Team
 * @version 1.1 - 改进文档、添加 toString、改进方法命名
 */
public final class SeasonSample {
    
    // ========== 气候趋势基础参数 ==========
    
    /** 温度趋势 [-1, +1] 建议范围 */
    public final float temperature;
    
    /** 湿度趋势 [0, 1] */
    public final float humidity;
    
    /** 雪线 Y 坐标（高于该值可用于渲染冰冷效果） */
    public final float snowlineHeight;
    
    /** 雪量 [0.0, 1.0]，用于控制地表的积雪层数（0.0 = 无雪，1.0 = 最大积雪） */
    public final float snowAmount;
    
    // ========== 颜色偏移（乘数值） ==========
    
    /** 草地颜色偏移（与生物群系颜色相乘） */
    public final Vector3f grassTint;
    
    /** 树叶颜色偏移（与生物群系颜色相乘） */
    public final Vector3f foliageTint;
    
    /** 天空颜色偏移（与原始天空颜色相乘） */
    public final Vector3f skyTint;
    
    /** 雾颜色偏移（与原始雾颜色相乘） */
    public final Vector3f fogTint;
    
    /** 光照颜色偏移（冷暖色调，与原始光照颜色相乘） */
    public final Vector3f lightTint;
    
    // ========== 构造函数（复制所有参数） ==========
    
    /**
     * 构造函数，深复制所有 Vector3f 参数以避免外部修改。
     */
    public SeasonSample(
            float temperature,
            float humidity,
            float snowlineHeight,
            float snowAmount,
            Vector3f grassTint,
            Vector3f foliageTint,
            Vector3f skyTint,
            Vector3f fogTint,
            Vector3f lightTint) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.snowlineHeight = snowlineHeight;
        this.snowAmount = snowAmount;
        // 深复制，避免外部修改
        this.grassTint = new Vector3f(grassTint);
        this.foliageTint = new Vector3f(foliageTint);
        this.skyTint = new Vector3f(skyTint);
        this.fogTint = new Vector3f(fogTint);
        this.lightTint = new Vector3f(lightTint);
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 返回一个默认季节采样（无偏移，保持原色）。
     * 
     * <p>所有颜色 tint 为 (0.0, 0.0, 0.0)，表示相对偏移为 0，不改变原始颜色。
     * <p>公式：finalColor = originalColor * (1.0 + 0.0) = originalColor
     */
    public static SeasonSample defaultSample() {
        Vector3f neutral = new Vector3f(0.0f, 0.0f, 0.0f); // 偏移值为 0
        return new SeasonSample(
            0.0f,   // temperature
            0.5f,   // humidity
            90.0f,  // snowlineHeight
            0.0f,   // snowAmount
            neutral, neutral, neutral, neutral, neutral
        );
    }
    
    /**
     * 返回一个默认季节采样（无偏移，保持原色）。
     * 
     * @deprecated 使用 {@link #defaultSample()} 代替
     */
    @Deprecated
    public static SeasonSample neutral() {
        return defaultSample();
    }
    
    /**
     * 线性插值两个采样值
     */
    public static SeasonSample lerp(SeasonSample a, SeasonSample b, float t) {
        float temperature = a.temperature + (b.temperature - a.temperature) * t;
        float humidity = a.humidity + (b.humidity - a.humidity) * t;
        float snowlineHeight = a.snowlineHeight + (b.snowlineHeight - a.snowlineHeight) * t;
        float snowAmount = a.snowAmount + (b.snowAmount - a.snowAmount) * t;
        
        Vector3f grassTint = new Vector3f(a.grassTint).lerp(b.grassTint, t);
        Vector3f foliageTint = new Vector3f(a.foliageTint).lerp(b.foliageTint, t);
        Vector3f skyTint = new Vector3f(a.skyTint).lerp(b.skyTint, t);
        Vector3f fogTint = new Vector3f(a.fogTint).lerp(b.fogTint, t);
        Vector3f lightTint = new Vector3f(a.lightTint).lerp(b.lightTint, t);
        
        return new SeasonSample(
            temperature, humidity, snowlineHeight, snowAmount,
            grassTint, foliageTint, skyTint, fogTint, lightTint
        );
    }
    
    /**
     * 平滑插值（smoothstep）
     */
    public static SeasonSample smoothLerp(SeasonSample a, SeasonSample b, float t) {
        float smoothT = t * t * (3.0f - 2.0f * t); // smoothstep
        return lerp(a, b, smoothT);
    }
    
    // ========== 调试输出 ==========
    
    @Override
    public String toString() {
        return "SeasonSample{" +
                "temperature=" + temperature +
                ", humidity=" + humidity +
                ", snowlineHeight=" + snowlineHeight +
                ", snowAmount=" + snowAmount +
                ", grassTint=" + grassTint +
                ", foliageTint=" + foliageTint +
                ", skyTint=" + skyTint +
                ", fogTint=" + fogTint +
                ", lightTint=" + lightTint +
                '}';
    }
}

