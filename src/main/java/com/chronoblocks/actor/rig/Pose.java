package com.chronoblocks.actor.rig;

/**
 * Pose v1.0
 *
 * <p>姿态快照，描述"我这一帧想让模型摆成什么样"。
 *
 * <p>这是 Animation 和 RigProfile 混合的中间数据结构。
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class Pose {

    // ========== 身体姿态 ==========

    /** 身体俯仰（弧度） */
    public float bodyPitch = 0f;

    /** 身体左右（弧度） */
    public float bodyYaw = 0f;

    /** 身体翻滚（弧度） */
    public float bodyRoll = 0f;

    // ========== 头部姿态 ==========

    /** 头部俯仰（弧度） */
    public float headPitch = 0f;

    /** 头部左右（弧度） */
    public float headYaw = 0f;

    // ========== 手臂姿态 ==========

    /** 左臂俯仰（弧度） */
    public float leftArmPitch = 0f;

    /** 右臂俯仰（弧度） */
    public float rightArmPitch = 0f;

    // ========== 腿部姿态 ==========

    /** 左腿俯仰（弧度） */
    public float leftLegPitch = 0f;

    /** 右腿俯仰（弧度） */
    public float rightLegPitch = 0f;

    /**
     * 重置所有姿态参数到默认值。
     */
    public void reset() {
        bodyPitch = bodyYaw = bodyRoll = 0f;
        headPitch = headYaw = 0f;
        leftArmPitch = rightArmPitch = 0f;
        leftLegPitch = rightLegPitch = 0f;
    }
}
