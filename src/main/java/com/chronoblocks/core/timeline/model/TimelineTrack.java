package com.chronoblocks.core.timeline.model;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.timeline.keyframe.Keyframe;

import java.awt.Color;
import java.util.*;

/**
 * 时间线轨道
 * 表示时间轴上的一个轨道，可以绑定到容器或其他对象
 */
public class TimelineTrack {
    public enum BlendMode { Normal, Add, Multiply, Screen }

    private UUID id = UUID.randomUUID();
    private String name;
    private Color color = new Color(64, 160, 255);
    private double weight = 1.0;
    private BlendMode blendMode = BlendMode.Normal;

    private Object boundTarget; // Container 等
    private final List<Keyframe> keyframes = new ArrayList<>();

    public TimelineTrack(String name) {
        this.name = name;
    }

    /**
     * 设置ID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * 获取ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * 设置名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置绑定目标
     */
    public void setBoundTarget(Object target) {
        this.boundTarget = target;
    }

    /**
     * 获取绑定目标
     */
    public Object getBoundTarget() {
        return boundTarget;
    }

    /**
     * 设置颜色
     */
    public void setColor(Color color) {
        this.color = color;
    }

    /**
     * 获取颜色
     */
    public Color getColor() {
        return color;
    }

    /**
     * 设置混合模式
     */
    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode;
    }

    /**
     * 获取混合模式
     */
    public BlendMode getBlendMode() {
        return blendMode;
    }

    /**
     * 设置权重
     */
    public void setWeight(double weight) {
        this.weight = weight;
    }

    /**
     * 获取权重
     */
    public double getWeight() {
        return weight;
    }

    /**
     * 添加关键帧
     */
    public void putKeyframe(Keyframe keyframe) {
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingDouble(Keyframe::getTime));
    }

    /**
     * 获取所有关键帧
     */
    public List<Keyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    /**
     * 移除关键帧
     */
    public boolean removeKeyframe(Keyframe keyframe) {
        return keyframes.remove(keyframe);
    }

    /**
     * 清空关键帧
     */
    public void clearKeyframes() {
        keyframes.clear();
    }

    /**
     * 应用默认缓动
     */
    public void applyDefaultEasing(Enum<?> easing) {
        // 可在插值器侧读取默认缓动
        // 这里暂时为空实现
    }

    /**
     * 获取指定类型的关键帧
     */
    public List<Keyframe> getKeyframesByType(Keyframe.Type type) {
        return keyframes.stream()
                .filter(kf -> kf.getType() == type)
                .toList();
    }

    /**
     * 获取指定时间范围内的关键帧
     */
    public List<Keyframe> getKeyframesInRange(float startTime, float endTime) {
        return keyframes.stream()
                .filter(kf -> kf.getTime() >= startTime && kf.getTime() <= endTime)
                .toList();
    }

    @Override
    public String toString() {
        return String.format("TimelineTrack{name='%s', id=%s, keyframes=%d}", 
                name, id, keyframes.size());
    }
}
