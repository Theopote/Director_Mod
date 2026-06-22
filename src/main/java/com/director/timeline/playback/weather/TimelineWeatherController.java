package com.director.timeline.playback.weather;

import com.director.client.world.WorldRenderOverride;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.weather.WeatherKeyframe;
import com.director.timeline.playback.AbstractTrackTimelinePlaybackController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
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
    protected void rebuildSampler(TimelineTrack track) {
        sampler = new WeatherTrackSampler(track);
        sampler.rebuildKeyframeList();
    }

    @Override
    protected void disposeSampler() {
        sampler = null;
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
        return sampler.sample(timelineTimeSeconds);
    }

    @Override
    protected void onRebuildComplete(int keyframeCount) {
        if (enabled && keyframeCount > 0) {
            applySample(sampleAt(resolveCurrentTimelineTime()));
        }
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
            return;
        }
        try {
            WeatherEventController.getInstance().tick(currentSample, timelineTimeSeconds);
        } catch (Exception e) {
            logger.debug("[WeatherController] 无法更新事件子系统", e);
        }
        publishRenderSnapshot();
    }

    @Deprecated
    public void init(com.director.timeline.model.TimelineModel model) {
        logger.debug("[WeatherController] init() 已弃用，请使用 bindActiveTrack()");
    }

    public synchronized void forceDisableVanillaWeather() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        ClientWorld world = client.world;

        if (hasOverride() && currentSample != null && currentSample.enabled) {
            float rain = currentSample.rainIntensity;
            float thunder = currentSample.thunderIntensity;
            world.setRainGradient(rain);
            world.setThunderGradient(thunder);
            world.getLevelProperties().setRaining(rain > 0.01f);
        } else {
            world.setRainGradient(0.0f);
            world.setThunderGradient(0.0f);
            world.getLevelProperties().setRaining(false);
        }
    }

    @Override
    protected void onDisabled() {
        currentSample = null;
        clearWeatherSubsystems();
        logger.info("[WeatherController] 清除天气 override，并尝试恢复 Vanilla 晴天状态");
    }

    @Override
    protected void onSampleApplied(WeatherSample sample) {
        applyToWorld(sample);
    }

    @Override
    protected void onOverrideCleared(boolean fullReset) {
        if (!fullReset) {
            try {
                com.director.client.renderer.weather.WeatherRenderer.get().onWeatherSampleUpdated(null);
            } catch (Exception e) {
                logger.debug("[WeatherController] 无法清除 WeatherRenderer", e);
            }
            return;
        }
        clearWeatherSubsystems();
    }

    @Override
    protected void publishRenderSnapshot() {
        WorldRenderOverride.publishWeather(
                hasWeatherTrack(),
                hasOverride(),
                currentSample,
                WeatherEventController.getInstance().getCurrentFlashBrightness()
        );
    }

    private void applyToWorld(WeatherSample sample) {
        if (sample == null || !sample.enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
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
