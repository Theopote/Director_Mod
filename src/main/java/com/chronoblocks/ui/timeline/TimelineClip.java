package com.chronoblocks.ui.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 时间线片段（Clip）
 * 表示轨道上的一个可编辑区域，包含关键帧
 * 
 * @author ChronoBlocks Team
 * @version 6.0
 */
public class TimelineClip {
    private String label;
    private float startTime;
    private float endTime;
    private int color;
    private final List<Float> keyframes = new ArrayList<>();
    
    // ========== 额外属性 ==========
    private boolean muted = false;
    private float opacity = 1.0f;
    
    public TimelineClip(String label, float startTime, float endTime, int color) {
        this.label = label;
        this.startTime = startTime;
        this.endTime = endTime;
        this.color = color;
    }
    
    // ========== 时间操作 ==========
    
    public float getLength() {
        return endTime - startTime;
    }
    
    public void setLength(float length) {
        this.endTime = startTime + Math.max(0.1f, length);
    }
    
    public void move(float delta) {
        this.startTime += delta;
        this.endTime += delta;
    }
    
    public boolean containsTime(float time) {
        return time >= startTime && time <= endTime;
    }
    
    // ========== 关键帧管理 ==========
    
    public void addKeyframe(float time) {
        if (!keyframes.contains(time)) {
            keyframes.add(time);
            Collections.sort(keyframes);
        }
    }
    
    public void removeKeyframe(float time) {
        keyframes.remove(time);
    }
    
    public void removeKeyframeAt(int index) {
        if (index >= 0 && index < keyframes.size()) {
            keyframes.remove(index);
        }
    }
    
    public boolean hasKeyframeAt(float time, float tolerance) {
        for (Float kf : keyframes) {
            if (Math.abs(kf - time) < tolerance) {
                return true;
            }
        }
        return false;
    }
    
    public Float getKeyframeAt(float time, float tolerance) {
        for (Float kf : keyframes) {
            if (Math.abs(kf - time) < tolerance) {
                return kf;
            }
        }
        return null;
    }
    
    public void clearKeyframes() {
        keyframes.clear();
    }
    
    /**
     * 获取指定时间范围内的关键帧
     */
    public List<Float> getKeyframesInRange(float start, float end) {
        List<Float> result = new ArrayList<>();
        for (Float kf : keyframes) {
            if (kf >= start && kf <= end) {
                result.add(kf);
            }
        }
        return result;
    }
    
    // ========== 克隆 ==========
    
    public TimelineClip clone() {
        TimelineClip copy = new TimelineClip(label + " (副本)", startTime, endTime, color);
        copy.keyframes.addAll(this.keyframes);
        copy.muted = this.muted;
        copy.opacity = this.opacity;
        return copy;
    }
    
    // ========== Getters & Setters ==========
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public float getStartTime() {
        return startTime;
    }
    
    public void setStartTime(float startTime) {
        this.startTime = startTime;
    }
    
    public float getEndTime() {
        return endTime;
    }
    
    public void setEndTime(float endTime) {
        this.endTime = endTime;
    }
    
    public int getColor() {
        return color;
    }
    
    public void setColor(int color) {
        this.color = color;
    }
    
    public List<Float> getKeyframes() {
        return keyframes;
    }
    
    public boolean isMuted() {
        return muted;
    }
    
    public void setMuted(boolean muted) {
        this.muted = muted;
    }
    
    public float getOpacity() {
        return opacity;
    }
    
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0f, Math.min(1f, opacity));
    }
}

