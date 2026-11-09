package com.director.timeline.model.entity;

/**
 * EntityStateClip v1.0
 *
 * <p>State：持续状态（潜行/冲刺/发光/无敌等）。
 *
 * <p>重叠片段按字段覆盖。未设置的字段继承前一个覆盖值。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityStateClip extends EntityClip {

    private Boolean sneaking;
    private Boolean sprinting;
    private Boolean glowing;
    private Boolean invulnerable;

    /** 透明度/可见性（0-1） */
    private Float visibility;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public EntityStateClip(String name, float startTime, float endTime) {
        this(java.util.UUID.randomUUID(), name, startTime, endTime);
    }

    public EntityStateClip(java.util.UUID id, String name, float startTime, float endTime) {
        super(id, name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.STATE;
    }

    // ========== Getters and Setters ==========

    public boolean isSneaking() {
        return Boolean.TRUE.equals(sneaking);
    }

    public Boolean getSneakingOverride() { return sneaking; }

    public void setSneaking(Boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isSprinting() {
        return Boolean.TRUE.equals(sprinting);
    }

    public Boolean getSprintingOverride() { return sprinting; }

    public void setSprinting(Boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean isGlowing() {
        return Boolean.TRUE.equals(glowing);
    }

    public Boolean getGlowingOverride() { return glowing; }

    public void setGlowing(Boolean glowing) {
        this.glowing = glowing;
    }

    public boolean isInvulnerable() {
        return Boolean.TRUE.equals(invulnerable);
    }

    public Boolean getInvulnerableOverride() { return invulnerable; }

    public void setInvulnerable(Boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public float getVisibility() {
        return visibility == null ? 1f : visibility;
    }

    public void setVisibility(float visibility) {
        setVisibilityOverride(visibility);
    }

    public Float getVisibilityOverride() { return visibility; }

    public void setVisibilityOverride(Float visibility) {
        this.visibility = visibility == null ? null : Math.clamp(visibility, 0f, 1f);
    }
}

