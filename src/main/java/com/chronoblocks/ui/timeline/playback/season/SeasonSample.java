package com.chronoblocks.ui.timeline.playback.season;

import org.joml.Vector3f;

/**
 * SeasonSample v4.0
 *
 * <p>采样结果（已经插值好的"当前季节状态"），供：
 * <ul>
 *   <li>BiomeColorsMixin / ClientWorldMixin / BackgroundRendererMixin</li>
 *   <li>SeasonSnowManager / WaterFreezeManager</li>
 * </ul>
 * 使用。
 *
 * <p>注意：这里尽量做成"傻瓜结构体"，字段公开，方便 Mixin 直接访问。
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public class SeasonSample {

    // 是否启用季节覆盖（false 表示完全使用原版行为）
    public boolean enabled = false;

    // 气候参数
    public float humidity;           // [0, 1]

    // 积雪控制
    public float snowAmount;         // [0, 1] 覆盖概率
    public float snowlineHeight;     // 世界高度
    public int snowThickness;        // [0, 10] 积雪厚度（层数）

    // 冰冻控制
    public float freezeStrength;     // [0, 1] 覆盖概率

    // 颜色 Tint（相对偏移，0=无，0.1=+10%）
    public final Vector3f grassTint   = new Vector3f(0f, 0f, 0f);
    public final Vector3f foliageTint = new Vector3f(0f, 0f, 0f);
    public final Vector3f skyTint     = new Vector3f(0f, 0f, 0f);
    public final Vector3f fogTint     = new Vector3f(0f, 0f, 0f);
    public final Vector3f lightTint   = new Vector3f(0f, 0f, 0f);

    public SeasonSample() {
    }

    /**
     * 创建一个禁用状态的采样（不应用任何季节效果）。
     */
    public static SeasonSample disabled() {
        SeasonSample s = new SeasonSample();
        s.enabled = false;
        return s;
    }

    /**
     * 返回默认采样（无季节偏移）。
     * 
     * @deprecated 使用 {@link #disabled()} 代替
     */
    @Deprecated
    public static SeasonSample defaultSample() {
        SeasonSample s = new SeasonSample();
        s.enabled = false;
        return s;
    }

    /**
     * 返回默认采样（无季节偏移）。
     * 
     * @deprecated 使用 {@link #disabled()} 代替
     */
    @Deprecated
    public static SeasonSample neutral() {
        return disabled();
    }

    /**
     * 深拷贝当前采样。
     */
    public SeasonSample copy() {
        SeasonSample s = new SeasonSample();
        s.enabled = this.enabled;

        s.humidity = this.humidity;

        s.snowAmount = this.snowAmount;
        s.snowlineHeight = this.snowlineHeight;
        s.snowThickness = this.snowThickness;

        s.freezeStrength = this.freezeStrength;

        s.grassTint.set(this.grassTint);
        s.foliageTint.set(this.foliageTint);
        s.skyTint.set(this.skyTint);
        s.fogTint.set(this.fogTint);
        s.lightTint.set(this.lightTint);

        return s;
    }

    /**
     * 线性插值两个采样值（v4.0 保留向后兼容）。
     */
    public static SeasonSample lerp(SeasonSample a, SeasonSample b, float t) {
        if (a == null || !a.enabled) return b != null ? b.copy() : disabled();
        if (b == null || !b.enabled) return a.copy();

        SeasonSample s = new SeasonSample();
        s.enabled = true;

        float iu = 1f - t;
        s.humidity = a.humidity * iu + b.humidity * t;

        s.snowAmount = a.snowAmount * iu + b.snowAmount * t;
        s.snowlineHeight = a.snowlineHeight * iu + b.snowlineHeight * t;
        s.snowThickness = Math.round(a.snowThickness * iu + b.snowThickness * t);

        s.freezeStrength = a.freezeStrength * iu + b.freezeStrength * t;

        s.grassTint.set(
                a.grassTint.x * iu + b.grassTint.x * t,
                a.grassTint.y * iu + b.grassTint.y * t,
                a.grassTint.z * iu + b.grassTint.z * t
        );
        s.foliageTint.set(
                a.foliageTint.x * iu + b.foliageTint.x * t,
                a.foliageTint.y * iu + b.foliageTint.y * t,
                a.foliageTint.z * iu + b.foliageTint.z * t
        );
        s.skyTint.set(
                a.skyTint.x * iu + b.skyTint.x * t,
                a.skyTint.y * iu + b.skyTint.y * t,
                a.skyTint.z * iu + b.skyTint.z * t
        );
        s.fogTint.set(
                a.fogTint.x * iu + b.fogTint.x * t,
                a.fogTint.y * iu + b.fogTint.y * t,
                a.fogTint.z * iu + b.fogTint.z * t
        );
        s.lightTint.set(
                a.lightTint.x * iu + b.lightTint.x * t,
                a.lightTint.y * iu + b.lightTint.y * t,
                a.lightTint.z * iu + b.lightTint.z * t
        );

        return s;
    }

    /**
     * 平滑插值（smoothstep）。
     */
    public static SeasonSample smoothLerp(SeasonSample a, SeasonSample b, float t) {
        float smoothT = t * t * (3.0f - 2.0f * t); // smoothstep
        return lerp(a, b, smoothT);
    }

    // ========== 调试输出 ==========

    @Override
    public String toString() {
        return "SeasonSample{" +
                "enabled=" + enabled +
                ", humidity=" + humidity +
                ", snowAmount=" + snowAmount +
                ", snowThickness=" + snowThickness +
                ", freezeStrength=" + freezeStrength +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SeasonSample that = (SeasonSample) obj;
        
        if (enabled != that.enabled) return false;
        if (Float.compare(that.humidity, humidity) != 0) return false;
        if (Float.compare(that.snowAmount, snowAmount) != 0) return false;
        if (Float.compare(that.snowlineHeight, snowlineHeight) != 0) return false;
        if (that.snowThickness != snowThickness) return false;
        if (Float.compare(that.freezeStrength, freezeStrength) != 0) return false;
        
        return grassTint.equals(that.grassTint) &&
               foliageTint.equals(that.foliageTint) &&
               skyTint.equals(that.skyTint) &&
               fogTint.equals(that.fogTint) &&
               lightTint.equals(that.lightTint);
    }

    @Override
    public int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 31 * result + Float.hashCode(humidity);
        result = 31 * result + Float.hashCode(snowAmount);
        result = 31 * result + Float.hashCode(snowlineHeight);
        result = 31 * result + Integer.hashCode(snowThickness);
        result = 31 * result + Float.hashCode(freezeStrength);
        result = 31 * result + grassTint.hashCode();
        result = 31 * result + foliageTint.hashCode();
        result = 31 * result + skyTint.hashCode();
        result = 31 * result + fogTint.hashCode();
        result = 31 * result + lightTint.hashCode();
        return result;
    }
}
