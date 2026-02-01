package com.chronoblocks.ui.timeline.playback.time;

/**
 * 时间采样结果 v3.0 - 专业动画级天空控制
 * 
 * <p>表示在某个时间点采样得到的 Minecraft 世界时间状态。
 * 
 * <p>包含完整的天文参数，用于控制天空渲染、光照、月相等所有时间相关的视觉效果。
 * 
 * <p>关键设计：
 * <ul>
 *   <li>useWorldTime = true：使用自然时间，Minecraft 原版计算所有参数</li>
 *   <li>useWorldTime = false：使用自定义太阳角度，需要 Mixin 覆盖天空计算</li>
 * </ul>
 * 
 * <p>专业动画级功能支持：
 * <ul>
 *   <li>✅ 真实世界太阳运动（高度角、方位角）</li>
 *   <li>✅ 完全反时间（倒放动画）</li>
 *   <li>✅ 日光强度渐变（skyLight）</li>
 *   <li>✅ 手工制作日出夕阳动画</li>
 *   <li>✅ 蓝天、金色黄昏、深夜蓝调（天空颜色覆盖）</li>
 *   <li>✅ 月亮位置和相位控制</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 3.0 - 专业动画级天空控制
 */
public final class TimeSample {

    private static final double DAY_LENGTH_TICKS = 24000.0;
    
    /* =============================
     *    基础世界时间 / 控制参数
     * ============================= */
    
    /** 是否使用自然世界时间（true = 使用 worldTimeTicks，false = 使用自定义太阳角度） */
    private final boolean useWorldTime;
    
    /** Minecraft 世界时间（tick，0-24000），用于月相、部分光照逻辑 */
    private final long worldTimeTicks;
    private final double worldTimeOfDay;
    
    /** 是否冻结世界时间推进（控制器使用，Mixin 可以读取此值） */
    private final boolean freezeWorldTime;
    
    /* =============================
     *         太阳（日照）系统
     * ============================= */
    
    /** 太阳在天空的循环角度（0~1），覆盖 ClientWorld.getSkyAngle() */
    private final float skyAngle;
    
    /** 太阳高度角（仰角，度，0~90，0=地平线，90=正头顶） */
    private final float sunElevation;
    
    /** 太阳方位角（度，0~360，0=正北，90=正东，180=正南，270=正西） */
    private final float sunAzimuth;
    
    /** 天空亮度（0.0-1.0，0=午夜，1=正午），用于光照和颜色计算 */
    private final float skyLight;
    
    /* =============================
     *        月亮（月相）系统
     * ============================= */
    
    /** 月亮相位（0-7） */
    private final int moonPhase;
    
    /** 月亮高度角（度，0~90，负值表示在下方） */
    private final float moonElevation;
    
    /** 月亮方位角（度，0~360） */
    private final float moonAzimuth;
    
    /* =============================
     *        天空颜色系统
     * ============================= */
    
    /** 是否手动设置黎明/黄昏颜色 */
    private final boolean useCustomSkyColor;
    
    /** 日出/日落颜色覆盖（Vanilla 使用 sunriseColor[] 数组） */
    private final float sunriseR;
    private final float sunriseG;
    private final float sunriseB;
    private final float sunriseA;
    
    /** 天空总体颜色覆盖（白天/傍晚，RGBA） */
    private final float skyColorR;
    private final float skyColorG;
    private final float skyColorB;
    private final float skyColorA;
    
    /**
     * 创建时间采样结果（完整构造函数）
     */
    private TimeSample(boolean useWorldTime, long worldTimeTicks, double worldTimeOfDay, boolean freezeWorldTime,
                      float skyAngle, float sunElevation, float sunAzimuth, float skyLight,
                      int moonPhase, float moonElevation, float moonAzimuth,
                      boolean useCustomSkyColor, float sunriseR, float sunriseG, float sunriseB, float sunriseA,
                      float skyColorR, float skyColorG, float skyColorB, float skyColorA) {
        this.useWorldTime = useWorldTime;
        this.worldTimeTicks = worldTimeTicks;
        this.worldTimeOfDay = normalizeTimeOfDay(worldTimeOfDay);
        this.freezeWorldTime = freezeWorldTime;
        this.skyAngle = Math.max(0.0f, Math.min(1.0f, skyAngle));
        this.sunElevation = Math.max(-90.0f, Math.min(90.0f, sunElevation));
        // 规范化方位角
        float normalizedAzimuth = sunAzimuth;
        while (normalizedAzimuth < 0.0f) normalizedAzimuth += 360.0f;
        while (normalizedAzimuth >= 360.0f) normalizedAzimuth -= 360.0f;
        this.sunAzimuth = normalizedAzimuth;
        this.skyLight = Math.max(0.0f, Math.min(1.0f, skyLight));
        this.moonPhase = Math.max(0, Math.min(7, moonPhase));
        this.moonElevation = Math.max(-90.0f, Math.min(90.0f, moonElevation));
        // 规范化月亮方位角
        float normalizedMoonAzimuth = moonAzimuth;
        while (normalizedMoonAzimuth < 0.0f) normalizedMoonAzimuth += 360.0f;
        while (normalizedMoonAzimuth >= 360.0f) normalizedMoonAzimuth -= 360.0f;
        this.moonAzimuth = normalizedMoonAzimuth;
        this.useCustomSkyColor = useCustomSkyColor;
        this.sunriseR = Math.max(0.0f, Math.min(1.0f, sunriseR));
        this.sunriseG = Math.max(0.0f, Math.min(1.0f, sunriseG));
        this.sunriseB = Math.max(0.0f, Math.min(1.0f, sunriseB));
        this.sunriseA = Math.max(0.0f, Math.min(1.0f, sunriseA));
        this.skyColorR = Math.max(0.0f, Math.min(1.0f, skyColorR));
        this.skyColorG = Math.max(0.0f, Math.min(1.0f, skyColorG));
        this.skyColorB = Math.max(0.0f, Math.min(1.0f, skyColorB));
        this.skyColorA = Math.max(0.0f, Math.min(1.0f, skyColorA));
    }

    public static TimeSample fromNaturalTime(double worldTimeOfDay, boolean freezeWorldTime) {
        double normalizedTime = normalizeTimeOfDay(worldTimeOfDay);
        long worldTimeTicks = (long) Math.floor(normalizedTime);

        float skyAngleNorm = calculateVanillaSkyAngle(normalizedTime);
        float sunElevation = calculateSunElevationFromSkyAngle(skyAngleNorm);
        float sunAzimuth = 180.0f;
        float skyLight = calculateSkyLightFromElevation(sunElevation);

        int moonPhase = (int) ((worldTimeTicks / 24000L) % 8L);
        float moonElevation = -sunElevation;
        float moonAzimuth = (sunAzimuth + 180.0f) % 360.0f;

        return new TimeSample(true, worldTimeTicks, normalizedTime, freezeWorldTime,
                             skyAngleNorm, sunElevation, sunAzimuth, skyLight,
                             moonPhase, moonElevation, moonAzimuth,
                             false, 1.0f, 0.6f, 0.2f, 1.0f,
                             0.6f, 0.7f, 1.0f, 1.0f);
    }

    public static TimeSample fromCustomSun(double worldTimeOfDay, float sunElevation, float sunAzimuth, boolean freezeWorldTime) {
        double normalizedTime = normalizeTimeOfDay(worldTimeOfDay);
        long worldTimeTicks = (long) Math.floor(normalizedTime);

        float skyAngleNorm = calculateSkyAngleFromElevation(sunElevation, sunAzimuth);
        float skyLight = calculateSkyLightFromElevation(sunElevation);

        int moonPhase = (int) ((worldTimeTicks / 24000L) % 8L);
        float moonElevation = -sunElevation;
        float moonAzimuth = (sunAzimuth + 180.0f) % 360.0f;

        return new TimeSample(false, worldTimeTicks, normalizedTime, freezeWorldTime,
                             skyAngleNorm, sunElevation, sunAzimuth, skyLight,
                             moonPhase, moonElevation, moonAzimuth,
                             false, 1.0f, 0.6f, 0.2f, 1.0f,
                             0.6f, 0.7f, 1.0f, 1.0f);
    }

    /**
     * 自定义太阳高度角，但以 /time set 的方式驱动世界时间与天空。
     */
    public static TimeSample fromSunElevationAsTime(float sunElevation, float sunAzimuth) {
        float skyAngleNorm = calculateSkyAngleFromElevation(sunElevation, sunAzimuth);
        double timeOfDay = skyAngleToTimeOfDay(skyAngleNorm);
        return fromNaturalTime(timeOfDay, true);
    }
    
    /**
     * 创建一个使用自然世界时间的采样结果
     * 所有参数将基于 worldTimeTicks 自动计算
     */
    public static TimeSample fromNaturalTime(long worldTimeTicks) {
        return fromNaturalTime((double) worldTimeTicks, false);
    }

    /**
     * 创建一个使用自定义太阳角度的采样结果
     */
    public static TimeSample fromCustomSun(long worldTimeTicks, float sunElevation, float sunAzimuth) {
        return fromCustomSun((double) worldTimeTicks, sunElevation, sunAzimuth, true);
    }

    /**
     * 复制当前采样结果（创建新实例，避免共享引用）
     */
    public TimeSample copy() {
        return new TimeSample(
            useWorldTime, worldTimeTicks, worldTimeOfDay, freezeWorldTime,
            skyAngle, sunElevation, sunAzimuth, skyLight,
            moonPhase, moonElevation, moonAzimuth,
            useCustomSkyColor, sunriseR, sunriseG, sunriseB, sunriseA,
            skyColorR, skyColorG, skyColorB, skyColorA
        );
    }
    
    // ======================= 计算辅助方法 =======================
    
    /**
     * 基于天空角度计算太阳高度角（简化模型）
     */
    public static float calculateSunElevationFromSkyAngle(float skyAngleNorm) {
        // Vanilla-like mapping:
        // skyAngleNorm: 0.0 = midnight, 0.25 = sunrise, 0.5 = noon, 0.75 = sunset
        double theta = (skyAngleNorm - 0.5d) * 2.0d * Math.PI;
        double cos = Math.cos(theta);
        double maxElevation = 60.0d;
        double elev = cos * maxElevation;
        if (elev > 90.0d) elev = 90.0d;
        if (elev < -90.0d) elev = -90.0d;
        return (float) elev;
    }
    
    /**
     * 基于太阳高度角计算天空角度（反向映射）
     * 
     * <p>注意：Minecraft 1.21 中太阳位置由 skyAngle 单独控制，不支持独立的方位角。
     * 这个方法根据高度角生成完整昼夜循环的 skyAngle。
     */
    private static float calculateSkyAngleFromElevation(float elevation, float azimuth) {
        double maxElevation = 60.0d;
        double clamped = Math.max(-maxElevation, Math.min(maxElevation, elevation));
        double ratio = clamped / maxElevation;
        if (ratio > 1.0d) ratio = 1.0d;
        if (ratio < -1.0d) ratio = -1.0d;
        double t = Math.acos(ratio); // 0..pi
        double skyAngle = azimuth < 180.0f
                ? 0.5d - t / (2.0d * Math.PI)
                : 0.5d + t / (2.0d * Math.PI);
        while (skyAngle < 0.0d) skyAngle += 1.0d;
        while (skyAngle >= 1.0d) skyAngle -= 1.0d;
        return (float) skyAngle;
    }

    /**
     * 将天空角度映射回时间（tick），用于等价于 /time set 的效果。
     */
    private static double skyAngleToTimeOfDay(float skyAngle) {
        float target = skyAngle;
        while (target < 0.0f) target += 1.0f;
        while (target >= 1.0f) target -= 1.0f;
        double low = 0.0d;
        double high = DAY_LENGTH_TICKS;
        for (int i = 0; i < 32; i++) {
            double mid = (low + high) * 0.5d;
            float angle = calculateVanillaSkyAngle(mid);
            if (angle < target) {
                low = mid;
            } else {
                high = mid;
            }
        }
        double time = (low + high) * 0.5d;
        time = time % DAY_LENGTH_TICKS;
        if (time < 0.0d) time += DAY_LENGTH_TICKS;
        return time;
    }
    
    /**
     * 基于太阳高度角计算天空亮度
     * 
     * <p>Minecraft 公式：brightness = 1.0F - cos(skyAngle * 2π) * 2F + 0.2F
     * 这里简化：基于太阳高度直接计算
     */
    public static float calculateSkyLightFromElevation(float elevation) {
        // Improved brightness mapping:
        // - When the sun is well below horizon (e.g. <= -5°) treat as night (0.0)
        // - From -5° to 0°: transition from 0.0 to 0.2 (dawn/dusk)
        // - From 0° to 60°: transition from 0.2 to 1.0 (daylight)
        if (elevation <= -5.0f) {
            return 0.0f;
        } else if (elevation < 0.0f) {
            float p = (elevation + 5.0f) / 5.0f; // 0..1
            return p * 0.2f;
        } else {
            float p = Math.min(1.0f, elevation / 60.0f);
            return 0.2f + p * 0.8f;
        }
    }

    /**
     * 创建一个完整的 TimeSample（显式参数），用于在采样器中直接构造插值结果
     */
    public static TimeSample createFull(boolean useWorldTime, long worldTimeTicks, double worldTimeOfDay, boolean freezeWorldTime,
                                        float skyAngle, float sunElevation, float sunAzimuth, float skyLight,
                                        int moonPhase, float moonElevation, float moonAzimuth,
                                        boolean useCustomSkyColor, float sunriseR, float sunriseG, float sunriseB, float sunriseA,
                                        float skyColorR, float skyColorG, float skyColorB, float skyColorA) {
        return new TimeSample(useWorldTime, worldTimeTicks, worldTimeOfDay, freezeWorldTime,
                skyAngle, sunElevation, sunAzimuth, skyLight,
                moonPhase, moonElevation, moonAzimuth,
                useCustomSkyColor, sunriseR, sunriseG, sunriseB, sunriseA,
                skyColorR, skyColorG, skyColorB, skyColorA);
    }

    private static float calculateVanillaSkyAngle(double timeOfDay) {
        double f = (timeOfDay / DAY_LENGTH_TICKS) - 0.25;
        if (f < 0.0) {
            f += 1.0;
        }
        if (f > 1.0) {
            f -= 1.0;
        }
        double g = f;
        f = 1.0 - (Math.cos(f * Math.PI) + 1.0) / 2.0;
        f = g + (f - g) / 3.0;
        return (float) f;
    }

    private static double normalizeTimeOfDay(double timeOfDay) {
        double result = timeOfDay % DAY_LENGTH_TICKS;
        if (result < 0.0) {
            result += DAY_LENGTH_TICKS;
        }
        return result;
    }
    
    // ================== Getters（基础时间参数）==================
    
    public boolean isUseWorldTime() {
        return useWorldTime;
    }
    
    /** 兼容旧代码 */

    public long getWorldTimeTicks() {
        return worldTimeTicks;
    }

    public double getWorldTimeOfDay() {
        return worldTimeOfDay;
    }
    
    public boolean isFreezeWorldTime() {
        return freezeWorldTime;
    }
    
    // ================== Getters（太阳系统）==================
    
    public float getSkyAngle() {
        return skyAngle;
    }

    public float getSkyLight() {
        return skyLight;
    }

    
    public float getSunElevation() {
        return sunElevation;
    }
    
    /** 兼容旧代码：使用 sunAltitude 作为别名 */
    
    public float getSunAzimuth() {
        return sunAzimuth;
    }

    
    // ================== Getters（月亮系统）==================
    
    public int getMoonPhase() {
        return moonPhase;
    }

    
    // ================== Getters（天空颜色系统）==================
    
    public boolean isUseCustomSkyColor() {
        return useCustomSkyColor;
    }
    
    public float getSunriseR() {
        return sunriseR;
    }
    
    public float getSunriseG() {
        return sunriseG;
    }
    
    public float getSunriseB() {
        return sunriseB;
    }
    
    public float getSunriseA() {
        return sunriseA;
    }
    
    public float getSkyColorR() {
        return skyColorR;
    }
    
    public float getSkyColorG() {
        return skyColorG;
    }
    
    public float getSkyColorB() {
        return skyColorB;
    }
    
    public float getSkyColorA() {
        return skyColorA;
    }
    
    @Override
    public String toString() {
        if (useWorldTime) {
            return String.format("TimeSample{worldTime=%d, natural, freeze=%b}", 
                    worldTimeTicks, freezeWorldTime);
        } else {
            return String.format("TimeSample{worldTime=%d, sunElevation=%.1f°, sunAzimuth=%.1f°, skyAngle=%.3f, freeze=%b}", 
                    worldTimeTicks, sunElevation, sunAzimuth, skyAngle, freezeWorldTime);
        }
    }
}
