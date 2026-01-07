package com.chronoblocks.ui.timeline.model.entity;

import com.chronoblocks.ui.timeline.playback.entity.EntityActionEvent;

import java.util.Objects;

/**
 * AnimationEvent v1.0
 *
 * <p>动画事件：在动画播放到某个归一化时间点时，触发一个 Action
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 绑定到动画的归一化时间（0.0 ~ 1.0）</li>
 *   <li>✅ 触发 Action（如 THROW_PROP）</li>
 *   <li>✅ 可序列化，可持久化</li>
 * </ul>
 *
 * <p>使用示例：
 * <ul>
 *   <li>投掷动画在 0.62 位置触发 THROW_PROP</li>
 *   <li>攻击动画在 0.5 位置触发 ATTACK Action</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class AnimationEvent {

    /** 归一化时间（0.0 ~ 1.0） */
    private float normalizedTime;

    /** 触发的 Action */
    private EntityActionEvent action;

    /**
     * 构造函数
     *
     * @param normalizedTime 归一化时间（0.0 ~ 1.0）
     * @param action 触发的 Action
     */
    public AnimationEvent(float normalizedTime, EntityActionEvent action) {
        this.normalizedTime = Math.max(0.0f, Math.min(1.0f, normalizedTime));
        this.action = Objects.requireNonNull(action, "action cannot be null");
    }

    // ========== Getters and Setters ==========

    public float getNormalizedTime() {
        return normalizedTime;
    }

    public void setNormalizedTime(float normalizedTime) {
        this.normalizedTime = Math.max(0.0f, Math.min(1.0f, normalizedTime));
    }

    public EntityActionEvent getAction() {
        return action;
    }

    public void setAction(EntityActionEvent action) {
        this.action = Objects.requireNonNull(action, "action cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnimationEvent that = (AnimationEvent) o;
        return Float.compare(that.normalizedTime, normalizedTime) == 0
                && Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedTime, action);
    }

    @Override
    public String toString() {
        return String.format("AnimationEvent{normalizedTime=%.2f, action=%s}",
                normalizedTime, action);
    }
}

