package com.chronoblocks.ui.timeline.playback.time;

/**
 * 时间采样结果
 * 
 * <p>表示在某个时间点采样得到的 Minecraft 世界时间状态。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class TimeSample {
    /** Minecraft 世界时间（tick） */
    private final long worldTimeTicks;
    
    /** 是否使用自定义太阳角度 */
    private final boolean useCustomSun;
    
    /** 太阳高度角（度，0~90） */
    private final float sunElevation;
    
    /** 太阳方位角（度，0~360） */
    private final float sunAzimuth;
    
    public TimeSample(long worldTimeTicks, boolean useCustomSun, float sunElevation, float sunAzimuth) {
        this.worldTimeTicks = worldTimeTicks;
        this.useCustomSun = useCustomSun;
        this.sunElevation = Math.max(0.0f, Math.min(90.0f, sunElevation));
        // 规范化方位角
        float normalizedAzimuth = sunAzimuth;
        while (normalizedAzimuth < 0.0f) normalizedAzimuth += 360.0f;
        while (normalizedAzimuth >= 360.0f) normalizedAzimuth -= 360.0f;
        this.sunAzimuth = normalizedAzimuth;
    }
    
    /**
     * 创建一个使用自然世界时间的采样结果
     */
    public static TimeSample fromNaturalTime(long worldTimeTicks) {
        return new TimeSample(worldTimeTicks, false, 0.0f, 0.0f);
    }
    
    /**
     * 创建一个使用自定义太阳角度的采样结果
     */
    public static TimeSample fromCustomSun(long worldTimeTicks, float sunElevation, float sunAzimuth) {
        return new TimeSample(worldTimeTicks, true, sunElevation, sunAzimuth);
    }
    
    public long getWorldTimeTicks() {
        return worldTimeTicks;
    }
    
    public boolean isUseCustomSun() {
        return useCustomSun;
    }
    
    public float getSunElevation() {
        return sunElevation;
    }
    
    public float getSunAzimuth() {
        return sunAzimuth;
    }
    
    @Override
    public String toString() {
        if (useCustomSun) {
            return String.format("TimeSample{worldTime=%d, sunElevation=%.1f°, sunAzimuth=%.1f°}", 
                    worldTimeTicks, sunElevation, sunAzimuth);
        } else {
            return String.format("TimeSample{worldTime=%d, natural}", worldTimeTicks);
        }
    }
}
