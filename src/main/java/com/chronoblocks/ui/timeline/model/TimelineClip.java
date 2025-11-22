package com.chronoblocks.ui.timeline.model;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.container.ContainerAnimationPreset;
import com.chronoblocks.ui.util.UIUtils;

import java.util.*;
import java.util.UUID;

/**
 * 时间线片段（Clip）v7.4 - 终极优化版
 * <p>
 * 表示轨道上的一个可编辑区域，包含关键帧。
 * 此版本针对性能、健壮性和封装性进行了优化。
 * <ul>
 *   <li>使用 {@link TreeSet} 管理关键帧，实现 O(log n) 的高效添加和查找。</li>
 *   <li>在构造函数和 Setter 中添加了验证，防止无效的时间状态 (startTime > endTime)。</li>
 *   <li>通过返回不可修改的集合视图，增强了封装性。</li>
 *   <li>自动裁剪越界关键帧，确保数据一致性。</li>
 *   <li>提供拷贝构造函数，符合 Java 惯例。</li>
 *   <li>使用 Stream API 实现现代化代码风格。</li>
 * </ul>
 * </p>
 *
 * @author ChronoBlocks Team
 * @version 7.4
 */
public class TimelineClip {
    private static final float MINIMUM_LENGTH = 0.05f; // 定义最小片段长度常量

    private String label;
    private float startTime;
    private float endTime;
    private int color;
    // 使用 TreeSet 替代 ArrayList 以获得高效的排序和查找
    private final NavigableSet<Float> keyframes = new TreeSet<>();

    // ========== 额外属性 ==========
    private boolean muted = false;
    private float opacity = 1.0f;
    private ClipAnimationConfig animationConfig = ClipAnimationConfig.defaultConfig();
    private UUID boundContainerId = null;
    private String boundContainerName = null;
    
    // ========== 父引用（用于 O(1) 查找）==========
    private TimelineTrack parentTrack = null;

    public TimelineClip(String label, float startTime, float endTime, int color) {
        if (startTime > endTime) {
            throw new IllegalArgumentException("Start time cannot be after end time.");
        }
        this.label = Objects.requireNonNull(label, "Label cannot be null");
        this.startTime = startTime;
        this.endTime = Math.max(startTime + MINIMUM_LENGTH, endTime);
        this.color = color;
    }

    /**
     * 拷贝构造函数。创建一个与另一个 TimelineClip 实例完全相同的新实例。
     * 这是 Java 中标准的对象复制模式。
     * @param other 要复制的源对象
     */
    public TimelineClip(TimelineClip other) {
        Objects.requireNonNull(other, "Source clip cannot be null");
        this.label = other.label;
        this.startTime = other.startTime;
        this.endTime = other.endTime;
        this.color = other.color;
        this.keyframes.addAll(other.keyframes);
        this.muted = other.muted;
        this.opacity = other.opacity;
        this.animationConfig = other.animationConfig != null ? other.animationConfig.copy() : ClipAnimationConfig.defaultConfig();
        this.boundContainerId = other.boundContainerId;
        this.boundContainerName = other.boundContainerName;
    }

    // ========== 时间操作 ==========

    public float getLength() {
        return endTime - startTime;
    }

    public void setLength(float length) {
        this.endTime = this.startTime + Math.max(MINIMUM_LENGTH, length);
        pruneOrphanedKeyframes(); // 确保关键帧在范围内
    }

    /**
     * 设置片段的开始和结束时间，并自动验证。
     * @param newStart 新的开始时间
     * @param newEnd   新的结束时间
     */
    public void setTimeRange(float newStart, float newEnd) {
        if (newStart > newEnd) {
            throw new IllegalArgumentException("Start time cannot be after end time.");
        }
        this.startTime = newStart;
        this.endTime = newEnd;
        pruneOrphanedKeyframes(); // 确保关键帧在范围内
    }

    /**
     * 移动整个片段（包括其内部的关键帧）
     * @param delta 时间偏移量
     */
    public void offsetTime(float delta) {
        this.startTime += delta;
        this.endTime += delta;

        // 使用 Stream API 转换关键帧，代码更简洁且具表达力
        NavigableSet<Float> movedKeyframes = this.keyframes.stream()
                .map(kf -> kf + delta)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        
        this.keyframes.clear();
        this.keyframes.addAll(movedKeyframes);
    }

    public boolean containsTime(float time) {
        return time >= startTime && time <= endTime;
    }

    // ========== 关键帧管理 (基于 TreeSet 优化) ==========

    /**
     * 添加一个关键帧。操作是 O(log n) 复杂度。
     * @param time 关键帧的时间点
     */
    public void addKeyframe(float time) {
        // 确保关键帧在片段范围内
        if (containsTime(time)) {
            keyframes.add(time);
        }
    }
    
    /**
     * 更新指定索引的关键帧时间（用于拖动关键帧）
     * 
     * <p>此方法会移除旧的关键帧并添加新的关键帧，确保关键帧在片段范围内。
     * 由于 keyframes 是 TreeSet，我们需要先转换为列表来获取索引对应的值。
     * 
     * @param index 关键帧索引（基于排序后的列表）
     * @param newTime 新的时间点
     * @return 如果成功更新返回 true
     */
    public boolean updateKeyframeTime(int index, float newTime) {
        if (index < 0 || index >= keyframes.size()) {
            return false;
        }
        
        // 确保新时间在片段范围内
        if (!containsTime(newTime)) {
            newTime = Math.max(getStartTime(), Math.min(getEndTime(), newTime));
        }
        
        // 由于 keyframes 是 TreeSet，需要转换为列表来获取索引对应的值
        List<Float> keyframeList = new ArrayList<>(keyframes);
        float oldTime = keyframeList.get(index);
        
        // 如果新时间和旧时间相同，不需要更新
        if (Math.abs(newTime - oldTime) < 0.0001f) {
            return true;
        }
        
        // 移除旧的关键帧
        keyframes.remove(oldTime);
        
        // 添加新的关键帧
        keyframes.add(newTime);
        
        return true;
    }

    /**
     * 移除一个关键帧。操作是 O(log n) 复杂度。
     * @param time 关键帧的时间点
     * @return 如果成功移除返回 true
     */
    public boolean removeKeyframe(float time) {
        return keyframes.remove(time);
    }

    /**
     * 查找在指定时间点附近的第一个关键帧。
     * 利用 TreeSet 的 floor 和 ceiling 方法实现高效查找 O(log n)。
     *
     * @param time      目标时间点
     * @param tolerance 允许的误差范围
     * @return 如果找到则返回关键帧的 {@link Float} 对象，否则返回 null。
     */
    public Float getKeyframeAt(float time, float tolerance) {
        // 查找时间点之前最近的关键帧
        Float prev = keyframes.floor(time);
        if (prev != null && (time - prev) <= tolerance) {
            return prev;
        }

        // 查找时间点之后最近的关键帧
        Float next = keyframes.ceiling(time);
        if (next != null && (next - time) <= tolerance) {
            return next;
        }

        return null;
    }

    public boolean hasKeyframeAt(float time, float tolerance) {
        return getKeyframeAt(time, tolerance) != null;
    }

    public void clearKeyframes() {
        keyframes.clear();
    }

    /**
     * 获取指定时间范围内的关键帧。
     * @return 一个包含范围内关键帧的新列表
     */
    public List<Float> getKeyframesInRange(float start, float end) {
        return new ArrayList<>(keyframes.subSet(start, true, end, true));
    }

    // ========== Getters & Setters (增强版) ==========

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = Objects.requireNonNull(label); }

    public float getStartTime() { return startTime; }
    public void setStartTime(float startTime) {
        // 验证：确保 new startTime 不会超过 endTime
        this.startTime = Math.min(startTime, this.endTime - MINIMUM_LENGTH);
        pruneOrphanedKeyframes(); // 确保关键帧在范围内
    }

    public float getEndTime() { return endTime; }
    public void setEndTime(float endTime) {
        // 验证：确保 new endTime 不会小于 startTime
        this.endTime = Math.max(endTime, this.startTime + MINIMUM_LENGTH);
        pruneOrphanedKeyframes(); // 确保关键帧在范围内
    }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public UUID getBoundContainerId() { return boundContainerId; }
    public void setBoundContainerId(UUID boundContainerId) { this.boundContainerId = boundContainerId; }

    public String getBoundContainerName() { return boundContainerName; }
    public void setBoundContainerName(String boundContainerName) {
        this.boundContainerName = (boundContainerName == null || boundContainerName.isBlank()) ? null : boundContainerName.trim();
    }

    public void bindToContainer(UUID containerId, String containerName) {
        setBoundContainerId(containerId);
        setBoundContainerName(containerName);
    }

    /**
     * 返回一个关键帧集合的不可修改视图，以保护内部状态。
     * @return 一个不可修改的 {@link NavigableSet}
     */
    public NavigableSet<Float> getKeyframes() {
        return Collections.unmodifiableNavigableSet(keyframes);
    }

    /**
     * 获取关键帧时间列表（语义方法，ChronoBlocks 风格）
     * 
     * <p>返回所有关键帧的时间点列表，按时间排序。
     * 用于需要列表形式的场景（如序列化、遍历等）。
     * 
     * @return 关键帧时间列表（按时间排序）
     */
    public List<Float> getKeyframeTimes() {
        return new ArrayList<>(keyframes);
    }

    /**
     * 移除指定时间点的关键帧（语义方法，ChronoBlocks 风格）
     * 
     * <p>这是 removeKeyframe 的语义化包装，提供更清晰的 API。
     * 
     * @param time 关键帧时间点
     * @return 如果成功移除返回 true
     */
    public boolean removeKeyframeAt(float time) {
        return removeKeyframe(time);
    }

    // ========== 片段复制（语义方法，ChronoBlocks 风格）==========

    /**
     * 创建片段的浅拷贝（语义方法）
     * 
     * <p>复制片段的基本属性（时间、长度、标签、颜色、关键帧等），
     * 但不复制父轨道引用。用于复制/粘贴、分割等操作。
     * 
     * @return 新的片段实例，包含相同的属性
     */
    public TimelineClip duplicateShallow() {
        TimelineClip copy = new TimelineClip(this.label, this.startTime, this.endTime, this.color);
        copy.muted = this.muted;
        copy.opacity = this.opacity;
        copy.animationConfig = this.animationConfig != null ? this.animationConfig.copy() : ClipAnimationConfig.defaultConfig();
        copy.boundContainerId = this.boundContainerId;
        copy.boundContainerName = this.boundContainerName;
        // 复制关键帧
        copy.keyframes.addAll(this.keyframes);
        return copy;
    }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { this.opacity = UIUtils.clamp(opacity, 0f, 1f); }

    public ClipAnimationConfig getAnimationConfig() {
        if (animationConfig == null) {
            animationConfig = ClipAnimationConfig.defaultConfig();
        }
        return animationConfig;
    }

    public void setAnimationConfig(ClipAnimationConfig config) {
        this.animationConfig = config != null ? config.copy() : ClipAnimationConfig.defaultConfig();
    }

    public void applyPreset(ContainerAnimationPreset preset) {
        if (preset == null) {
            return;
        }
        ClipAnimationConfig config = getAnimationConfig();
        config.setMode(preset.getMode());
        config.setDuration(Math.max(config.getDuration(), preset.getSuggestedDuration()));
        config.setDelay(preset.getDelay());
        config.setEasing(preset.getEasing());
        config.setOrdering(preset.getOrdering());
        config.getParams().clear();
        config.getParams().putAll(preset.getParams());
    }
    
    /**
     * 获取父轨道引用（用于 O(1) 反向查找）
     * @return 父轨道，如果未附加到轨道则返回 null
     */
    public TimelineTrack getParentTrack() {
        return parentTrack;
    }
    
    /**
     * 设置父轨道引用（包级私有，仅供 TimelineTrack 调用）
     * @param parentTrack 父轨道引用
     */
    void setParentTrack(TimelineTrack parentTrack) {
        this.parentTrack = parentTrack;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 移除所有落在当前 startTime 和 endTime 之外的关键帧。
     * 确保数据模型的内部一致性，防止"孤儿"关键帧的出现。
     * 
     * 使用 TreeSet.subSet() 获取范围视图，然后 retainAll() 只保留该范围内的元素。
     * 这是一个高效的 O(log n) 操作。
     */
    private void pruneOrphanedKeyframes() {
        // subSet 创建一个指定范围的视图（包含边界）
        // retainAll 只保留该视图中存在的元素，从而移除所有范围外的元素
        keyframes.retainAll(keyframes.subSet(this.startTime, true, this.endTime, true));
    }
}