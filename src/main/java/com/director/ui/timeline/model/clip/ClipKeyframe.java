package com.director.ui.timeline.model.clip;

import com.director.ui.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.TimelineSelectable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * ClipKeyframe v1.0
 * 
 * <p>这是时间片段（TimelineClip）内部的关键帧数据模型。
 * ClipKeyframe 必须是一个独立对象，否则无法支持：
 * <ul>
 *   <li>选中系统（SelectionManager）</li>
 *   <li>属性面板显示</li>
 *   <li>Undo/Redo（Command 模式）</li>
 *   <li>曲线插值类型</li>
 *   <li>右键菜单（插值选择 / 删除关键帧）</li>
 * </ul>
 * 
 * <p>关键帧时间使用全局时间（相对于 Timeline 的开始时间），
 * 但通常显示时会被转换为相对于 Clip 的本地时间。
 * 
 * @author Director Team
 * @version 1.0
 */
public class ClipKeyframe implements TimelineSelectable, Comparable<ClipKeyframe> {
    
    private final UUID id;
    private float time; // 全局时间（相对于 Timeline 的开始）
    private TimelineInterpolation interpolation = TimelineInterpolation.LINEAR;
    
    /**
     * 创建关键帧
     * 
     * @param time 关键帧时间（全局时间，秒）
     */
    public ClipKeyframe(float time) {
        this.id = UUID.randomUUID();
        this.time = time;
    }
    
    /**
     * 创建关键帧（带插值类型）
     * 
     * @param time 关键帧时间（全局时间，秒）
     * @param interpolation 插值类型
     */
    public ClipKeyframe(float time, TimelineInterpolation interpolation) {
        this.id = UUID.randomUUID();
        this.time = time;
        this.interpolation = Objects.requireNonNullElse(interpolation, TimelineInterpolation.LINEAR);
    }
    
    // ========== TimelineSelectable 实现 ==========
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return "Keyframe @ " + String.format("%.3fs", time);
    }
    
    @Override
    public float getStartTime() {
        return time;
    }
    
    @Override
    public float getEndTime() {
        return time; // 关键帧是瞬时点
    }
    
    @Override
    public String getType() {
        return "ClipKeyframe";
    }
    
    // ========== Getters and Setters ==========
    
    /**
     * 获取关键帧时间（全局时间）
     * 
     * @return 时间（秒）
     */
    public float getTime() {
        return time;
    }
    
    /**
     * 设置关键帧时间（全局时间）
     * 
     * @param time 时间（秒）
     */
    public void setTime(float time) {
        this.time = time;
    }
    
    /**
     * 获取插值类型
     * 
     * @return 插值类型
     */
    public TimelineInterpolation getInterpolation() {
        return interpolation;
    }
    
    /**
     * 设置插值类型
     * 
     * @param interpolation 插值类型，不能为 null
     */
    public void setInterpolation(TimelineInterpolation interpolation) {
        this.interpolation = Objects.requireNonNull(interpolation, "Interpolation cannot be null");
    }
    
    // ========== Comparable 实现（用于 TreeSet 排序）==========
    
    @Override
    public int compareTo(@NotNull ClipKeyframe other) {
        int timeCompare = Float.compare(this.time, other.time);
        // 如果时间相同，使用 ID 作为次要排序键，确保 TreeSet 中不会出现重复
        if (timeCompare == 0) {
            return this.id.compareTo(other.id);
        }
        return timeCompare;
    }
    
    // ========== equals & hashCode ==========
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClipKeyframe that = (ClipKeyframe) o;
        return Objects.equals(id, that.id); // 基于 ID 判断相等性
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("ClipKeyframe{id=%s, time=%.3fs, interpolation=%s}", 
                id.toString().substring(0, 8), time, interpolation);
    }
    
    /**
     * 创建关键帧的深拷贝
     * 
     * @return 新的关键帧实例，具有相同的属性但不同的 ID
     */
    public ClipKeyframe copy() {
        return new ClipKeyframe(time, interpolation);
    }
}

