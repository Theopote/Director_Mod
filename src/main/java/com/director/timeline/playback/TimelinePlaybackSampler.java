package com.director.timeline.playback;

import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.playback.season.TimelineSeasonController;
import com.director.timeline.playback.time.TimelineWorldController;
import com.director.timeline.playback.weather.TimelineWeatherController;
import com.director.timeline.playback.block.TimelineClipBlockController;

import java.util.Objects;

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
        sampleWorldAt(timeSeconds, TimelineSamplingContext.PREVIEW);
    }

    public static void sampleWorldForExport(float timeSeconds) {
        sampleWorldAt(timeSeconds, TimelineSamplingContext.EXPORT);
    }

    public static void sampleWorldHeadless(float timeSeconds) {
        sampleWorldAt(timeSeconds, TimelineSamplingContext.HEADLESS);
    }

    public static void sampleWorldAt(float timeSeconds, TimelineSamplingContext context) {
        Objects.requireNonNull(context, "context");
        if (!Float.isFinite(timeSeconds) || timeSeconds < 0) {
            String label = context != null && context.mode() == TimelineSamplingContext.Mode.PREVIEW
                    ? "采样" : context != null && context.mode() == TimelineSamplingContext.Mode.HEADLESS
                    ? "无头采样" : "导出";
            throw new IllegalArgumentException(label + "时间无效");
        }
        try (var ignored = TimelineSamplingContext.open(context)) {
            TimelineModel model = resolveModel();
            if (model == null) {
                if (context.isStrict()) {
                    String label = context.mode() == TimelineSamplingContext.Mode.HEADLESS ? "无头" : "导出";
                    throw new IllegalStateException(label + "时间线已关闭或被卸载");
                }
                return;
            }
            TimelineSamplingPolicy policy = TimelineSamplingPolicy.forContext(context);
            HeadlessPlaybackState headlessState = context.mode() == TimelineSamplingContext.Mode.HEADLESS
                    ? HeadlessPlaybackState.captureAndSuspend(model) : null;
            try {
                model.setCurrentTime(timeSeconds);
                policy.run("点光源", timeSeconds, () -> com.director.lighting.PointLightRuntime.update(model));
                driveSubsystems(timeSeconds, policy);
                policy.run("方块片段", timeSeconds, () -> TimelineClipBlockController.getInstance().update());
            } finally {
                if (headlessState != null) headlessState.restore(model);
            }
        }
    }

    static record HeadlessPlaybackState(float time, boolean playing, boolean scrubbing) {
        static HeadlessPlaybackState captureAndSuspend(TimelineModel model) {
            var state = new HeadlessPlaybackState(model.getCurrentTime(), model.isPlaying(), model.isScrubbing());
            model.setPlaying(false);
            model.setScrubbing(false);
            return state;
        }

        void restore(TimelineModel model) {
            model.setCurrentTime(time);
            model.setScrubbing(scrubbing);
            model.setPlaying(playing);
        }
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
