package com.director.timeline.playback.weather;

import com.director.client.world.WorldRenderOverride;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.weather.WeatherKeyframe;
import com.director.timeline.playback.AbstractTrackTimelinePlaybackController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 天气轨道播放控制器：采样 WeatherTrackSampler 并分发到粒子/天空/事件子系统。
 *
 * <p>线程模型见 {@link com.director.timeline.playback.TimelinePlaybackThreading}；
 * Mixin 通过 {@link WorldRenderOverride} 读取快照。
 */
public final class TimelineWeatherController extends AbstractTrackTimelinePlaybackController<WeatherSample> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineWeatherController.class);
    private static final TimelineWeatherController INSTANCE = new TimelineWeatherController();

    private WeatherTrackSampler sampler;
    private float sampledTime;
    public synchronized float getSampledTime() { return sampledTime; }

    private TimelineWeatherController() {
        super(LOGGER);
    }

    public static TimelineWeatherController getInstance() {
        return INSTANCE;
    }

    @Override
    protected String logTag() {
        return "WeatherController";
    }

    @Override
    protected TrackType expectedTrackType() {
        return TrackType.WEATHER;
    }

    @Deprecated
    public void setActiveWeatherTrack(TimelineTrack track) {
        bindActiveTrack(track);
    }

    public TimelineTrack getActiveWeatherTrack() {
        return getActiveTrack();
    }

    public boolean hasWeatherTrack() {
        return hasTrackInModel();
    }

    @Override
    public synchronized void checkAndAutoActivate() {
        super.checkAndAutoActivate();
        // Model notifications include key deletion, undo and redo, not just track changes.
        rebuildFromActiveTrack();
    }

    @Override
    public synchronized void bindActiveTrack(TimelineTrack track) {
        if (track != null && track.getType() != TrackType.WEATHER) return;
        super.bindActiveTrack(track);
    }

    @Override
    protected boolean computeHasOverride() {
        return super.computeHasOverride() && currentSample.enabled;
    }

    public synchronized float cloudPhase(float time) {
        return sampler == null ? 0 : sampler.effects().cloudPhase(time);
    }

    public synchronized WeatherEffectSampler effects() {
        return sampler == null ? null : sampler.effects();
    }

    @Override
    protected void rebuildSampler(TimelineTrack track) {
        sampler = new WeatherTrackSampler(track);

    }

    @Override
    protected void disposeSampler() {
        sampler = null;
        clearWeatherSubsystems();
    }

    @Override
    protected boolean hasActiveSampler() {
        return sampler != null;
    }

    @Override
    protected int countTrackKeyframes(TimelineTrack track) {
        int count = 0;
        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof WeatherKeyframe) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected WeatherSample sampleAt(float timelineTimeSeconds) {
        sampledTime = Float.isFinite(timelineTimeSeconds) ? Math.max(0,timelineTimeSeconds) : 0;
        return sampler.sample(sampledTime);
    }

    @Override
    protected void onRebuildComplete(int keyframeCount) {
        if (enabled) applySample(sampleAt(resolveCurrentTimelineTime()));
        else clearWeatherSubsystems();
    }

    @Override
    protected void onSingleTrackAutoActivated() {
        if (currentSample == null && sampler != null) {
            float currentTime = resolveCurrentTimelineTime();
            WeatherSample sample = sampler.sample(currentTime);
            applySample(sample);
            logger.debug("[{}] 自动激活后立即采样 @ t={}s, rain={}", logTag(), currentTime, sample.rainIntensity);
        }
    }

    public synchronized void tickEvents(float timelineTimeSeconds) {
        if (!enabled || !hasOverride() || currentSample == null) {
            WeatherEventController.getInstance().clear();
            publishRenderSnapshot();
            return;
        }
        try {
            WeatherEventController.getInstance().tick(currentSample, sampledTime);
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法更新事件子系统", e);
        }
        publishRenderSnapshot();
    }

    @Deprecated
    public void init(com.director.timeline.model.TimelineModel model) {
        logger.debug("[WeatherController] init() 已弃用，请使用 bindActiveTrack()");
    }

    /** Compatibility entry point; render overrides leave server weather untouched. */
    public synchronized void forceDisableVanillaWeather() {}

    @Override
    protected void onDisabled() {
        currentSample = null;
        clearWeatherSubsystems();
        logger.info("[WeatherController] 清除天气覆盖，原版天气继续由服务端状态驱动");
    }

    @Override
    protected void onSampleApplied(WeatherSample sample) {
        applyToWorld(sample);
    }

    @Override
    protected void onOverrideCleared(boolean fullReset) {
        clearWeatherSubsystems();
    }

    @Override
    protected void publishRenderSnapshot() {
        WorldRenderOverride.publishWeather(
                hasOverride(),
                hasOverride(),
                currentSample,
                hasOverride() ? WeatherEventController.getInstance().getCurrentFlashBrightness() : 0
        );
    }

    private void applyToWorld(WeatherSample sample) {
        if (sample == null || !sample.enabled) {
            clearWeatherSubsystems();
            return;
        }

        try {
            WeatherParticleController.getInstance().updateSample(sample);
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法更新天气粒子", e);
        }

        try {
            WeatherSkyController.getInstance().apply(sample);
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法应用天空控制", e);
        }

        try {
            com.director.client.renderer.weather.WeatherRenderer.get().onWeatherSampleUpdated(sample);
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法更新 WeatherRenderer", e);
        }
    }

    private void clearWeatherSubsystems() {
        try {
            WeatherParticleController.getInstance().updateSample(null);
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法清除 WeatherParticleController", e);
        }

        try {
            WeatherSkyController.getInstance().clear();
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法清除 WeatherSkyController", e);
        }

        try {
            WeatherEventController.getInstance().clear();
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法清除 WeatherEventController", e);
        }

        try {
            com.director.client.renderer.weather.WeatherRenderer.get().onWeatherSampleUpdated(null);
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法清除 WeatherRenderer", e);
        }
    }
}
