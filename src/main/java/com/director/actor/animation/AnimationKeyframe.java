package com.director.actor.animation;

import com.director.actor.rig.Pose;

/**
 * AnimationKeyframe v1.0
 *
 * <p>动画关键帧，包含时间和姿态。
 *
 * @author Director Team
 * @version 1.0
 */
public class AnimationKeyframe {

    private final float time;
    private final Pose pose;

    /**
     * 构造函数
     *
     * @param time 时间（秒）
     * @param pose 姿态
     */
    public AnimationKeyframe(float time, Pose pose) {
        this.time = time;
        this.pose = pose;
    }

    /**
     * 获取时间
     *
     * @return 时间（秒）
     */
    public float getTime() {
        return time;
    }

    /**
     * 获取姿态
     *
     * @return 姿态
     */
    public Pose getPose() {
        return pose;
    }
}
