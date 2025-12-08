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
    public float temperature;        // [-1, 1]
    public float humidity;           // [0, 1]

    // 积雪控制
    public float snowAmount;         // [0, 1]
    public float snowSlopeFactor;    // [0, 1]
    public float snowlineHeight;     // 世界高度
    public float snowMeltSpeed;      // ≥ 0

    // 冰冻控制
    public float freezeStrength;     // [0, 1]
    public float freezeThreshold;    // [-1, 1]
    public float freezeMeltSpeed;    // ≥ 0

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

        s.temperature = this.temperature;
        s.humidity = this.humidity;

        s.snowAmount = this.snowAmount;
        s.snowSlopeFactor = this.snowSlopeFactor;
        s.snowlineHeight = this.snowlineHeight;
        s.snowMeltSpeed = this.snowMeltSpeed;

        s.freezeStrength = this.freezeStrength;
        s.freezeThreshold = this.freezeThreshold;
        s.freezeMeltSpeed = this.freezeMeltSpeed;

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
        s.temperature = a.temperature * iu + b.temperature * t;
        s.humidity = a.humidity * iu + b.humidity * t;

        s.snowAmount = a.snowAmount * iu + b.snowAmount * t;
        s.snowSlopeFactor = a.snowSlopeFactor * iu + b.snowSlopeFactor * t;
        s.snowlineHeight = a.snowlineHeight * iu + b.snowlineHeight * t;
        s.snowMeltSpeed = a.snowMeltSpeed * iu + b.snowMeltSpeed * t;

        s.freezeStrength = a.freezeStrength * iu + b.freezeStrength * t;
        s.freezeThreshold = a.freezeThreshold * iu + b.freezeThreshold * t;
        s.freezeMeltSpeed = a.freezeMeltSpeed * iu + b.freezeMeltSpeed * t;

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
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", snowAmount=" + snowAmount +
                ", freezeStrength=" + freezeStrength +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SeasonSample that = (SeasonSample) obj;
        
        if (enabled != that.enabled) return false;
        if (Float.compare(that.temperature, temperature) != 0) return false;
        if (Float.compare(that.humidity, humidity) != 0) return false;
        if (Float.compare(that.snowAmount, snowAmount) != 0) return false;
        if (Float.compare(that.snowSlopeFactor, snowSlopeFactor) != 0) return false;
        if (Float.compare(that.snowlineHeight, snowlineHeight) != 0) return false;
        if (Float.compare(that.snowMeltSpeed, snowMeltSpeed) != 0) return false;
        if (Float.compare(that.freezeStrength, freezeStrength) != 0) return false;
        if (Float.compare(that.freezeThreshold, freezeThreshold) != 0) return false;
        if (Float.compare(that.freezeMeltSpeed, freezeMeltSpeed) != 0) return false;
        
        return grassTint.equals(that.grassTint) &&
               foliageTint.equals(that.foliageTint) &&
               skyTint.equals(that.skyTint) &&
               fogTint.equals(that.fogTint) &&
               lightTint.equals(that.lightTint);
    }

    @Override
    public int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 31 * result + Float.hashCode(temperature);
        result = 31 * result + Float.hashCode(humidity);
        result = 31 * result + Float.hashCode(snowAmount);
        result = 31 * result + Float.hashCode(snowSlopeFactor);
        result = 31 * result + Float.hashCode(snowlineHeight);
        result = 31 * result + Float.hashCode(snowMeltSpeed);
        result = 31 * result + Float.hashCode(freezeStrength);
        result = 31 * result + Float.hashCode(freezeThreshold);
        result = 31 * result + Float.hashCode(freezeMeltSpeed);
        result = 31 * result + grassTint.hashCode();
        result = 31 * result + foliageTint.hashCode();
        result = 31 * result + skyTint.hashCode();
        result = 31 * result + fogTint.hashCode();
        result = 31 * result + lightTint.hashCode();
        return result;
    }
}
