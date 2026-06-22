package com.director.ui.timeline.model.entity;

import com.director.timeline.model.TimelineSelectable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * EntityClip v1.0
 *
 * <p>演员轨道上的片段（表演段落）。
 *
 * <p>设计特点：
 * <ul>
 *   <li>实现 {@link TimelineSelectable}，可以在时间线上选择和编辑</li>
 *   <li>统一的 start/end 时间</li>
 *   <li>统一的 muted/weight 等（将来做混合/叠加时很有用）</li>
 *   <li>可比较排序（按时间排序）</li>
 * </ul>
 *
 * <p>与 {@link com.director.timeline.model.TimelineClip} 的关系：
 * <ul>
 *   <li>EntityClip 是独立的实体片段系统，不继承 TimelineClip</li>
 *   <li>可以与 TimelineClip 并存，用于不同的使用场景</li>
 *   <li>EntityClip 更适合"导演段落式表演"</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public abstract class EntityClip implements TimelineSelectable, Comparable<EntityClip> {

    private final UUID id;
    private String name;

    private float startTime;
    private float endTime;

    private boolean muted = false;
    private float weight = 1.0f; // 预留：叠加/混合权重
    private transient EntityTimelineClip timelineClip;

    /** 关键帧列表（时间相对于 Clip 开始时间） */
    private final List<EntityClipKeyframe> keyframes = new ArrayList<>();

    /**
     * 构造函数
     *
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    protected EntityClip(String name, float startTime, float endTime) {
        this(UUID.randomUUID(), name, startTime, endTime);
    }

    /**
     * 完整构造函数
     *
     * @param id 唯一ID
     * @param name 片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    protected EntityClip(UUID id, String name, float startTime, float endTime) {
        if (startTime > endTime) {
            throw new IllegalArgumentException("startTime > endTime");
        }
        this.id = Objects.requireNonNull(id, "id");
        this.name = (name == null || name.isBlank()) ? getType() : name;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * 获取片段类型（由子类实现）
     *
     * @return 片段类型
     */
    public abstract EntityClipType getClipType();

    /**
     * 获取视觉类型（用于 Timeline 渲染区分）
     * <p>默认返回 CURVE（连续变化），子类可以覆盖以返回 BLOCK（状态区间）或 EVENT（瞬时事件）
     *
     * @return 视觉类型
     */
    public TimelineClipVisualType getVisualType() {
        return TimelineClipVisualType.CURVE;
    }

    // ========== TimelineSelectable 实现 ==========

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getStartTime() {
        return startTime;
    }

    @Override
    public float getEndTime() {
        return endTime;
    }

    @Override
    public String getType() {
        return "EntityClip:" + getClipType().name();
    }

    // ========== 基础属性 ==========

    /**
     * 设置片段名称
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? getType() : name;
        syncTimelineClip();
    }

    /**
     * 设置时间范围
     *
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public void setTimeRange(float startTime, float endTime) {
        if (startTime > endTime) {
            throw new IllegalArgumentException("startTime > endTime");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        syncTimelineClip();
    }

    /**
     * 检查是否静音（不执行）
     *
     * @return 如果静音则返回 true
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * 设置是否静音
     *
     * @param muted 是否静音
     */
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    /**
     * 获取权重（用于叠加/混合）
     *
     * @return 权重 [0-1]
     */
    public float getWeight() {
        return weight;
    }

    /**
     * 设置权重
     *
     * @param weight 权重 [0-1]
     */
    public void setWeight(float weight) {
        this.weight = Math.max(0.0f, Math.min(1.0f, weight));
    }

    /**
     * 获取片段长度
     *
     * @return 长度（秒）
     */
    public float getLength() {
        return endTime - startTime;
    }

    /**
     * 片段整体平移时间
     *
     * @param delta 时间偏移量（秒）
     */
    public void offsetTime(float delta) {
        this.startTime += delta;
        this.endTime += delta;
        syncTimelineClip();
    }

    public EntityTimelineClip getTimelineClip() {
        return timelineClip;
    }

    public void setTimelineClip(EntityTimelineClip timelineClip) {
        this.timelineClip = timelineClip;
    }

    private void syncTimelineClip() {
        if (timelineClip != null) {
            timelineClip.syncFromEntityClip(this);
        }
    }

    // ========== 关键帧管理 ==========

    /**
     * 添加关键帧
     * <p>关键帧时间应该是全局时间，会自动验证是否在 Clip 范围内
     *
     * @param keyframe 关键帧对象（时间应为全局时间）
     */
    public void addKeyframe(EntityClipKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }
        
        float globalTime = keyframe.getTime();
        
        // 确保关键帧在 Clip 时间范围内
        if (globalTime < this.startTime || globalTime > this.endTime) {
            return;
        }
        
        // 检查是否已存在相同时间的关键帧（容差范围内）
        float epsilon = 0.0001f;
        EntityClipKeyframe existing = findKeyframeAt(globalTime, epsilon);
        if (existing != null) {
            // 如果已存在，移除旧的
            keyframes.remove(existing);
        }
        
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparing(EntityClipKeyframe::getTime));
        
        // 同步到 TimelineClip
        syncKeyframesToTimelineClip();
    }

    /**
     * 移除关键帧
     *
     * @param keyframeId 关键帧 ID
     * @return 如果成功移除返回 true
     */
    public boolean removeKeyframe(UUID keyframeId) {
        boolean removed = keyframes.removeIf(kf -> kf.getId().equals(keyframeId));
        if (removed) {
            syncKeyframesToTimelineClip();
        }
        return removed;
    }

    /**
     * 获取所有关键帧（只读）
     *
     * @return 关键帧列表（按时间排序）
     */
    public List<EntityClipKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    /**
     * 查找指定时间的关键帧
     * <p>时间参数是全局时间
     *
     * @param globalTime 全局时间（秒）
     * @param epsilon 允许的误差范围（秒）
     * @return 找到的关键帧，如果不存在则返回 null
     */
    @Nullable
    public EntityClipKeyframe findKeyframeAt(float globalTime, float epsilon) {
        for (EntityClipKeyframe kf : keyframes) {
            if (Math.abs(kf.getTime() - globalTime) <= epsilon) {
                return kf;
            }
        }
        return null;
    }

    /**
     * 清除所有关键帧
     */
    public void clearKeyframes() {
        keyframes.clear();
        syncKeyframesToTimelineClip();
    }

    /**
     * 同步关键帧到 TimelineClip
     */
    private void syncKeyframesToTimelineClip() {
        if (timelineClip != null) {
            timelineClip.syncKeyframesFromEntityClip(this);
        }
    }

    // ========== Comparable 实现 ==========

    @Override
    public int compareTo(@NotNull EntityClip other) {
        int c = Float.compare(this.startTime, other.startTime);
        if (c != 0) {
            return c;
        }
        // 时间相同时，用 ID 作为次排序键，保证 TreeSet 等结构中不重复
        return this.id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityClip that = (EntityClip) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", clipType=" + getClipType() +
                '}';
    }
}

