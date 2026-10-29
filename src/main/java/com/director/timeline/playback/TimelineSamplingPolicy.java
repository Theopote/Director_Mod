package com.director.timeline.playback;

import org.slf4j.LoggerFactory;

/** Interactive preview may recover; exports must never silently omit a failed subsystem. */
enum TimelineSamplingPolicy {
    PREVIEW, EXPORT;

    void run(String subsystem, float timeSeconds, Runnable sample) {
        try {
            sample.run();
        } catch (RuntimeException failure) {
            if (failure instanceof java.util.concurrent.CancellationException) throw failure;
            if (this == EXPORT) throw new IllegalStateException(
                    "导出采样失败：" + subsystem + "，时间 " + timeSeconds + "s：" + failure.getMessage(), failure);
            LoggerFactory.getLogger(TimelinePlaybackSampler.class).warn("[TimelinePlaybackSampler] {} 失败", subsystem, failure);
        }
    }
}
