package com.director.client.world;

import com.director.timeline.playback.season.SeasonSample;
import com.director.timeline.playback.time.TimeSample;
import com.director.timeline.playback.weather.WeatherSample;

/**
 * 世界渲染覆盖的只读快照（Mixin 层唯一依赖入口）。
 * <p>
 * 由 {@code timeline.playback} 控制器在采样后写入；Mixin / 渲染层只读，不依赖 Controller 单例。
 */
public final class WorldRenderOverride {

    private static final WorldRenderOverride INSTANCE = new WorldRenderOverride();

    private volatile boolean timeOverrideActive;
    private volatile boolean internalTimeUpdate;
    private volatile TimeSample timeSample;

    private volatile boolean weatherTrackManaged;
    private volatile boolean weatherOverrideActive;
    private volatile WeatherSample weatherSample;
    private volatile float weatherFlashBrightness;

    private volatile boolean seasonOverrideActive;
    private volatile SeasonSample seasonSample;

    private WorldRenderOverride() {
    }

    public static WorldRenderOverride get() {
        return INSTANCE;
    }

    // ==================== Read API (Mixin / render) ====================

    public boolean hasTimeOverride() {
        return timeOverrideActive && timeSample != null;
    }

    /** {@code true} 表示当前为控制器内部写时间，应放行 {@code ClientWorld#setTime}。 */
    public boolean isInternalTimeUpdate() {
        return internalTimeUpdate;
    }

    public TimeSample timeSample() {
        return timeOverrideActive ? timeSample : null;
    }

    /**
     * 工程中存在天气轨道时由控制器标记；用于压制原版随机降水（见 {@code WorldMixin}）。
     */
    public boolean hasWeatherTrackManaged() {
        return weatherTrackManaged;
    }

    public boolean hasWeatherOverride() {
        return weatherOverrideActive && weatherSample != null;
    }

    public WeatherSample weatherSample() {
        return weatherOverrideActive ? weatherSample : null;
    }

    public float weatherFlashBrightness() {
        return weatherFlashBrightness;
    }

    public boolean hasSeasonOverride() {
        return seasonOverrideActive && seasonSample != null;
    }

    public SeasonSample seasonSample() {
        return seasonOverrideActive ? seasonSample : null;
    }

    // ==================== Publish API (playback controllers) ====================

    public static void publishTime(boolean overrideActive, boolean internalUpdate, TimeSample sample) {
        WorldRenderOverride state = INSTANCE;
        state.timeOverrideActive = overrideActive && sample != null;
        state.internalTimeUpdate = internalUpdate;
        state.timeSample = sample;
    }

    public static void publishWeather(
            boolean trackManaged,
            boolean overrideActive,
            WeatherSample sample,
            float flashBrightness
    ) {
        WorldRenderOverride state = INSTANCE;
        state.weatherTrackManaged = trackManaged;
        state.weatherOverrideActive = overrideActive && sample != null;
        state.weatherSample = sample;
        state.weatherFlashBrightness = Math.max(0f, flashBrightness);
    }

    public static void publishSeason(boolean overrideActive, SeasonSample sample) {
        WorldRenderOverride state = INSTANCE;
        state.seasonOverrideActive = overrideActive && sample != null;
        state.seasonSample = sample;
    }

    public static void clearAll() {
        WorldRenderOverride state = INSTANCE;
        state.timeOverrideActive = false;
        state.internalTimeUpdate = false;
        state.timeSample = null;
        state.weatherTrackManaged = false;
        state.weatherOverrideActive = false;
        state.weatherSample = null;
        state.weatherFlashBrightness = 0f;
        state.seasonOverrideActive = false;
        state.seasonSample = null;
    }
}
