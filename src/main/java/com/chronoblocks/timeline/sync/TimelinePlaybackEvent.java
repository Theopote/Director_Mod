package com.chronoblocks.timeline.sync;

/**
 * 🎬 TimelinePlaybackEvent
 * ------------------------------------------------------
 * 时间线播放状态事件。
 *
 * <p>当播放状态发生变化时，通过此事件通知所有监听器。
 *
 * @author ChronoBlocks Team
 * @version 4.8
 * @since 2025-10-18
 */
public class TimelinePlaybackEvent {
    
    /** 播放状态 */
    public final PlaybackState state;
    
    /** 播放速度倍数 */
    public final float speed;
    
    /** 是否循环 */
    public final boolean loop;
    
    /** 当前时间（秒） */
    public final float currentTime;
    
    /** 总时长（秒） */
    public final float duration;
    
    /**
     * 构造函数
     * 
     * @param state 播放状态
     * @param speed 播放速度
     * @param loop 是否循环
     * @param currentTime 当前时间
     * @param duration 总时长
     */
    public TimelinePlaybackEvent(PlaybackState state, float speed, boolean loop, 
                                 float currentTime, float duration) {
        this.state = state;
        this.speed = speed;
        this.loop = loop;
        this.currentTime = currentTime;
        this.duration = duration;
    }
    
    /**
     * 获取播放进度（0.0 ~ 1.0）
     * 
     * @return 播放进度
     */
    public float getProgress() {
        if (duration <= 0) {
            return 0f;
        }
        return Math.min(1.0f, currentTime / duration);
    }
    
    /**
     * 是否为播放事件
     * 
     * @return 如果是播放事件则返回 true
     */
    public boolean isPlaying() {
        return state == PlaybackState.PLAY;
    }
    
    /**
     * 是否为暂停事件
     * 
     * @return 如果是暂停事件则返回 true
     */
    public boolean isPaused() {
        return state == PlaybackState.PAUSE;
    }
    
    /**
     * 是否为停止事件
     * 
     * @return 如果是停止事件则返回 true
     */
    public boolean isStopped() {
        return state == PlaybackState.STOP;
    }
    
    @Override
    public String toString() {
        return String.format("TimelinePlaybackEvent{state=%s, speed=%.2fx, loop=%s, time=%.2f/%.2f (%.1f%%)}",
            state, speed, loop, currentTime, duration, getProgress() * 100);
    }
}

