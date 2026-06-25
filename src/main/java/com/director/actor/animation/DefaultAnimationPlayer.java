package com.director.actor.animation;

import com.director.actor.rig.AnimationPoseResult;
import com.director.actor.rig.Pose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultAnimationPlayer v1.0
 *
 * <p>默认的动画播放器实现。
 *
 * <p>特点：
 * <ul>
 *   <li>支持循环播放</li>
 *   <li>支持速度控制</li>
 *   <li>支持暂停/恢复</li>
 *   <li>从 AnimationLibrary 加载动画</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class DefaultAnimationPlayer implements AnimationPlayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAnimationPlayer.class);

    /** 当前动画ID */
    private String currentAnimationId;

    /** 当前动画数据 */
    private AnimationData currentAnimation;

    /** 当前播放时间（秒） */
    private float currentTime = 0f;

    /** 是否循环 */
    private boolean loop = false;

    /** 播放速度倍率 */
    private float speed = 1.0f;

    /** 是否正在播放 */
    private boolean playing = false;

    /** 是否暂停 */
    private boolean paused = false;

    @Override
    public void play(String animationId, boolean loop, float speed) {
        if (animationId == null || animationId.isBlank()) {
            LOGGER.warn("[AnimationPlayer] 动画ID为空，无法播放");
            return;
        }

        // 从动画库加载动画
        var animOpt = AnimationLibrary.getInstance().get(animationId);
        if (animOpt.isEmpty()) {
            LOGGER.warn("[AnimationPlayer] 动画不存在: {}", animationId);
            return;
        }

        this.currentAnimationId = animationId;
        this.currentAnimation = animOpt.get();
        this.loop = loop;
        this.speed = Math.max(0.01f, speed);
        this.currentTime = 0f;
        this.playing = true;
        this.paused = false;

        LOGGER.debug("[AnimationPlayer] 播放动画: {} (loop={}, speed={})",
                animationId, loop, speed);
    }

    @Override
    public void stop() {
        if (currentAnimation != null) {
            LOGGER.debug("[AnimationPlayer] 停止动画: {}", currentAnimationId);
        }

        this.currentAnimationId = null;
        this.currentAnimation = null;
        this.currentTime = 0f;
        this.playing = false;
        this.paused = false;
    }

    @Override
    public void pause() {
        if (playing && !paused) {
            paused = true;
            LOGGER.debug("[AnimationPlayer] 暂停动画: {}", currentAnimationId);
        }
    }

    @Override
    public void resume() {
        if (playing && paused) {
            paused = false;
            LOGGER.debug("[AnimationPlayer] 恢复动画: {}", currentAnimationId);
        }
    }

    @Override
    public void update(float deltaTime) {
        if (!playing || paused || currentAnimation == null) {
            return;
        }

        float duration = currentAnimation.getDuration();
        if (duration <= 0f) {
            // 零时长动画无法推进时间，直接视为结束
            currentTime = 0f;
            if (!loop) {
                playing = false;
            }
            return;
        }

        // 更新播放时间
        currentTime += deltaTime * speed;

        // 检查是否播放完毕
        if (currentTime >= duration) {
            if (loop) {
                // 循环播放
                currentTime = currentTime % duration;
            } else {
                // 停止播放
                currentTime = duration;
                playing = false;
                LOGGER.debug("[AnimationPlayer] 动画播放完毕: {}", currentAnimationId);
            }
        }
    }

    @Override
    public AnimationPoseResult getCurrentPose() {
        if (currentAnimation == null) {
            return null;
        }

        // 从动画数据中采样姿态
        Pose pose = currentAnimation.sampleAt(currentTime);

        // 创建 AnimationPoseResult
        // 默认控制所有部位（可以根据动画数据调整）
        return new AnimationPoseResult(pose, com.director.actor.rig.PoseMask.full());
    }

    @Override
    public float getProgress() {
        if (currentAnimation == null || currentAnimation.getDuration() <= 0) {
            return 0f;
        }

        return Math.min(1.0f, currentTime / currentAnimation.getDuration());
    }

    @Override
    public float getDuration() {
        if (currentAnimation == null) {
            return 0f;
        }
        return currentAnimation.getDuration();
    }

    @Override
    public boolean isPlaying() {
        return playing && !paused;
    }

    @Override
    public String getCurrentAnimationId() {
        return currentAnimationId;
    }

    /**
     * 获取当前播放时间
     *
     * @return 当前时间（秒）
     */
    public float getCurrentTime() {
        return currentTime;
    }

    /**
     * 设置当前播放时间
     *
     * @param time 时间（秒）
     */
    public void setCurrentTime(float time) {
        if (currentAnimation != null) {
            this.currentTime = Math.max(0f, Math.min(time, currentAnimation.getDuration()));
        }
    }
}
