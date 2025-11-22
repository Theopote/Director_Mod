package com.chronoblocks.ui.timeline.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间线轨道（专业级多类型支持）
 * 包含多个 TimelineClip，支持不同的轨道类型
 * 
 * @author ChronoBlocks Team
 * @version 7.0
 */
public class TimelineTrack {
    private String name;
    private TrackType type;  // 轨道类型
    private final List<TimelineClip> clips = new ArrayList<>();
    private boolean locked = false;
    private boolean visible = true;
    private int zOrder = 0;  // Z轴排序
    private boolean muted = false;  // 静音（对音频轨道有效）
    private boolean solo = false;   // 独奏（只播放此轨道）
    private boolean collapsed = false;  // 折叠状态（用于 UI 显示）
    private String boundContainerName;
    private String boundContainerId;
    
    /**
     * 构造函数（默认为容器轨道）
     */
    public TimelineTrack(String name) {
        this(name, TrackType.CONTAINER);
    }
    
    /**
     * 构造函数（指定轨道类型）
     */
    public TimelineTrack(String name, TrackType type) {
        this.name = name;
        this.type = type;
    }
    
    // ========== Clip 管理 ==========
    
    /**
     * 添加片段到轨道，并自动设置父引用
     */
    public void addClip(TimelineClip clip) {
        if (clip != null && !this.clips.contains(clip)) {
            this.clips.add(clip);
            clip.setParentTrack(this); // 设置父引用，实现 O(1) 反向查找
            // 可选：排序（按开始时间）
            clips.sort((a, b) -> Float.compare(a.getStartTime(), b.getStartTime()));
        }
    }
    
    /**
     * 从轨道移除片段，并清除父引用
     */
    public void removeClip(TimelineClip clip) {
        if (clip != null && this.clips.remove(clip)) {
            clip.setParentTrack(null); // 清除父引用
        }
    }

    // ========== 语义方法（ChronoBlocks 风格：时间线语义 API）==========

    /**
     * 查找指定时间点上的片段（语义方法）
     * 
     * <p>返回该时间点所在的片段，如果该时间点没有片段则返回 null。
     * 用于命中检测和交互逻辑。
     * 
     * @param time 时间点（秒）
     * @return 该时间点上的片段，如果没有则返回 null
     */
    public TimelineClip findClipAtTime(float time) {
        for (TimelineClip clip : clips) {
            if (time >= clip.getStartTime() && time <= clip.getEndTime()) {
                return clip;
            }
        }
        return null;
    }
    
    /**
     * 根据索引移除片段，并清除父引用
     */
    public void removeClipAt(int index) {
        if (index >= 0 && index < clips.size()) {
            TimelineClip clip = clips.remove(index);
            if (clip != null) {
                clip.setParentTrack(null); // 清除父引用
            }
        }
    }
    
    public int getClipIndex(TimelineClip clip) {
        return clips.indexOf(clip);
    }
    
    /**
     * 查找指定时间范围内的片段
     */
    public List<TimelineClip> getClipsInRange(float startTime, float endTime) {
        List<TimelineClip> result = new ArrayList<>();
        for (TimelineClip clip : clips) {
            if (clip.getEndTime() >= startTime && clip.getStartTime() <= endTime) {
                result.add(clip);
            }
        }
        return result;
    }
    
    /**
     * 检查时间范围是否有重叠的片段
     */
    public boolean hasOverlap(float startTime, float endTime, TimelineClip exclude) {
        for (TimelineClip clip : clips) {
            if (clip == exclude) continue;
            if (clip.getEndTime() > startTime && clip.getStartTime() < endTime) {
                return true;
            }
        }
        return false;
    }
    
    // ========== Getters & Setters ==========
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public List<TimelineClip> getClips() {
        return clips;
    }
    
    public boolean isLocked() {
        return locked;
    }
    
    public void setLocked(boolean locked) {
        this.locked = locked;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public int getZOrder() {
        return zOrder;
    }
    
    public void setZOrder(int zOrder) {
        this.zOrder = zOrder;
    }
    
    public TrackType getType() {
        return type;
    }
    
    public void setType(TrackType type) {
        this.type = type;
    }
    
    public boolean isMuted() {
        return muted;
    }
    
    public void setMuted(boolean muted) {
        this.muted = muted;
    }
    
    public boolean isSolo() {
        return solo;
    }
    
    public void setSolo(boolean solo) {
        this.solo = solo;
    }
    
    public boolean isCollapsed() {
        return collapsed;
    }
    
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public String getBoundContainerName() {
        return boundContainerName;
    }

    public void setBoundContainerName(String boundContainerName) {
        this.boundContainerName = (boundContainerName == null || boundContainerName.isBlank())
            ? null
            : boundContainerName.trim();
    }

    public String getBoundContainerId() {
        return boundContainerId;
    }

    public void setBoundContainerId(String boundContainerId) {
        this.boundContainerId = (boundContainerId == null || boundContainerId.isBlank())
            ? null
            : boundContainerId.trim();
    }
}