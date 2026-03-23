package com.director.actor.rig;

/**
 * AnimationPoseResult v1.0
 *
 * <p>动画姿态结果，包含姿态和控制掩码。
 *
 * <p>AnimationClip 只负责：
 * <ul>
 *   <li>生成 Pose</li>
 *   <li>声明它控制哪些部位</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class AnimationPoseResult {

    /** 动画生成的姿态 */
    public final Pose pose;

    /** 动画控制哪些部位 */
    public final PoseMask mask;

    /**
     * 构造函数
     *
     * @param pose 姿态
     * @param mask 控制掩码
     */
    public AnimationPoseResult(Pose pose, PoseMask mask) {
        this.pose = pose != null ? pose : new Pose();
        this.mask = mask != null ? mask : PoseMask.none();
    }
}
