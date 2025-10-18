package com.chronoblocks.timeline.sync;

import com.chronoblocks.ui.adapters.animation.PropertyAnimationController;
import com.chronoblocks.ui.adapters.animation.PropertyAnimationTimelineBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎬 TimelinePlaybackSynchronizer
 * ------------------------------------------------------
 * 统一管理 ChronoBlocks 编辑器中的播放状态。
 *
 * <h3>职责：</h3>
 * <ul>
 *  <li>✅ 同步 TimelinePanel 播放按钮、PropertyAnimationController 的播放状态</li>
 *  <li>✅ 广播"播放/暂停/循环"事件到外部容器（如场景对象、特效模块）</li>
 *  <li>✅ 管理全局播放速度（包括倍速预览）</li>
 *  <li>✅ 支持延迟与节流的事件派发，确保主线程安全</li>
 * </ul>
 *
 * <p>该类是 PropertyAnimationTimelineBridge 的上层协调者。
 *
 * <h3>架构位置：</h3>
 * <pre>
 * [Timeline UI] ⇄ [TimelinePlaybackSynchronizer] ⇄ [PropertyAnimationController]
 *                              ⇅
 *                         [Scene/Container System]
 * </pre>
 *
 * @author ChronoBlocks Team
 * @version 4.8
 * @since 2025-10-18
 */
public class TimelinePlaybackSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePlaybackSynchronizer.class);

    // =========================================
    // 🔧 依赖组件
    // =========================================

    private final PropertyAnimationController controller;
    private final PropertyAnimationTimelineBridge bridge;

    /** 当前序列名称 */
    private String currentSequenceName;

    /** 序列时长 */
    private float sequenceDuration = 10.0f;

    // =========================================
    // ⚙️ 播放状态
    // =========================================

    private boolean isPlaying = false;
    private boolean isLoop = false;
    private float playbackSpeed = 1.0f; // 倍速（默认1x）

    // 时间缓存（用于同步暂停/恢复）
    private float lastTimelineTime = 0f;

    // 事件监听器列表
    private final java.util.List<PlaybackStateListener> listeners = 
        new java.util.concurrent.CopyOnWriteArrayList<>();

    // =========================================
    // 🧩 构造与绑定
    // =========================================

    public TimelinePlaybackSynchronizer(PropertyAnimationController controller,
                                        PropertyAnimationTimelineBridge bridge) {
        this.controller = controller;
        this.bridge = bridge;
        LOGGER.info("[PlaybackSync] TimelinePlaybackSynchronizer 已创建");
    }

    /**
     * 绑定当前的时间线序列
     * 
     * @param sequenceName 序列名称
     * @param duration 序列时长
     * @param loop 是否循环
     */
    public void bindSequence(String sequenceName, float duration, boolean loop) {
        this.currentSequenceName = sequenceName;
        this.sequenceDuration = duration;
        this.isLoop = loop;
        controller.setLoop(loop);
        controller.setDuration(duration);
        LOGGER.info("[PlaybackSync] 绑定时间线序列: {} (时长: {}s, 循环: {})", 
            sequenceName, duration, loop);
    }

    // =========================================
    // ▶️ 播放控制
    // =========================================

    /**
     * 开始播放动画（时间线与控制器同步）
     */
    public void play() {
        if (isPlaying) {
            LOGGER.debug("[PlaybackSync] 已在播放中，忽略");
            return;
        }
        if (currentSequenceName == null) {
            LOGGER.warn("[PlaybackSync] 未绑定序列，无法播放");
            return;
        }

        isPlaying = true;
        LOGGER.info("[PlaybackSync] ▶ 播放开始");

        controller.play();
        broadcastState(PlaybackState.PLAY);
    }

    /**
     * 暂停动画播放
     */
    public void pause() {
        if (!isPlaying) {
            LOGGER.debug("[PlaybackSync] 未在播放，忽略暂停");
            return;
        }

        isPlaying = false;
        LOGGER.info("[PlaybackSync] ⏸ 暂停播放");

        controller.pause();
        lastTimelineTime = controller.getCurrentTime();
        broadcastState(PlaybackState.PAUSE);
    }

    /**
     * 停止播放（重置至起点）
     */
    public void stop() {
        if (!isPlaying && lastTimelineTime == 0) {
            LOGGER.debug("[PlaybackSync] 已停止，忽略");
            return;
        }

        isPlaying = false;
        lastTimelineTime = 0;
        LOGGER.info("[PlaybackSync] ⏹ 停止播放");

        controller.stop();
        broadcastState(PlaybackState.STOP);
    }

    /**
     * 切换播放/暂停
     */
    public void toggle() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }

    // =========================================
    // 🔁 循环控制
    // =========================================

    /**
     * 设置循环播放
     * 
     * @param loop 是否循环
     */
    public void setLoop(boolean loop) {
        if (this.isLoop == loop) {
            return;
        }
        
        this.isLoop = loop;
        controller.setLoop(loop);
        LOGGER.info("[PlaybackSync] 循环模式: {}", loop);
        broadcastState(PlaybackState.LOOP_CHANGED);
    }

    /**
     * 获取循环状态
     * 
     * @return 是否循环
     */
    public boolean isLoop() {
        return isLoop;
    }

    // =========================================
    // ⏩ 倍速控制
    // =========================================

    /**
     * 设置播放速度
     * 
     * @param speed 速度倍数（必须 > 0）
     */
    public void setPlaybackSpeed(float speed) {
        if (speed <= 0) {
            LOGGER.warn("[PlaybackSync] 无效速度: {}，重置为1.0x", speed);
            speed = 1.0f;
        }
        
        if (Math.abs(this.playbackSpeed - speed) < 0.001f) {
            return;
        }
        
        this.playbackSpeed = speed;
        controller.setSpeed(speed);
        LOGGER.info("[PlaybackSync] 倍速设置为: {}x", speed);
        broadcastState(PlaybackState.SPEED_CHANGED);
    }

    /**
     * 获取播放速度
     * 
     * @return 速度倍数
     */
    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    // =========================================
    // ⏱ 帧更新
    // =========================================

    /**
     * 每帧调用，用于将控制器时间同步回时间线
     * 
     * @param deltaTime 帧时间（秒）
     */
    public void updateFrame(float deltaTime) {
        if (!isPlaying || currentSequenceName == null) {
            return;
        }

        // 更新控制器（已内置速度倍率）
        controller.tick();

        float currentTime = controller.getCurrentTime();
        lastTimelineTime = currentTime;

        // 若超出时长则处理循环/停止
        if (currentTime >= sequenceDuration) {
            if (isLoop) {
                LOGGER.debug("[PlaybackSync] 循环回到起点");
                controller.stop();
                controller.play();
                lastTimelineTime = 0f;
            } else {
                LOGGER.debug("[PlaybackSync] 播放完成，自动停止");
                stop();
            }
        }
    }

    /**
     * 跳转到指定时间
     * 
     * @param time 目标时间（秒）
     */
    public void seekTo(float time) {
        if (currentSequenceName == null) {
            LOGGER.warn("[PlaybackSync] 未绑定序列，无法跳转");
            return;
        }

        // 限制在有效范围内
        time = Math.max(0f, Math.min(time, sequenceDuration));
        
        boolean wasPlaying = isPlaying;
        if (wasPlaying) {
            pause();
        }

        // 使用桥接器更新到指定时间点
        bridge.updateFromTimeline(time);
        lastTimelineTime = time;

        if (wasPlaying) {
            play();
        }

        LOGGER.debug("[PlaybackSync] 跳转到时间: {}", time);
        broadcastState(PlaybackState.SEEK);
    }

    // =========================================
    // 📡 状态广播
    // =========================================

    /**
     * 向外部模块广播播放状态
     * 
     * @param state 播放状态
     */
    private void broadcastState(PlaybackState state) {
        TimelinePlaybackEvent event = new TimelinePlaybackEvent(
            state, 
            playbackSpeed, 
            isLoop, 
            lastTimelineTime,
            sequenceDuration
        );

        // 通知所有监听器
        for (PlaybackStateListener listener : listeners) {
            try {
                listener.onPlaybackStateChanged(event);
            } catch (Exception e) {
                LOGGER.error("[PlaybackSync] 监听器异常: {}", e.getMessage(), e);
            }
        }

        LOGGER.trace("[PlaybackSync] 广播状态: {}", state);
    }

    // =========================================
    // 🎧 事件监听
    // =========================================

    /**
     * 添加播放状态监听器
     * 
     * @param listener 监听器
     */
    public void addListener(PlaybackStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            LOGGER.debug("[PlaybackSync] 添加监听器: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * 移除播放状态监听器
     * 
     * @param listener 监听器
     */
    public void removeListener(PlaybackStateListener listener) {
        if (listeners.remove(listener)) {
            LOGGER.debug("[PlaybackSync] 移除监听器: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * 清空所有监听器
     */
    public void clearListeners() {
        int count = listeners.size();
        listeners.clear();
        LOGGER.debug("[PlaybackSync] 清空 {} 个监听器", count);
    }

    // =========================================
    // 🧭 状态查询
    // =========================================

    /**
     * 是否正在播放
     * 
     * @return 播放状态
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * 获取当前时间
     * 
     * @return 当前播放时间（秒）
     */
    public float getCurrentTime() {
        return isPlaying ? controller.getCurrentTime() : lastTimelineTime;
    }

    /**
     * 获取序列时长
     * 
     * @return 时长（秒）
     */
    public float getDuration() {
        return sequenceDuration;
    }

    /**
     * 获取当前序列名称
     * 
     * @return 序列名称
     */
    public String getCurrentSequenceName() {
        return currentSequenceName;
    }

    /**
     * 获取播放进度（0.0 ~ 1.0）
     * 
     * @return 播放进度
     */
    public float getProgress() {
        if (sequenceDuration <= 0) {
            return 0f;
        }
        return Math.min(1.0f, getCurrentTime() / sequenceDuration);
    }

    /**
     * 获取调试信息
     * 
     * @return 调试信息字符串
     */
    public String getDebugInfo() {
        return String.format("TimelinePlaybackSynchronizer [序列: %s, 播放: %s, 时间: %.2f/%.2f (%.1f%%), 速度: %.2fx, 循环: %s, 监听器: %d]",
            currentSequenceName != null ? currentSequenceName : "未绑定",
            isPlaying ? "是" : "否",
            getCurrentTime(),
            sequenceDuration,
            getProgress() * 100,
            playbackSpeed,
            isLoop ? "是" : "否",
            listeners.size());
    }

    // =========================================
    // 🧹 清理
    // =========================================

    /**
     * 清空所有状态和监听器
     */
    public void clear() {
        stop();
        this.currentSequenceName = null;
        this.sequenceDuration = 10.0f;
        this.lastTimelineTime = 0f;
        clearListeners();
        LOGGER.info("[PlaybackSync] 状态已清空");
    }

    // =========================================
    // 📦 便利方法
    // =========================================

    /**
     * 获取动画控制器
     * 
     * @return 动画控制器
     */
    public PropertyAnimationController getController() {
        return controller;
    }

    /**
     * 获取时间线桥接器
     * 
     * @return 桥接器
     */
    public PropertyAnimationTimelineBridge getBridge() {
        return bridge;
    }
}

