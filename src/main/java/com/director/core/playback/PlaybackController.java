package com.director.core.playback;

import com.director.core.animation.playback.ClipPlaybackRuntimeManager;
import com.director.core.animation.timeline.AnimationTimelineService;
import com.director.core.animation.timeline.TimelineController;
import com.director.core.animation.container.AnimationClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Director 播放控制器（v4.5 一体化交互系统）
 * <p>
 * <strong>已废弃于导演时间线主路径</strong>：日常播放与导出均以 {@link com.director.timeline.model.TimelineModel}
 * + {@link com.director.ui.timeline.playback.PlaybackOrchestrator} 为唯一时钟。
 * 本类保留供遗留方块 pose 图求值；{@link #getDuration()} 在无 PlayableGraph 数据时恒为 0。
 *
 * @deprecated 新代码请使用 {@link com.director.ui.timeline.playback.PlaybackOrchestrator}。
 */
@Deprecated
public class PlaybackController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackController.class);
    private static final PlaybackController INSTANCE = new PlaybackController();

    // ========== 播放控制状态 ==========
    private boolean playing = false;
    private boolean loop = false;
    private float speed = 1.0f; // 1x
    private double currentTimeSec = 0.0;

    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

    private PlaybackController() {}

    public static PlaybackController get() { return INSTANCE; }

    // ========== 播放控制 ==========
    
    /**
     * 开始播放动画
     */
    public void play() { 
        if (playing) {
            return;
        }
        playing = true;
        ClipPlaybackRuntimeManager.get().update(currentTimeSec);
        LOGGER.debug("[Playback] Animation started");
        notifyListeners();
    }
    
    /**
     * 暂停播放动画
     */
    public void pause() { 
        if (!playing) {
            return;
        }
        playing = false;
        LOGGER.debug("[Playback] Animation paused");
        notifyListeners();
    }
    
    /**
     * 停止播放并重置到起点
     */
    public void stop() { 
        playing = false;
        currentTimeSec = 0.0;
        ClipPlaybackRuntimeManager.get().update(currentTimeSec);
        LOGGER.debug("[Playback] Animation stopped and reset to 0");
        notifyListeners();
    }
    
    /**
     * 切换循环模式
     */
    public void toggleLoop() { 
        loop = !loop; 
        LOGGER.debug("[Playback] Loop {}", loop ? "enabled" : "disabled");
        notifyListeners();
    }

    /**
     * 跳转到指定时间
     * @param time 目标时间（秒）
     */
    public void seek(float time) {
        double duration = computeDuration();
        currentTimeSec = duration > 0.0 ? Math.max(0.0, Math.min(time, duration)) : Math.max(0.0, time);
        ClipPlaybackRuntimeManager.get().update(currentTimeSec);
        LOGGER.trace("[Playback] Seeked to time: {}", currentTimeSec);
        notifyListeners();
    }

    // ========== 状态查询 ==========
    
    public boolean isPlaying() { 
        return playing; 
    }
    
    public boolean isLoop() { 
        return loop; 
    }

    public void setSpeed(float speed) { 
        this.speed = Math.max(0.1f, Math.min(speed, 5.0f)); 
        LOGGER.debug("[Playback] Speed set to {}", this.speed);
        notifyListeners();
    }
    
    public float getSpeed() { 
        return speed; 
    }

    public float getCurrentTime() {
        return (float) currentTimeSec;
    }

    public float getDuration() {
        return (float) computeDuration();
    }

    // ========== 帧更新驱动 ==========
    
    /**
     * 更新播放状态（每帧调用）
     * @param deltaSeconds 上一帧到当前帧的时间差（秒）
     */
    public void update(float deltaSeconds) {
        if (!playing) {
            return;
        }

        double duration = computeDuration();
        if (duration <= 0.0) {
            ClipPlaybackRuntimeManager.get().update(currentTimeSec);
            return;
        }

        double nextTime = currentTimeSec + deltaSeconds * speed;
        if (nextTime > duration) {
            if (loop) {
                nextTime = nextTime % duration;
                LOGGER.trace("[Playback] Looped to start");
            } else {
                nextTime = duration;
                playing = false;
                LOGGER.debug("[Playback] Reached end, stopped");
            }
        }
        if (nextTime < 0.0) {
            nextTime = 0.0;
        }
        currentTimeSec = nextTime;

        ClipPlaybackRuntimeManager.get().update(currentTimeSec);
        notifyListeners();
    }

    private double computeDuration() {
        TimelineController controller = AnimationTimelineService.get().getTimelineController();
        double maxEnd = 0.0;
        for (AnimationClip clip : controller.getPlayableGraph().getClips()) {
            maxEnd = Math.max(maxEnd, clip.getEnd());
        }
        return maxEnd;
    }

    private void notifyListeners() {
        double duration = computeDuration();
        PlaybackEvent event = new PlaybackEvent(
            playing,
            loop,
            speed,
            currentTimeSec,
            duration
        );
        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlaybackEvent(event);
            } catch (Exception e) {
                LOGGER.warn("[Playback] Listener threw exception", e);
            }
        }
    }

    public interface PlaybackListener {
        void onPlaybackEvent(PlaybackEvent event);
    }

    public record PlaybackEvent(
        boolean playing,
        boolean loop,
        float speed,
        double currentTimeSec,
        double durationSec
    ) {}
}
