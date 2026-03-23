package com.director.ui.timeline.model.entity;

/**
 * EntityStateClip v1.0
 *
 * <p>State：持续状态（潜行/冲刺/发光/无敌等）。
 *
 * <p>可叠加：比如一个长 StateClip 维持"发光"。
 *
 * <p>特点：
 * <ul>
 *   <li>适合关键帧插值</li>
 *   <li>支持淡入淡出</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityStateClip extends EntityClip {

    private boolean sneaking = false;
    private boolean sprinting = false;
    private boolean glowing = false;
    private boolean invulnerable = false;

    /** 透明度/可见性（0-1） */
    private float visibility = 1.0f;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public EntityStateClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.STATE;
    }

    // ========== Getters and Setters ==========

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public boolean isInvulnerable() {
        return invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public float getVisibility() {
        return visibility;
    }

    public void setVisibility(float visibility) {
        this.visibility = Math.max(0f, Math.min(1f, visibility));
    }
}

