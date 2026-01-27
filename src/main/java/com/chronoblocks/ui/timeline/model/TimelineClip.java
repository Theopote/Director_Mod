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
    private final NavigableSet<Float> keyframes = new TreeSet<>(); // ⚠️ 保留用于向后兼容
    
    // ⭐ 新增：ClipKeyframe 对象列表（新架构）
    private final java.util.List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> clipKeyframes = new java.util.ArrayList<>();

    // ========== 额外属性 ==========
    private ClipAnimationConfig animationConfig = ClipAnimationConfig.defaultConfig();
    private UUID boundContainerId = null;
    private String boundContainerName = null;
    private boolean autoFadePreviousClips = true;
    private float autoFadeOpacity = 0.35f;
    
    // ========== Post-Animation Fade 状态（问题2修复）==========
    /** 动画结束后的淡化开始时间（相对于片段结束时间，秒）*/
    private float postAnimationFadeStart = 0.0f;
    
    /** 动画结束后的淡化结束时间（相对于片段结束时间，秒）。超过此时间后，方块恢复为完全不透明 */
    private float postAnimationFadeEnd = 2.0f; // 默认在片段结束后2秒恢复
    
    /** 片段恢复为最终状态的绝对时间（endTime + postAnimationFadeEnd），缓存计算 */
    private transient Float cachedRestoreTime = null;
    
    // ========== 可见性关键帧（问题5修复）==========
    /** 可见性关键帧集合（按时间排序） */
    private final NavigableSet<VisibilityKeyframe> visibilityKeyframes = new TreeSet<>();
    
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
        for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : other.clipKeyframes) {
            if (kf != null) {
                this.clipKeyframes.add(kf.copy());
            }
        }
        this.animationConfig = other.animationConfig != null ? other.animationConfig.copy() : ClipAnimationConfig.defaultConfig();
        this.boundContainerId = other.boundContainerId;
        this.boundContainerName = other.boundContainerName;
        this.autoFadePreviousClips = other.autoFadePreviousClips;
        this.autoFadeOpacity = other.autoFadeOpacity;
        this.postAnimationFadeStart = other.postAnimationFadeStart;
        this.postAnimationFadeEnd = other.postAnimationFadeEnd;
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

        if (!this.clipKeyframes.isEmpty()) {
            for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : this.clipKeyframes) {
                if (kf != null) {
                    kf.setTime(kf.getTime() + delta);
                }
            }
            this.clipKeyframes.sort(java.util.Comparator.comparing(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe::getTime));
        }
    }

    public boolean containsTime(float time) {
        return time >= startTime && time <= endTime;
    }

    // ========== 关键帧管理 (基于 TreeSet 优化) ==========

    /**
     * 将 legacy 的 Float keyframes 同步为 ClipKeyframe 对象（只做一次）。
     * <p>
     * 说明：历史代码仍可能通过 {@link #addKeyframe(float)} 写入 TreeSet<Float>，
     * 但交互系统（点击/框选/右键菜单/属性面板）需要稳定的对象（ClipKeyframe）来做 selection/undo/redo。
     */
    private void ensureClipKeyframesFromLegacy() {
        if (!clipKeyframes.isEmpty()) {
            return;
        }
        if (keyframes.isEmpty()) {
            return;
        }
        for (Float t : keyframes) {
            if (t == null) continue;
            clipKeyframes.add(new com.chronoblocks.ui.timeline.model.clip.ClipKeyframe(t));
        }
        clipKeyframes.sort(java.util.Comparator.comparing(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe::getTime));
    }

    /**
     * 添加一个关键帧。操作是 O(log n) 复杂度。
     * @param time 关键帧的时间点
     */
    public void addKeyframe(float time) {
        // 确保关键帧在片段范围内
        if (containsTime(time)) {
            keyframes.add(time);
            // 同步到对象关键帧列表，保证 selection 可用
            ensureClipKeyframesFromLegacy();
            final float EPS = 0.0001f;
            boolean exists = false;
            for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : clipKeyframes) {
                if (kf != null && Math.abs(kf.getTime() - time) <= EPS) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                clipKeyframes.add(new com.chronoblocks.ui.timeline.model.clip.ClipKeyframe(time));
                clipKeyframes.sort(java.util.Comparator.comparing(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe::getTime));
            }
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
        boolean removed = keyframes.remove(time);
        // 同步删除对象关键帧（按时间近似匹配）
        if (removed) {
            ensureClipKeyframesFromLegacy();
            final float EPS = 0.0001f;
            clipKeyframes.removeIf(kf -> kf != null && Math.abs(kf.getTime() - time) <= EPS);
        }
        return removed;
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
        clipKeyframes.clear();
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
        invalidateRestoreTimeCache();
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

    // ========== ClipKeyframe 对象管理（新架构）==========

    /**
     * 获取所有 ClipKeyframe 对象列表（只读）
     * 
     * @return 关键帧对象列表（按时间排序）
     */
    public java.util.List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> getClipKeyframes() {
        ensureClipKeyframesFromLegacy();
        return java.util.Collections.unmodifiableList(clipKeyframes);
    }

    /**
     * 添加一个 ClipKeyframe 对象
     * 
     * @param keyframe 关键帧对象
     */
    public void addKeyframe(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }
        // 确保关键帧在片段范围内
        if (containsTime(keyframe.getTime())) {
            clipKeyframes.add(keyframe);
            clipKeyframes.sort(java.util.Comparator.comparing(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe::getTime));
        }
    }

    /**
     * 移除指定 ID 的关键帧
     * 
     * @param id 关键帧 ID
     */
    public void removeKeyframe(java.util.UUID id) {
        clipKeyframes.removeIf(kf -> kf.getId().equals(id));
    }

    /**
     * 查找指定时间点的 ClipKeyframe 对象
     * 
     * @param time 时间点（全局时间）
     * @param epsilon 允许的误差范围
     * @return 找到的关键帧对象，否则返回 null
     */
    public com.chronoblocks.ui.timeline.model.clip.ClipKeyframe findKeyframeAt(float time, float epsilon) {
        ensureClipKeyframesFromLegacy();
        for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : clipKeyframes) {
            if (Math.abs(kf.getTime() - time) <= epsilon) {
                return kf;
            }
        }
        return null;
    }

    /**
     * 移动关键帧到新时间（保持插值等属性不变）
     * 
     * @param keyframe 关键帧对象
     * @param newTime 新时间（全局时间）
     */
    public void moveKeyframe(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe keyframe, float newTime) {
        if (keyframe == null) {
            return;
        }
        // 确保新时间在片段范围内
        if (!containsTime(newTime)) {
            newTime = Math.max(getStartTime(), Math.min(getEndTime(), newTime));
        }
        keyframe.setTime(newTime);
        clipKeyframes.sort(java.util.Comparator.comparing(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe::getTime));
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
        copy.animationConfig = this.animationConfig != null ? this.animationConfig.copy() : ClipAnimationConfig.defaultConfig();
        copy.boundContainerId = this.boundContainerId;
        copy.boundContainerName = this.boundContainerName;
        copy.autoFadePreviousClips = this.autoFadePreviousClips;
        copy.autoFadeOpacity = this.autoFadeOpacity;
        copy.postAnimationFadeStart = this.postAnimationFadeStart;
        copy.postAnimationFadeEnd = this.postAnimationFadeEnd;
        // 复制关键帧
        copy.keyframes.addAll(this.keyframes);
        for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : this.clipKeyframes) {
            if (kf != null) {
                copy.clipKeyframes.add(kf.copy());
            }
        }
        return copy;
    }

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

    public boolean isAutoFadePreviousClips() {
        return autoFadePreviousClips;
    }

    public void setAutoFadePreviousClips(boolean autoFadePreviousClips) {
        this.autoFadePreviousClips = autoFadePreviousClips;
    }

    public float getAutoFadeOpacity() {
        return autoFadeOpacity;
    }

    public void setAutoFadeOpacity(float autoFadeOpacity) {
        this.autoFadeOpacity = UIUtils.clamp(autoFadeOpacity, 0f, 1f);
        invalidateRestoreTimeCache();
    }
    
    /**
     * 获取 post-animation fade 开始时间（相对于片段结束时间）
     * 
     * @return 淡化开始时间（秒）
     */
    public float getPostAnimationFadeStart() {
        return postAnimationFadeStart;
    }
    
    /**
     * 设置 post-animation fade 开始时间（相对于片段结束时间）
     * 
     * @param postAnimationFadeStart 淡化开始时间（秒），必须 >= 0
     */
    public void setPostAnimationFadeStart(float postAnimationFadeStart) {
        this.postAnimationFadeStart = Math.max(0.0f, postAnimationFadeStart);
        if (this.postAnimationFadeStart > this.postAnimationFadeEnd) {
            this.postAnimationFadeEnd = this.postAnimationFadeStart;
        }
        invalidateRestoreTimeCache();
    }
    
    /**
     * 获取 post-animation fade 结束时间（相对于片段结束时间）
     * 
     * @return 淡化结束时间（秒），超过此时间后方块恢复为完全不透明
     */
    public float getPostAnimationFadeEnd() {
        return postAnimationFadeEnd;
    }
    
    /**
     * 设置 post-animation fade 结束时间（相对于片段结束时间）
     * 
     * @param postAnimationFadeEnd 淡化结束时间（秒），必须 >= postAnimationFadeStart
     */
    public void setPostAnimationFadeEnd(float postAnimationFadeEnd) {
        this.postAnimationFadeEnd = Math.max(this.postAnimationFadeStart, postAnimationFadeEnd);
        invalidateRestoreTimeCache();
    }
    
    /**
     * 获取片段恢复为最终状态的绝对时间
     * 
     * <p>计算方式：endTime + postAnimationFadeEnd
     * 
     * @return 恢复时间（秒）
     */
    public float getRestoreTime() {
        if (cachedRestoreTime == null) {
            cachedRestoreTime = endTime + postAnimationFadeEnd;
        }
        return cachedRestoreTime;
    }
    
    /**
     * 检查指定时间是否在片段的 post-animation fade 阶段
     * 
     * @param time 时间（秒）
     * @return 如果在淡化阶段返回 true
     */
    public boolean isInPostAnimationFade(float time) {
        float fadeStart = endTime + postAnimationFadeStart;
        float fadeEnd = endTime + postAnimationFadeEnd;
        return time >= fadeStart && time <= fadeEnd;
    }
    
    /**
     * 检查指定时间片段是否已经完全恢复（超过 restoreTime）
     * 
     * @param time 时间（秒）
     * @return 如果已经完全恢复返回 true
     */
    public boolean isFullyRestored(float time) {
        return time >= getRestoreTime();
    }
    
    /**
     * 使 restoreTime 缓存失效（当 endTime 或 postAnimationFadeEnd 改变时调用）
     */
    private void invalidateRestoreTimeCache() {
        cachedRestoreTime = null;
    }
    
    // ========== 可见性关键帧管理（问题5修复）==========
    
    /**
     * 获取所有可见性关键帧（只读视图）
     * 
     * @return 不可修改的关键帧集合
     */
    public NavigableSet<VisibilityKeyframe> getVisibilityKeyframes() {
        return Collections.unmodifiableNavigableSet(visibilityKeyframes);
    }
    
    /**
     * 添加可见性关键帧
     * 
     * @param keyframe 关键帧
     * @return 如果成功添加返回 true
     */
    public boolean addVisibilityKeyframe(VisibilityKeyframe keyframe) {
        if (keyframe == null) {
            return false;
        }
        // 确保关键帧时间在片段范围内
        float clipLength = getLength();
        if (keyframe.getTime() < 0 || keyframe.getTime() > clipLength) {
            return false;
        }
        return visibilityKeyframes.add(keyframe);
    }
    
    /**
     * 移除可见性关键帧
     * 
     * @param keyframe 关键帧
     * @return 如果成功移除返回 true
     */
    public boolean removeVisibilityKeyframe(VisibilityKeyframe keyframe) {
        return keyframe != null && visibilityKeyframes.remove(keyframe);
    }
    
    /**
     * 移除指定时间的关键帧
     * 
     * @param time 关键帧时间（相对于片段开始时间）
     * @return 如果找到并移除了关键帧返回 true
     */
    public boolean removeVisibilityKeyframeAt(float time) {
        VisibilityKeyframe toRemove = null;
        for (VisibilityKeyframe kf : visibilityKeyframes) {
            if (Math.abs(kf.getTime() - time) < 0.001f) {
                toRemove = kf;
                break;
            }
        }
        return toRemove != null && visibilityKeyframes.remove(toRemove);
    }
    
    /**
     * 清除所有可见性关键帧
     */
    public void clearVisibilityKeyframes() {
        visibilityKeyframes.clear();
    }
    
    /**
     * 根据时间计算透明度（基于关键帧插值）
     * 
     * <p>使用线性插值在相邻关键帧之间计算透明度值
     * 
     * @param time 时间（相对于片段开始时间，秒）
     * @return 计算出的透明度 [0..1]，如果无关键帧返回 1.0
     */
    public float computeAlphaAt(float time) {
        if (visibilityKeyframes.isEmpty()) {
            return 1.0f; // 无关键帧，默认完全不透明
        }
        
        // 找到时间前后的关键帧
        VisibilityKeyframe lower = visibilityKeyframes.floor(new VisibilityKeyframe(time, 1.0f, VisibilityKeyframe.VisibilityMode.NORMAL));
        VisibilityKeyframe higher = visibilityKeyframes.ceiling(new VisibilityKeyframe(time, 1.0f, VisibilityKeyframe.VisibilityMode.NORMAL));
        
        if (lower == null && higher == null) {
            return 1.0f;
        }
        
        if (lower == null) {
            // 时间在所有关键帧之前，使用第一个关键帧的值
            return higher.getEffectiveAlpha();
        }
        
        if (higher == null) {
            // 时间在所有关键帧之后，使用最后一个关键帧的值
            return lower.getEffectiveAlpha();
        }
        
        if (lower == higher || Math.abs(lower.getTime() - higher.getTime()) < 0.001f) {
            // 正好在关键帧上
            return lower.getEffectiveAlpha();
        }
        
        // 线性插值
        float t = (time - lower.getTime()) / (higher.getTime() - lower.getTime());
        float alpha1 = lower.getEffectiveAlpha();
        float alpha2 = higher.getEffectiveAlpha();
        return alpha1 + (alpha2 - alpha1) * t;
    }
    
    /**
     * 根据全局时间计算透明度
     * 
     * @param globalTime 全局时间（秒）
     * @return 计算出的透明度 [0..1]
     */
    public float computeAlphaAtGlobalTime(float globalTime) {
        float localTime = globalTime - startTime;
        if (localTime < 0) {
            return 0.0f; // 片段还没开始
        }
        if (localTime > getLength()) {
            // 片段已结束，可能需要考虑 post-animation fade
            if (isInPostAnimationFade(globalTime)) {
                // 在淡化阶段，计算淡化透明度
                float fadeStart = endTime + postAnimationFadeStart;
                float fadeEnd = endTime + postAnimationFadeEnd;
                float fadeT = (globalTime - fadeStart) / (fadeEnd - fadeStart);
                // 从 1.0 淡出到 autoFadeOpacity
                return 1.0f - (1.0f - autoFadeOpacity) * fadeT;
            }
            // 片段结束且已恢复
            return computeAlphaAt(getLength());
        }
        return computeAlphaAt(localTime);
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
