package com.chronoblocks.ui.timeline;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间线轨道
 * 包含多个 TimelineClip
 * 
 * @author ChronoBlocks Team
 * @version 6.0
 */
public class TimelineTrack {
    private String name;
    private final List<TimelineClip> clips = new ArrayList<>();
    private boolean locked = false;
    private boolean visible = true;
    private int zOrder = 0;  // Z轴排序
    
    public TimelineTrack(String name) {
        this.name = name;
    }
    
    // ========== Clip 管理 ==========
    
    public void addClip(TimelineClip clip) {
        clips.add(clip);
    }
    
    public void removeClip(TimelineClip clip) {
        clips.remove(clip);
    }
    
    public void removeClipAt(int index) {
        if (index >= 0 && index < clips.size()) {
            clips.remove(index);
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
}

