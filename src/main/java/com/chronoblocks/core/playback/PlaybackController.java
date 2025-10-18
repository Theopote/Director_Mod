package com.chronoblocks.core.playback;

import com.chronoblocks.core.animation.AnimationClip;
import com.chronoblocks.core.animation.AnimationState;
import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.core.animation.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChronoBlocks 播放控制器（v4.5 一体化交互系统）
 * 
 * 动画播放的核心驱动引擎，负责：
 * - 播放控制（Play / Pause / Stop）
 * - 循环控制（Loop）
 * - 速度控制（Speed）
 * - 时间跳转（Seek）
 * - 帧驱动更新（Tick）
 * 
 * 数据流：
 * Toolbar → PlaybackController → AnimationState → Timeline 显示
 * 
 * @author ChronoBlocks Team
 * @version 4.5
 */
public class PlaybackController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackController.class);
    private static final PlaybackController INSTANCE = new PlaybackController();

    // ========== 播放控制状态 ==========
    private boolean loop = false;
    private float speed = 1.0f; // 1x

    private PlaybackController() {}

    public static PlaybackController get() { return INSTANCE; }

    // ========== 播放控制 ==========
    
    /**
     * 开始播放动画
     */
    public void play() { 
        AnimationState state = AnimationState.get();
        state.setPlaying(true); 
        LOGGER.debug("[Playback] Animation started");
    }
    
    /**
     * 暂停播放动画
     */
    public void pause() { 
        AnimationState state = AnimationState.get();
        state.setPlaying(false); 
        LOGGER.debug("[Playback] Animation paused");
    }
    
    /**
     * 停止播放并重置到起点
     */
    public void stop() { 
        AnimationState state = AnimationState.get();
        state.setPlaying(false); 
        state.setCurrentTime(0f); 
        LOGGER.debug("[Playback] Animation stopped and reset to 0");
    }
    
    /**
     * 切换循环模式
     */
    public void toggleLoop() { 
        loop = !loop; 
        LOGGER.debug("[Playback] Loop {}", loop ? "enabled" : "disabled");
    }

    /**
     * 跳转到指定时间
     * @param time 目标时间（秒）
     */
    public void seek(float time) {
        AnimationState state = AnimationState.get();
        state.setCurrentTime(time);
        LOGGER.trace("[Playback] Seeked to time: {}", time);
    }

    // ========== 状态查询 ==========
    
    public boolean isPlaying() { 
        return AnimationState.get().isPlaying(); 
    }
    
    public boolean isLoop() { 
        return loop; 
    }

    public void setSpeed(float speed) { 
        this.speed = Math.max(0.1f, Math.min(speed, 5.0f)); 
        LOGGER.debug("[Playback] Speed set to {}", this.speed);
    }
    
    public float getSpeed() { 
        return speed; 
    }

    // ========== 帧更新驱动 ==========
    
    /**
     * 更新播放状态（每帧调用）
     * @param deltaSeconds 上一帧到当前帧的时间差（秒）
     */
    public void update(float deltaSeconds) {
        AnimationState state = AnimationState.get();
        
        // 只在播放状态下更新时间
        if (!state.isPlaying()) return;
        
        AnimationClip clip = state.getClip();
        if (clip == null) return;

        // 计算新时间
        float t = state.getCurrentTime() + deltaSeconds * speed;
        float duration = state.getDuration();

        // 处理超出范围
        if (t > duration) {
            if (loop) {
                t = 0f; // 循环回到起点
                LOGGER.trace("[Playback] Looped to start");
            } else {
                t = duration; // 停在终点
                state.setPlaying(false);
                LOGGER.debug("[Playback] Reached end, stopped");
            }
        }
        if (t < 0f) t = 0f;
        
        state.setCurrentTime(t);

        // 采样并应用动画到容器系统
        sampleAndApplyAnimation(t, clip);
    }
    
    /**
     * 采样动画数据并应用到容器（v4.5）
     */
    private void sampleAndApplyAnimation(float time, AnimationClip clip) {
        // Sample each track at time t and apply to container system
        for (Track track : clip.getTracks()) {
            Keyframe sample = track.sample(time);
            
            if (sample != null) {
                // 应用动画到容器
                com.chronoblocks.core.animation.AnimationApplicator.get()
                    .applyAnimation(track, sample);
                
                LOGGER.trace("[Playback] Track '{}' sampled at t={}: {}", 
                    track.getName(), time, sample);
            }
        }
    }
}
