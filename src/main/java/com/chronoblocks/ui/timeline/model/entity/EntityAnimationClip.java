package com.chronoblocks.ui.timeline.model.entity;

/**
 * EntityAnimationClip v1.0
 *
 * <p>Animation：动作播放片段。
 *
 * <p>控制"演什么动作"，而不是"怎么动"。
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class EntityAnimationClip extends EntityClip {

    public enum AnimationSource {
        VANILLA,
        GECKOLIB,
        CUSTOM
    }

    private AnimationSource source = AnimationSource.VANILLA;

    /** 动画 id：例如 "walk" / "attack" / "mymod:wave" */
    private String animationId = "idle";

    private boolean loop = true;
    private float speed = 1.0f;

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public EntityAnimationClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.ANIMATION;
    }

    // ========== Getters and Setters ==========

    public AnimationSource getSource() {
        return source;
    }

    public void setSource(AnimationSource source) {
        this.source = source != null ? source : AnimationSource.VANILLA;
    }

    public String getAnimationId() {
        return animationId;
    }

    public void setAnimationId(String animationId) {
        this.animationId = (animationId == null || animationId.isBlank()) ? "idle" : animationId;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.0f, Math.min(4.0f, speed));
    }
}

