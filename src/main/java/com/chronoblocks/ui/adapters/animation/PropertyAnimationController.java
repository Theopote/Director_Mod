package com.chronoblocks.ui.adapters.animation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🎮 PropertyAnimationController
 * ------------------------------------------------------
 * 属性动画控制器：负责管理插值的时间推进、播放状态、循环逻辑。
 * 与时间线系统同步，驱动各对象的属性平滑更新。
 *
 * 支持功能：
 *  ✅ 播放 / 暂停 / 回放
 *  ✅ 速度倍率（speed multiplier）
 *  ✅ 帧同步更新（tick-driven）
 *  ✅ 循环播放（loop）
 *  ✅ 多轨道并行管理
 *
 * @author ChronoBlocks Team
 * @version 4.6
 */
public class PropertyAnimationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyAnimationController.class);

    /** 当前是否正在播放 */
    private boolean playing = false;

    /** 是否循环播放 */
    private boolean loop = false;

    /** 播放速度倍数 */
    private float speed = 1.0f;

    /** 当前时间（秒） */
    private float currentTime = 0f;

    /** 动画总时长（秒） */
    private float duration = 10f;

    /** 帧步进（tick）时长（秒） */
    private final float deltaTime = 1f / 60f; // 60 FPS

    /** 注册的动画通道（一个通道对应一个属性动画） */
    private final Map<String, AnimationChannel<?>> channels = new ConcurrentHashMap<>();

    // ===============================
    // 🎮 播放控制
    // ===============================

    public void play() {
        playing = true;
        LOGGER.debug("[AnimationController] ▶ 播放开始");
    }

    public void pause() {
        playing = false;
        LOGGER.debug("[AnimationController] ⏸ 暂停");
    }

    public void stop() {
        playing = false;
        currentTime = 0;
        LOGGER.debug("[AnimationController] ⏹ 停止并重置时间");
    }

    public void toggle() {
        if (playing) pause(); else play();
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.01f, speed);
    }

    public void setDuration(float duration) {
        this.duration = Math.max(0.01f, duration);
    }

    public float getCurrentTime() {
        return currentTime;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isLoop() {
        return loop;
    }

    public float getSpeed() {
        return speed;
    }

    public float getDuration() {
        return duration;
    }

    // ===============================
    // 🕓 主 Tick 循环
    // ===============================

    /**
     * 每帧调用（通常在主渲染或逻辑线程中）
     */
    public void tick() {
        if (!playing) return;

        currentTime += deltaTime * speed;
        if (currentTime > duration) {
            if (loop) {
                currentTime %= duration;
                LOGGER.trace("[AnimationController] 循环回到 0");
            } else {
                currentTime = duration;
                pause();
            }
        }

        // 更新所有通道
        for (AnimationChannel<?> channel : channels.values()) {
            channel.update(currentTime);
        }
    }

    // ===============================
    // 🔗 通道系统
    // ===============================

    /**
     * 注册动画通道
     */
    public <T> void registerChannel(String id, AnimationChannel<T> channel) {
        channels.put(id, channel);
        LOGGER.debug("[AnimationController] 注册通道: {}", id);
    }

    /**
     * 移除动画通道
     */
    public void removeChannel(String id) {
        channels.remove(id);
        LOGGER.debug("[AnimationController] 移除通道: {}", id);
    }

    /**
     * 清空所有通道
     */
    public void clearChannels() {
        channels.clear();
    }

    /**
     * 获取通道
     */
    @SuppressWarnings("unchecked")
    public <T> AnimationChannel<T> getChannel(String id) {
        return (AnimationChannel<T>) channels.get(id);
    }

    /**
     * 获取所有通道ID
     */
    public Set<String> getChannelIds() {
        return Collections.unmodifiableSet(channels.keySet());
    }

    /**
     * 获取通道数量
     */
    public int getChannelCount() {
        return channels.size();
    }

    // ===============================
    // 🎞 通道类定义
    // ===============================

    public static class AnimationChannel<T> {
        private final String name;
        private final List<PropertyAnimationInterpolator.Keyframe<T>> keyframes;
        private final PropertyAnimationInterpolator.Easing defaultEasing;
        private final ValueUpdater<T> updater;

        public AnimationChannel(String name,
                                List<PropertyAnimationInterpolator.Keyframe<T>> keyframes,
                                PropertyAnimationInterpolator.Easing defaultEasing,
                                ValueUpdater<T> updater) {
            this.name = name;
            this.keyframes = keyframes;
            this.defaultEasing = defaultEasing;
            this.updater = updater;
        }

        /**
         * 在指定时间点更新属性
         */
        public void update(float time) {
            if (keyframes.isEmpty()) return;
            PropertyAnimationInterpolator interp = PropertyAnimationInterpolator.get();
            T value = interp.interpolateKeyframes(keyframes, time);
            if (value != null) updater.update(value);
        }

        public String getName() { return name; }
        
        public List<PropertyAnimationInterpolator.Keyframe<T>> getKeyframes() { 
            return Collections.unmodifiableList(keyframes); 
        }
        
        public PropertyAnimationInterpolator.Easing getDefaultEasing() { 
            return defaultEasing; 
        }
    }

    /**
     * 用于在插值完成后回写属性值的回调接口。
     */
    @FunctionalInterface
    public interface ValueUpdater<T> {
        void update(T newValue);
    }

    // ===============================
    // 🧠 快速示例
    // ===============================

    public static PropertyAnimationController demo() {
        PropertyAnimationController controller = new PropertyAnimationController();

        List<PropertyAnimationInterpolator.Keyframe<Float>> posYFrames = List.of(
            new PropertyAnimationInterpolator.Keyframe<>(0f, 0f, PropertyAnimationInterpolator.Easing.EASE_IN),
            new PropertyAnimationInterpolator.Keyframe<>(2f, 10f, PropertyAnimationInterpolator.Easing.EASE_OUT),
            new PropertyAnimationInterpolator.Keyframe<>(4f, 0f, PropertyAnimationInterpolator.Easing.SMOOTHSTEP)
        );

        controller.registerChannel(
            "posY",
            new AnimationChannel<>(
                "Y轴动画",
                posYFrames,
                PropertyAnimationInterpolator.Easing.SMOOTHSTEP,
                val -> LOGGER.info("Y轴高度 = {}", val)
            )
        );

        controller.setDuration(4f);
        controller.setLoop(true);
        controller.play();

        return controller;
    }
}

