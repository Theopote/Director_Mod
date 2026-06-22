package com.director.timeline.playback;

import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.playback.entity.TimelineActorController;
import com.director.timeline.playback.season.TimelineSeasonController;
import com.director.timeline.playback.time.TimelineWorldController;
import com.director.timeline.playback.weather.TimelineWeatherController;
import com.director.timeline.playback.weather.WeatherParticleController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 以 {@link TimelineModel} 为唯一时钟的时间线采样入口（导出 / 离线抓帧 / 重同步）。
 * <p>
 * UI 帧循环仍由 {@link com.director.ui.timeline.playback.PlaybackOrchestrator} 协调；
 * 方块片段可见性由 {@code TimelineClipBlockController} 在 UI 层补采样。
 */
public final class TimelinePlaybackSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePlaybackSampler.class);

    private TimelinePlaybackSampler() {
    }

    /**
     * 在指定时间点采样环境/演员等子系统（不含相机与方块片段）。
     */
    public static void sampleAt(float timeSeconds) {
        TimelineModel model = resolveModel();
        if (model == null) {
            return;
        }
        model.setCurrentTime(timeSeconds);
        driveSubsystems(timeSeconds);
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
        run("WeatherParticle", () -> WeatherParticleController.getInstance().tick());
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
