package com.chronoblocks.timeline.sync;

/**
 * 🎧 PlaybackStateListener
 * ------------------------------------------------------
 * 播放状态监听器接口。
 *
 * <p>实现此接口以接收播放状态变化通知。
 *
 * @author ChronoBlocks Team
 * @version 4.8
 * @since 2025-10-18
 */
@FunctionalInterface
public interface PlaybackStateListener {
    
    /**
     * 当播放状态发生变化时调用
     * 
     * @param event 播放事件
     */
    void onPlaybackStateChanged(TimelinePlaybackEvent event);
}

