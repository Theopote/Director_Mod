package com.director.actor.rig;

/**
 * Pose v1.0
 *
 * <p>姿态快照，描述"我这一帧想让模型摆成什么样"。
 *
 * <p>这是 Animation 和 RigProfile 混合的中间数据结构。
 *
 * @author Director Team
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
    public float leftArmYaw = 0f;
    public float leftArmRoll = 0f;

    /** 右臂俯仰（弧度） */
    public float rightArmPitch = 0f;
    public float rightArmYaw = 0f;
    public float rightArmRoll = 0f;

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
        leftArmPitch = leftArmYaw = leftArmRoll = 0f;
        rightArmPitch = rightArmYaw = rightArmRoll = 0f;
        leftLegPitch = rightLegPitch = 0f;
    }

    /**
     * 复制姿态
     *
     * @return 姿态副本
     */
    public Pose copy() {
        Pose copy = new Pose();
        copy.bodyPitch = this.bodyPitch;
        copy.bodyYaw = this.bodyYaw;
        copy.bodyRoll = this.bodyRoll;
        copy.headPitch = this.headPitch;
        copy.headYaw = this.headYaw;
        copy.leftArmPitch = this.leftArmPitch;
        copy.leftArmYaw = this.leftArmYaw;
        copy.leftArmRoll = this.leftArmRoll;
        copy.rightArmPitch = this.rightArmPitch;
        copy.rightArmYaw = this.rightArmYaw;
        copy.rightArmRoll = this.rightArmRoll;
        copy.leftLegPitch = this.leftLegPitch;
        copy.rightLegPitch = this.rightLegPitch;
        return copy;
    }

    /**
     * 线性插值两个姿态
     *
     * @param a 起始姿态
     * @param b 结束姿态
     * @param t 插值系数 [0.0-1.0]
     * @return 插值后的姿态
     */
    public static Pose lerp(Pose a, Pose b, float t) {
        t = Math.max(0f, Math.min(1f, t));

        Pose result = new Pose();
        result.bodyPitch = lerp(a.bodyPitch, b.bodyPitch, t);
        result.bodyYaw = lerp(a.bodyYaw, b.bodyYaw, t);
        result.bodyRoll = lerp(a.bodyRoll, b.bodyRoll, t);
        result.headPitch = lerp(a.headPitch, b.headPitch, t);
        result.headYaw = lerp(a.headYaw, b.headYaw, t);
        result.leftArmPitch = lerp(a.leftArmPitch, b.leftArmPitch, t);
        result.leftArmYaw = lerp(a.leftArmYaw, b.leftArmYaw, t);
        result.leftArmRoll = lerp(a.leftArmRoll, b.leftArmRoll, t);
        result.rightArmPitch = lerp(a.rightArmPitch, b.rightArmPitch, t);
        result.rightArmYaw = lerp(a.rightArmYaw, b.rightArmYaw, t);
        result.rightArmRoll = lerp(a.rightArmRoll, b.rightArmRoll, t);
        result.leftLegPitch = lerp(a.leftLegPitch, b.leftLegPitch, t);
        result.rightLegPitch = lerp(a.rightLegPitch, b.rightLegPitch, t);
        return result;
    }

    /**
     * 线性插值（辅助方法）
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
