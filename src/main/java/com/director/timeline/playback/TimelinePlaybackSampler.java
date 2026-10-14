package com.director.timeline.playback;

import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.playback.entity.TimelineActorController;
import com.director.timeline.playback.season.TimelineSeasonController;
import com.director.timeline.playback.time.TimelineWorldController;
import com.director.timeline.playback.weather.TimelineWeatherController;
import com.director.timeline.playback.weather.WeatherParticleController;
import com.director.timeline.playback.block.TimelineClipBlockController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 以 {@link TimelineModel} 为唯一时钟的时间线采样入口（导出 / 离线抓帧 / 重同步）。
 * <p>
 * UI 帧循环仍由 {@link com.director.ui.timeline.playback.PlaybackOrchestrator} 协调。
 */
public final class TimelinePlaybackSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePlaybackSampler.class);

    private TimelinePlaybackSampler() {
    }

    /**
     * 在指定时间点采样完整世界状态（环境/演员/方块片段，不含相机）。
     */
    public static void sampleWorldAt(float timeSeconds) {
        TimelineModel model = resolveModel();
        if (model == null) {
            return;
        }
        model.setCurrentTime(timeSeconds);
        com.director.lighting.PointLightRuntime.update(model);
        driveSubsystems(timeSeconds);
        run("方块片段", () -> TimelineClipBlockController.getInstance().update());
    }

    public static float resolveDuration(float fallbackSeconds) {
        TimelineModel model = resolveModel();
        if (model == null) {
            return fallbackSeconds;
        }
        float duration = model.getDuration();
        return duration > 0f ? duration : fallbackSeconds;
    }

    public static TimelineModel resolveModel() {
        var service = DirectorServices.get().containerTimeline();
        if (service == null) {
            return null;
        }
        return service.getTimelineModel();
    }

    private static void driveSubsystems(float timeSeconds) {
        run("季节采样", () -> TimelineSeasonController.getInstance().onTimelineTick(timeSeconds));
        run("天气采样", () -> TimelineWeatherController.getInstance().updateForPlayback(timeSeconds));
        run("世界时间", () -> TimelineWorldController.getInstance().update());
        run("演员运行时", () -> com.director.actor.runtime.ActorRuntimeController.getInstance().update());
        run("演员轨道", () -> TimelineActorController.getInstance().updateForPlayback(timeSeconds));
        run("WeatherRenderer", () -> com.director.client.renderer.weather.WeatherRenderer.get().tick());
        run("天气事件", () -> TimelineWeatherController.getInstance().tickEvents(timeSeconds));
    }

    private static void run(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.warn("[TimelinePlaybackSampler] {} 失败", name, e);
        }
    }
}
