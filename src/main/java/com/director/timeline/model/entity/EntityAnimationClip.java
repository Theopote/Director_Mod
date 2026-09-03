package com.director.timeline.model.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EntityAnimationClip v1.0
 *
 * <p>Animation：动作播放片段。
 *
 * <p>控制"演什么动作"，而不是"怎么动"。
 *
 * <p>v1.1 新增：AnimationEvent 支持
 * <ul>
 *   <li>✅ 在动画播放到某个归一化时间点时，触发 Action</li>
 *   <li>✅ 支持多个事件（如投掷动画在 0.62 位置触发 THROW_PROP）</li>
 *   <li>✅ 不影响已有 AnimationClip 行为（events 是可选的）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.1
 */
public class EntityAnimationClip extends EntityClip {

    public enum AnimationSource {
        BUILT_IN,
        VANILLA,
        /** 仅为旧存档兼容保留；当前版本没有 GeckoLib 运行时。 */
        @Deprecated GECKOLIB,
        CUSTOM
    }

    private AnimationSource source = AnimationSource.BUILT_IN;

    /** 动画 id：例如 "walk" / "attack" / "mymod:wave" */
    private String animationId = "idle";

    private boolean loop = true;
    private float speed = 1.0f;

    /** 动画事件列表（可选，不影响已有行为） */
    private final List<AnimationEvent> events = new ArrayList<>();

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
        this.source = source != null ? source : AnimationSource.BUILT_IN;
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

    // ========== Animation Events ==========

    /**
     * 获取动画事件列表（只读）
     *
     * @return 动画事件列表
     */
    public List<AnimationEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * 添加动画事件
     *
     * @param event 动画事件
     */
    public void addEvent(AnimationEvent event) {
        if (event != null) {
            events.add(event);
            // 按归一化时间排序
            events.sort((a, b) -> Float.compare(a.getNormalizedTime(), b.getNormalizedTime()));
        }
    }

    /**
     * 移除动画事件
     *
     * @param event 动画事件
     * @return 如果成功移除则返回 true
     */
    public boolean removeEvent(AnimationEvent event) {
        return events.remove(event);
    }

    /**
     * 清空所有动画事件
     */
    public void clearEvents() {
        events.clear();
    }

    /**
     * 检查是否有动画事件
     *
     * @return 如果有事件则返回 true
     */
    public boolean hasEvents() {
        return !events.isEmpty();
    }
}

