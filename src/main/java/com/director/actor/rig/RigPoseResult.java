package com.director.actor.rig;

/**
 * RigPoseResult v1.0
 *
 * <p>RigProfile 姿态结果，包含姿态和控制掩码。
 *
 * <p>RigProfile 生成一个"语义 Pose" + Mask（通常是全身）。
 *
 * @author Director Team
 * @version 1.0
 */
public final class RigPoseResult {

    /** RigProfile 生成的姿态 */
    public final Pose pose;

    /** RigProfile 控制哪些部位（通常是全身） */
    public final PoseMask mask;

    /**
     * 构造函数
     *
     * @param pose 姿态
     * @param mask 控制掩码
     */
    public RigPoseResult(Pose pose, PoseMask mask) {
        this.pose = pose != null ? pose : new Pose();
        this.mask = mask != null ? mask : PoseMask.none();
    }
}
