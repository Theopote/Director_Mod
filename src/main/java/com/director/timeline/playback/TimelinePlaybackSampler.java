package com.director.timeline.playback;

import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.playback.season.TimelineSeasonController;
import com.director.timeline.playback.time.TimelineWorldController;
import com.director.timeline.playback.weather.TimelineWeatherController;
import com.director.timeline.playback.block.TimelineClipBlockController;

/**
 * 以 {@link TimelineModel} 为唯一时钟的时间线采样入口（导出 / 离线抓帧 / 重同步）。
 * <p>
 * UI 帧循环仍由 {@link com.director.ui.timeline.playback.PlaybackOrchestrator} 协调。
 */
public final class TimelinePlaybackSampler {

    private TimelinePlaybackSampler() {
    }

    /**
     * 在指定时间点采样完整世界状态（环境/演员/方块片段，不含相机）。
     */
    public static void sampleWorldAt(float timeSeconds) {
        sampleWorldAt(timeSeconds, TimelineSamplingPolicy.PREVIEW);
    }

    public static void sampleWorldForExport(float timeSeconds) {
        if (!Float.isFinite(timeSeconds) || timeSeconds < 0) throw new IllegalArgumentException("导出采样时间无效");
        sampleWorldAt(timeSeconds, TimelineSamplingPolicy.EXPORT);
    }

    private static void sampleWorldAt(float timeSeconds, TimelineSamplingPolicy policy) {
        TimelineModel model = resolveModel();
        if (model == null) {
            if (policy == TimelineSamplingPolicy.EXPORT) throw new IllegalStateException("导出时间线已关闭或被卸载");
            return;
        }
        model.setCurrentTime(timeSeconds);
        policy.run("点光源", timeSeconds, () -> com.director.lighting.PointLightRuntime.update(model));
        driveSubsystems(timeSeconds, policy);
        policy.run("方块片段", timeSeconds, () -> TimelineClipBlockController.getInstance().update());
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

    private static void driveSubsystems(float timeSeconds, TimelineSamplingPolicy policy) {
        policy.run("季节采样", timeSeconds, () -> TimelineSeasonController.getInstance().onTimelineTick(timeSeconds));
        policy.run("天气采样", timeSeconds, () -> TimelineWeatherController.getInstance().updateForPlayback(timeSeconds));
        policy.run("世界时间", timeSeconds, () -> TimelineWorldController.getInstance().update());
        policy.run("演员运行时", timeSeconds, () -> com.director.actor.runtime.ActorRuntimeController.getInstance().update());
        policy.run("WeatherRenderer", timeSeconds, () -> com.director.client.renderer.weather.WeatherRenderer.get().tick());
        policy.run("天气事件", timeSeconds, () -> TimelineWeatherController.getInstance().tickEvents(timeSeconds));
    }

}
