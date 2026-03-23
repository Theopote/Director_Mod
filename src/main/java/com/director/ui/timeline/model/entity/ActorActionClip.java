package com.director.ui.timeline.model.entity;

import com.director.actor.action.ActorActionType;

/**
 * ActorActionClip v1.0
 *
 * <p>表示演员在一段时间内的"动作语义状态"
 * 例如：RUN / DANCE / CARRY
 *
 * <p>核心特点：
 * <ul>
 *   <li>❌ 不直接控制动画</li>
 *   <li>❌ 不操作世界</li>
 *   <li>✅ 只影响 Runtime 表现策略</li>
 *   <li>✅ 必须是可插值 / 可过渡的</li>
 * </ul>
 *
 * <p>与 EntityActionClip 的区别：
 * <ul>
 *   <li>EntityActionClip：瞬时（Throw / Place）</li>
 *   <li>ActorActionClip：持续（Run / Dance）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>一个 Actor 在同一时刻只能有一个主 Action</li>
 *   <li>RUN 和 DANCE 不能同时存在</li>
 *   <li>优先级 / 覆盖规则必须明确</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorActionClip extends EntityClip {

    private ActorActionType actionType = ActorActionType.IDLE;

    /**
     * 动作强度 / 风格
     * 0.0 ~ 1.0
     * 例如：
     * - RUN 的快慢
     * - DANCE 的幅度
     */
    private float intensity = 1.0f;

    /**
     * 是否循环（如 DANCE / IDLE）
     */
    private boolean loop = true;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public ActorActionClip(
            String name,
            float startTime,
            float endTime
    ) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.ACTOR_ACTION;
    }

    @Override
    public TimelineClipVisualType getVisualType() {
        return TimelineClipVisualType.BLOCK;
    }

    // =================================================
    // Getters / Setters
    // =================================================

    public ActorActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActorActionType actionType) {
        this.actionType = actionType != null
                ? actionType
                : ActorActionType.IDLE;
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = clamp(intensity, 0f, 1f);
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
