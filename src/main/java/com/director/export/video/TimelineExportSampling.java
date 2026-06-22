package com.director.export.video;

import com.director.timeline.playback.TimelinePlaybackSampler;
import com.director.ui.timeline.playback.TimelineClipBlockController;

/**
 * 导出帧：在客户端线程采样时间线世界状态（不含相机）。
 */
public final class TimelineExportSampling {

    private TimelineExportSampling() {
    }

    public static void sampleWorldAt(float timeSeconds) {
        TimelinePlaybackSampler.sampleAt(timeSeconds);
        try {
            TimelineClipBlockController.getInstance().update();
        } catch (RuntimeException ignored) {
        }
    }
}
