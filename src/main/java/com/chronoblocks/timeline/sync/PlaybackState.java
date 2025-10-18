package com.chronoblocks.timeline.sync;

/**
 * 🎬 PlaybackState
 * ------------------------------------------------------
 * 播放状态枚举。
 *
 * @author ChronoBlocks Team
 * @version 4.8
 * @since 2025-10-18
 */
public enum PlaybackState {
    /** 播放 */
    PLAY,
    
    /** 暂停 */
    PAUSE,
    
    /** 停止 */
    STOP,
    
    /** 跳转 */
    SEEK,
    
    /** 速度变化 */
    SPEED_CHANGED,
    
    /** 循环状态变化 */
    LOOP_CHANGED
}

