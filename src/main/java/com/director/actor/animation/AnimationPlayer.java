package com.director.actor.animation;

import com.director.actor.rig.AnimationPoseResult;

/**
 * AnimationPlayer v1.0
 *
 * <p>统一的 Director 关键帧动画播放器接口。
 *
 * <p>设计理念：
 * <ul>
 *   <li>播放器只负责播放，不负责渲染</li>
 *   <li>输出 AnimationPoseResult，由 Runtime 混合</li>
 *   <li>支持循环、速度控制、暂停/恢复</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public interface AnimationPlayer {

    /**
     * 播放动画
     *
     * @param animationId 动画ID
     * @param loop 是否循环
     * @param speed 播放速度倍率（1.0 = 正常速度）
     */
    void play(String animationId, boolean loop, float speed);

    /**
     * 停止当前动画
     */
    void stop();

    /**
     * 暂停动画
     */
    void pause();

    /**
     * 恢复播放
     */
    void resume();

    /**
     * 更新动画状态（每帧调用）
     *
     * @param deltaTime 时间增量（秒）
     */
    void update(float deltaTime);

    /**
     * 获取当前动画的姿态
     *
     * @return 当前姿态，如果没有动画则返回 null
     */
    AnimationPoseResult getCurrentPose();

    /**
     * 获取当前播放进度
     *
     * @return 进度 [0.0-1.0]
     */
    float getProgress();

    /**
     * 获取当前动画时长
     *
     * @return 时长（秒），如果没有动画则返回 0
     */
    float getDuration();

    /**
     * 检查是否正在播放
     *
     * @return 如果正在播放则返回 true
     */
    boolean isPlaying();

    /**
     * 获取当前动画ID
     *
     * @return 动画ID，如果没有动画则返回 null
     */
    String getCurrentAnimationId();
}
