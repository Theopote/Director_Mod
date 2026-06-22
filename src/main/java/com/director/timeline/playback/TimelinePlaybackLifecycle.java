package com.director.timeline.playback;

/**
 * 时间线内存重置时的播放子系统协作端口（由 UI 层在初始化时接线）。
 */
public interface TimelinePlaybackLifecycle {

    TimelinePlaybackLifecycle NOOP = () -> {};

    void onTimelineMemoryReset();
}
