package com.chronoblocks.actor.runtime;

/**
 * ActorRuntime v1.0
 *
 * <p>导演级身体控制参数抽象。
 *
 * <p>这些不是 Minecraft API，而是你定义的"导演级身体参数"。
 * 真正和 Minecraft 模型对接的代码，只存在于最底层 Renderer / PreviewEntity 中。
 *
 * <p>设计原则：
 * <ul>
 *   <li>不绑定骨骼名称</li>
 *   <li>不绑定实体类型</li>
 *   <li>只描述"身体应该怎么动"</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorRuntime {

    // ========== 身体姿态参数 ==========

    /** 身体前倾角度（弧度，0 = 直立，正值 = 前倾） */
    private float bodyLean = 0f;

    /** 手臂摆动强度（0 = 不动，1 = 正常，>1 = 快速） */
    private float armSwing = 0f;

    /** 腿摆动幅度（用于人形和四足） */
    private float legSwing = 0f;

    /** 步频（弧度/秒，用于周期性摆动） */
    private float stepFrequency = 0f;

    /** 头部俯仰角度（弧度） */
    private float headPitch = 0f;

    /** 头部左右角度（弧度） */
    private float headYaw = 0f;

    /** 身体上下摆动（0 = 不动，正值 = 上下摆动幅度） */
    private float bodyBob = 0f;

    /** 身体扭转（弧度，用于跳舞等） */
    private float bodyTwist = 0f;

    /** 头部是否向下（用于吃草等） */
    private boolean headDown = false;

    /** 手臂是否锁定向前（用于搬运等） */
    private boolean armsLockedForward = false;

    /** 时间（秒，用于周期性动画） */
    private float time = 0f;

    // ========== Getters / Setters ==========

    public float getBodyLean() {
        return bodyLean;
    }

    public void setBodyLean(float bodyLean) {
        this.bodyLean = bodyLean;
    }

    public float getArmSwing() {
        return armSwing;
    }

    public void setArmSwing(float armSwing) {
        this.armSwing = armSwing;
    }

    public float getStepFrequency() {
        return stepFrequency;
    }

    public void setStepFrequency(float stepFrequency) {
        this.stepFrequency = stepFrequency;
    }

    public float getBodyBob() {
        return bodyBob;
    }

    public void setBodyBob(float bodyBob) {
        this.bodyBob = bodyBob;
    }

    public float getBodyTwist() {
        return bodyTwist;
    }

    public void setBodyTwist(float bodyTwist) {
        this.bodyTwist = bodyTwist;
    }

    public float getLegSwing() {
        return legSwing;
    }

    public void setLegSwing(float legSwing) {
        this.legSwing = legSwing;
    }

    public float getHeadPitch() {
        return headPitch;
    }

    public void setHeadPitch(float headPitch) {
        this.headPitch = headPitch;
    }

    public float getHeadYaw() {
        return headYaw;
    }

    public void setHeadYaw(float headYaw) {
        this.headYaw = headYaw;
    }

    public boolean isHeadDown() {
        return headDown;
    }

    public void setHeadDown(boolean headDown) {
        this.headDown = headDown;
    }

    public boolean isArmsLockedForward() {
        return armsLockedForward;
    }

    public void lockArmsForward() {
        this.armsLockedForward = true;
    }

    public void unlockArms() {
        this.armsLockedForward = false;
    }

    public float getTime() {
        return time;
    }

    public void setTime(float time) {
        this.time = time;
    }

    /**
     * 更新时间（由 Renderer 每帧调用）。
     *
     * @param deltaTime 时间增量（秒）
     */
    public void update(float deltaTime) {
        this.time += deltaTime;
    }

    /**
     * 重置所有姿态参数到默认值。
     */
    public void resetPose() {
        bodyLean = 0f;
        armSwing = 0f;
        legSwing = 0f;
        stepFrequency = 0f;
        bodyBob = 0f;
        bodyTwist = 0f;
        headPitch = 0f;
        headYaw = 0f;
        headDown = false;
        armsLockedForward = false;
    }
}
