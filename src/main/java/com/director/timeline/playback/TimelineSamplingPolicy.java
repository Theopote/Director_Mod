package com.director.timeline.playback;

import org.slf4j.LoggerFactory;

/** Interactive preview may recover; export/headless sampling must not silently omit a subsystem. */
enum TimelineSamplingPolicy {
    PREVIEW,
    EXPORT,
    HEADLESS;

    static TimelineSamplingPolicy forContext(TimelineSamplingContext context) {
        return switch (context.mode()) {
            case PREVIEW -> PREVIEW;
            case EXPORT -> EXPORT;
            case HEADLESS -> HEADLESS;
        };
    }

    void run(String subsystem, float timeSeconds, Runnable sample) {
        try {
            sample.run();
        } catch (RuntimeException failure) {
            if (failure instanceof java.util.concurrent.CancellationException) throw failure;
            if (this != PREVIEW) {
                String label = this == EXPORT ? "导出" : "无头";
                throw new IllegalStateException(
                        label + "采样失败：" + subsystem + "，时间 " + timeSeconds + "s：" + failure.getMessage(), failure);
            }
            LoggerFactory.getLogger(TimelinePlaybackSampler.class).warn("[TimelinePlaybackSampler] {} 失败", subsystem, failure);
        }
    }
}
