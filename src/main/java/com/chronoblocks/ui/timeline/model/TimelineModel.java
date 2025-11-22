package com.chronoblocks.ui.timeline.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.chronoblocks.ui.timeline.persistence.TimelineModelPersistence;
import com.chronoblocks.ui.timeline.snapping.TimelineSnapSettings;
import com.chronoblocks.ui.timeline.snapping.TimelineSnapping;

/**
 * Timeline 数据模型（v7.4 统一真相源）
 * 
 * 负责管理时间轴的所有业务数据，与 UI 渲染完全解耦
 * 
 * 架构改进：
 * - ✅ 单一真相源：TimelineModel 拥有所有数据，不再委托
 * - ✅ 消除单例依赖：不依赖旧 AnimationState 或 PlaybackController
 * - ✅ 完全自洽：模型管理自己的状态
 * - ✅ 易于测试：无全局依赖
 * 
 * @author ChronoBlocks Team
 * @version 7.4
 */
public class TimelineModel {
    // ========== 核心动画状态（现在直接拥有）==========
    private boolean playing = false;
    private float currentTime = 0f;
    private float duration = 20.0f; // 默认持续时间
    private boolean loopEnabled = false; // 循环播放
    
    // ========== 视图状态（由外部注入）==========
    private final TimelineViewportState viewportState;
    
    // ========== 吸附设置（统一配置系统）==========
    private final TimelineSnapSettings snapSettings = new TimelineSnapSettings();
    
    // ========== 轨道数据 ==========
    private final List<TimelineTrack> tracks = new ArrayList<>();
    
    // ========== 性能优化：缓存动态时长 ==========
    private float cachedDynamicDuration = 0f;
    private boolean isDurationDirty = true; // 初始时需要计算
    
    // ========== 统一的选择状态（单一真相源）==========
    private final Set<Object> selectedItems = new HashSet<>();  // 可以是 Keyframe, Clip 等（使用 Set 避免重复）
    private Object lastClickedItem = null;
    private boolean multiSelectMode = false;  // Ctrl 键是否按下
    
    // ========== 模型变更监听器系统 ==========
    /**
     * 模型变更监听器接口
     */
    public interface ModelChangeListener {
        /**
         * 当模型数据发生变更时调用
         * @param changeType 变更类型（如 "clip_added", "track_deleted" 等）
         */
        void onModelChanged(String changeType);
    }
    
    private final List<ModelChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    /**
     * 添加模型变更监听器
     */
    public void addModelChangeListener(ModelChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * 移除模型变更监听器
     */
    public void removeModelChangeListener(ModelChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 通知所有监听器模型已变更
     * @param changeType 变更类型
     */
    public void notifyModelChanged(String changeType) {
        for (ModelChangeListener listener : listeners) {
            try {
                listener.onModelChanged(changeType);
            } catch (Exception e) {
                // 防止监听器异常影响其他监听器
                System.err.println("[TimelineModel] 监听器异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 标记模型已变更（同时标记时长需要重新计算）
     */
    public void markDirty() {
        markDurationDirty();
        notifyModelChanged("model_changed");
    }
    
    // ========== 构造函数 ==========
    public TimelineModel(TimelineViewportState viewportState) {
        this.viewportState = viewportState != null ? viewportState : new TimelineViewportState();

        // 尝试加载保存的数据
        if (TimelineModelPersistence.hasSavedData()) {
            TimelineModelPersistence.loadFromFile(this, this.viewportState);
        }
        // 如果没有保存的数据，轨道列表已初始化为空，时间线为空
    }
    
    /**
     * 保存当前时间线数据
     */
    public void save() {
        TimelineModelPersistence.saveToFile(this, viewportState);
    }
    
    /**
     * 重新加载保存的数据
     */
    public void reload() {
        TimelineModelPersistence.loadFromFile(this, viewportState);
    }
    
    // ========== 播放控制（现在直接管理自己的状态）==========
    
    public void play() {
        setPlaying(true);
    }
    
    public void pause() {
        setPlaying(false);
    }
    
    public void togglePlay() {
        this.playing = !this.playing;
    }
    
    public void jumpToStart() {
        setCurrentTime(0f);
    }
    
    public void jumpToEnd() {
        setCurrentTime(getDuration());
        setPlaying(false);
    }
    
    public boolean isLoopEnabled() {
        return loopEnabled;
    }
    
    public void setLoopEnabled(boolean loopEnabled) {
        this.loopEnabled = loopEnabled;
    }
    
    // ========== 缩放控制 ==========
    
    public void zoom(float factor) {
        float current = viewportState.getPixelsPerSecond();
        viewportState.setPixelsPerSecond(current * factor);
    }
    
    public void setZoom(float pps) {
        viewportState.setPixelsPerSecond(pps);
    }
    
    public void adjustScrollAfterZoom(float mouseX, float contentX, float contentW, float oldPps) {
        float beforeTime = screenToTime(mouseX, contentX, oldPps);
        float afterTime = screenToTime(mouseX, contentX, viewportState.getPixelsPerSecond());
        float scrollX = viewportState.getScrollX();
        scrollX += (afterTime - beforeTime) * viewportState.getPixelsPerSecond();
        viewportState.setScrollX(clamp(scrollX, Math.max(0f, getDuration() * viewportState.getPixelsPerSecond() - contentW)));
    }
    
    // ========== 滚动控制 ==========
    
    public void scroll(float delta) {
        viewportState.setScrollX(viewportState.getScrollX() + delta);
    }
    
    public void clampScroll(float contentW) {
        float maxScroll = Math.max(0f, getDuration() * viewportState.getPixelsPerSecond() - contentW);
        viewportState.setScrollX(clamp(viewportState.getScrollX(), maxScroll));
    }
    
    // ========== 坐标转换 ==========
    
    public float screenToTime(float mouseX, float contentX) {
        return screenToTime(mouseX, contentX, viewportState.getPixelsPerSecond());
    }
    
    private float screenToTime(float mouseX, float contentX, float pps) {
        return (mouseX - contentX + viewportState.getScrollX()) / pps;
    }
    
    public float timeToScreen(float time, float contentX) {
        return contentX + (time * viewportState.getPixelsPerSecond()) - viewportState.getScrollX();
    }
    
    // ========== 吸附配置 API（ChronoBlocks 统一风格）==========
    
    /**
     * 获取吸附配置对象（UI / 面板可以直接修改这里）
     * 
     * @return 吸附配置对象
     */
    public TimelineSnapSettings getSnapSettings() {
        return snapSettings;
    }
    
    /**
     * 当前缩放下的吸附时间阈值（秒）
     * 
     * <p>根据当前缩放级别（pixelsPerSecond）将像素阈值转换为时间阈值。
     * 用于吸附计算时判断时间差是否在阈值范围内。
     * 
     * @return 时间阈值（秒），如果缩放级别无效则返回 Float.MAX_VALUE
     */
    public float getSnapTimeThreshold() {
        float pps = getPixelsPerSecond();
        if (pps <= 0.0001f) return Float.MAX_VALUE;
        return snapSettings.getPixelThreshold() / pps;
    }
    
    /**
     * 像素吸附阈值（px）——给工具栏 / 设置面板使用
     * 
     * @return 像素吸附阈值
     */
    public float getSnapPixelThreshold() {
        return snapSettings.getPixelThreshold();
    }
    
    /**
     * 设置像素吸附阈值
     * 
     * @param px 像素阈值（必须 >= 1）
     */
    public void setSnapPixelThreshold(float px) {
        snapSettings.setPixelThreshold(px);
    }
    
    // ---------- 统一"是否启用"入口 ----------
    
    /**
     * 是否启用吸附系统（统一 API）
     * 
     * @return 是否启用吸附
     */
    public boolean isSnappingEnabled() {
        return snapSettings.isEnabled();
    }
    
    /**
     * 设置是否启用吸附系统
     * 
     * @param enabled 是否启用
     */
    public void setSnappingEnabled(boolean enabled) {
        snapSettings.setEnabled(enabled);
    }
    
    // ---------- 网格吸附（供 TimelineSnapping 调用） ----------
    
    /**
     * 吸附到网格（仅做数学运算，不检查开关）
     * 
     * <p>注意：此方法不检查吸附是否启用，仅执行数学运算。
     * 调用方应通过 {@link #isSnapToGridEnabled()} 检查是否启用网格吸附。
     * 
     * @param time 原始时间（秒）
     * @return 吸附后的时间（秒）
     */
    public float snapToGrid(float time) {
        float interval = snapSettings.getGridInterval();
        if (interval <= 0.0001f) return time;
        float n = Math.round(time / interval);
        return n * interval;
    }
    
    // ---------- 供 TimelineSnapping 查询的开关 ----------
    
    /**
     * 是否启用网格吸附（总开关 + 网格开关）
     * 
     * @return 是否启用网格吸附
     */
    public boolean isSnapToGridEnabled() {
        return snapSettings.isEnabled() && snapSettings.isSnapToGrid();
    }
    
    /**
     * 是否吸附片段边缘（总开关 + 片段边缘开关）
     * 
     * @return 是否吸附片段边缘
     */
    public boolean snapToClipEdges() {
        return snapSettings.isEnabled() && snapSettings.isSnapToClipEdges();
    }
    
    /**
     * 是否吸附关键帧（总开关 + 关键帧开关）
     * 
     * @return 是否吸附关键帧
     */
    public boolean snapToKeyframes() {
        return snapSettings.isEnabled() && snapSettings.isSnapToKeyframes();
    }
    
    // ====== 轨道访问（提供给吸附 / 命中检测使用）======
    
    /**
     * 返回全部轨道列表。
     * 
     * <p>建议只在 UI / 交互层使用，不要在这里直接修改列表顺序。
     * 如需修改轨道，请使用 {@link #addTrack(TimelineTrack)}、{@link #removeTrack(TimelineTrack)} 等方法。
     * 
     * @return 所有轨道的列表（可修改，但建议只读访问）
     */
    public List<TimelineTrack> getAllTracks() {
        return tracks;
    }
    
    // ========== 向后兼容的旧 API（已废弃）==========
    
    /**
     * @deprecated 请使用 {@link #isSnappingEnabled()} 和 {@link #getSnapSettings()}
     */
    @Deprecated
    public boolean isSnapEnabled() {
        return snapSettings.isEnabled();
    }
    
    /**
     * @deprecated 请使用 {@link #setSnappingEnabled(boolean)} 或 {@link #getSnapSettings()}
     */
    @Deprecated
    public void setSnapEnabled(boolean enabled) {
        snapSettings.setEnabled(enabled);
    }
    
    /**
     * @deprecated 请使用 {@link TimelineSnapSettings#getGridInterval()}
     */
    @Deprecated
    public float getSnapInterval() {
        return snapSettings.getGridInterval();
    }
    
    /**
     * @deprecated 请使用 {@link TimelineSnapSettings#setGridInterval(float)}
     */
    @Deprecated
    public void setSnapInterval(float interval) {
        snapSettings.setGridInterval(interval);
    }
    
    /**
     * @deprecated 请使用 {@link #snapToGrid(float)} + {@link TimelineSnapping}
     */
    @Deprecated
    public float snapToInterval(float time) {
        if (!snapSettings.isEnabled() || !snapSettings.isSnapToGrid()) {
            return time;
        }
        return snapToGrid(time);
    }
    
    // ========== 轨道管理 ==========
    
    public void addTrack(TimelineTrack track) {
        tracks.add(track);
        markDurationDirty(); // 轨道添加可能影响总时长
    }
    
    public void removeTrack(TimelineTrack track) {
        tracks.remove(track);
        markDurationDirty(); // 轨道移除可能影响总时长
    }
    
    public TimelineTrack getTrackAt(int index) {
        if (index >= 0 && index < tracks.size()) {
            return tracks.get(index);
        }
        return null;
    }

    // ========== 轨道管理（语义方法，ChronoBlocks 风格）==========

    /**
     * 安全获取轨道（语义方法）
     * 
     * <p>如果索引有效则返回轨道，否则返回 null。
     * 避免数组越界异常。
     * 
     * @param index 轨道索引
     * @return 轨道实例，如果索引无效则返回 null
     */
    public TimelineTrack getTrackSafe(int index) {
        return (index >= 0 && index < tracks.size()) ? tracks.get(index) : null;
    }

    /**
     * 获取轨道数量（语义方法）
     * 
     * @return 轨道数量
     */
    public int getTrackCount() {
        return tracks.size();
    }

    /**
     * 在指定轨道上方插入新轨道（语义方法）
     * 
     * @param target 目标轨道
     */
    public void insertTrackAbove(TimelineTrack target) {
        int idx = tracks.indexOf(target);
        if (idx >= 0) {
            TimelineTrack newTrack = new TimelineTrack("新轨道");
            tracks.add(idx, newTrack);
            markDurationDirty();
        }
    }

    /**
     * 在指定轨道下方插入新轨道（语义方法）
     * 
     * @param target 目标轨道
     */
    public void insertTrackBelow(TimelineTrack target) {
        int idx = tracks.indexOf(target);
        if (idx >= 0) {
            TimelineTrack newTrack = new TimelineTrack("新轨道");
            tracks.add(idx + 1, newTrack);
            markDurationDirty();
        }
    }
    
    // ========== 片段查找（O(1) 高效操作）==========
    
    /**
     * 查找片段所在的轨道（O(1) 高效操作）。
     * 使用父引用实现超快查找，避免遍历整个数据结构。
     * @param clip 要查找的片段
     * @return 片段所属的轨道，如果片段未附加到轨道则返回 null
     */
    public TimelineTrack findTrackForClip(TimelineClip clip) {
        if (clip == null) return null;
        return clip.getParentTrack(); // O(1) 超高效！
    }

    // ========== Clip 管理（语义方法，ChronoBlocks 风格）==========

    /**
     * 添加片段到轨道（语义方法）
     * 
     * <p>将片段添加到指定轨道，并自动设置父引用。
     * 
     * @param track 目标轨道
     * @param clip 要添加的片段
     */
    public void addClip(TimelineTrack track, TimelineClip clip) {
        if (track != null && clip != null) {
            track.addClip(clip);
            markDurationDirty();
        }
    }

    /**
     * 从轨道移除片段（语义方法）
     * 
     * <p>从指定轨道移除片段，并清除父引用。
     * 
     * @param track 目标轨道
     * @param clip 要移除的片段
     */
    public void removeClip(TimelineTrack track, TimelineClip clip) {
        if (track != null && clip != null) {
            track.removeClip(clip);
            markDurationDirty();
        }
    }

    /**
     * 在指定时间点分割片段（语义方法）
     * 
     * <p>将片段在指定时间点分割成两个片段。
     * 原片段保留前半部分，新片段包含后半部分。
     * 
     * @param track 片段所在的轨道
     * @param clip 要分割的片段
     * @param time 分割时间点（必须在片段范围内）
     */
    public void splitClipAtTime(TimelineTrack track, TimelineClip clip, float time) {
        if (track == null || clip == null) return;
        
        float clipStart = clip.getStartTime();
        float clipEnd = clip.getEndTime();
        
        // 验证分割点是否在片段范围内
        if (time <= clipStart || time >= clipEnd) {
            return;
        }

        // 计算分割后的长度
        float firstLength = time - clipStart;
        float secondLength = clipEnd - time;

        // 调整原片段的长度
        clip.setLength(firstLength);

        // 创建新片段（后半部分）
        TimelineClip second = clip.duplicateShallow();
        second.setStartTime(time);
        second.setLength(secondLength);

        // 将新片段添加到轨道
        track.addClip(second);
        
        markDurationDirty();
    }
    
    // ========== 工具方法 ==========
    
    private float clamp(float v, float b) {
        return Math.max((float) 0.0, Math.min(b, v));
    }
    
    // ========== Getters & Setters（直接管理自己的状态）==========
    
    /**
     * 获取播放状态
     */
    public boolean isPlaying() { 
        return this.playing; 
    }
    
    /**
     * 设置播放状态
     */
    public void setPlaying(boolean playing) { 
        this.playing = playing; 
    }
    
    /**
     * 获取当前时间
     */
    public float getCurrentTime() { 
        return this.currentTime; 
    }
    
    /**
     * 设置当前时间（自动限制在有效范围内）
     */
    public void setCurrentTime(float currentTime) { 
        this.currentTime = clamp(currentTime, getDuration());
    }
    
    /**
     * 获取持续时间（取用户设定值和片段最大结束时间中的较大值）
     * 使用缓存机制避免频繁重新计算，提升性能。
     * 这样既能自动适应片段长度，又允许用户手动设置更长的时长
     */
    public float getDuration() {
        if (isDurationDirty) {
            this.cachedDynamicDuration = calculateDynamicDuration();
            this.isDurationDirty = false;
        }
        // 返回两者中的较大值
        return Math.max(this.cachedDynamicDuration, this.duration); 
    }
    
    /**
     * 动态计算持续时间：遍历所有轨道和片段，找到最大的结束时间
     * @return 最大的片段结束时间，如果没有片段则返回 0
     */
    private float calculateDynamicDuration() {
        float maxEndTime = 0f;
        
        for (TimelineTrack track : tracks) {
            for (TimelineClip clip : track.getClips()) {
                maxEndTime = Math.max(maxEndTime, clip.getEndTime());
            }
        }
        
        return maxEndTime;
    }
    
    /**
     * 标记时长需要重新计算（当片段添加/删除/修改时调用）
     */
    public void markDurationDirty() {
        this.isDurationDirty = true;
    }
    
    /**
     * 设置持续时间
     */
    public void setDuration(float duration) { 
        this.duration = Math.max(0.1f, duration); 
    }
    
    public TimelineViewportState getViewportState() {
        return viewportState;
    }

    public float getPixelsPerSecond() { return viewportState.getPixelsPerSecond(); }
    public float getScrollX() { return viewportState.getScrollX(); }
    public void setScrollX(float scrollX) { viewportState.setScrollX(scrollX); }
    
    public float getMinPps() { return viewportState.getMinPixelsPerSecond(); }
    public float getMaxPps() { return viewportState.getMaxPixelsPerSecond(); }
    
    public List<TimelineTrack> getTracks() { return tracks; }
    
    /**
     * 获取当前选中的单个片段（便利方法）。
     * 如果选中了多个片段，则返回最后点击的那个（如果是片段的话）。
     * @return 选中的 TimelineClip，如果没有或选中的不是 Clip，则返回 null。
     */
    public TimelineClip getSelectedClip() {
        if (lastClickedItem instanceof TimelineClip && selectedItems.contains(lastClickedItem)) {
            return (TimelineClip) lastClickedItem;
        }
        // 作为备选，返回集合中的第一个 Clip
        return selectedItems.stream()
                .filter(TimelineClip.class::isInstance)
                .map(TimelineClip.class::cast)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 选择单个片段，这会覆盖当前的所有选择。
     * @param clip 要选择的片段，如果为 null 则清空选择。
     */
    public void setSelectedClip(TimelineClip clip) {
        selectedItems.clear();
        if (clip != null) {
            selectedItems.add(clip);
        }
        lastClickedItem = clip;
    }
    
    // ========== v4.5 多选功能 ==========
    
    /**
     * 选择单个项目
     * @param item 要选择的项目（Keyframe, Clip等）
     * @param addToSelection 是否添加到选择（Ctrl键）
     */
    public void selectItem(Object item, boolean addToSelection) {
        if (item == null) {
            return;
        }
        
        if (addToSelection) {
            // 多选模式：切换选中状态
            if (selectedItems.contains(item)) {
                selectedItems.remove(item);
            } else {
                selectedItems.add(item);
            }
        } else {
            // 单选模式：清空后选中
            selectedItems.clear();
            selectedItems.add(item);
        }
        
        lastClickedItem = item;
    }
    
    /**
     * 范围选择（Shift键）
     * @param item 范围结束项
     */
    public void selectRange(Object item) {
        if (item == null || lastClickedItem == null) {
        }
        
        // TODO: 实现范围选择逻辑
        // 需要根据时间顺序选择 lastClickedItem 和 item 之间的所有项
    }
    
    /**
     * 清除所有选择
     */
    public void clearSelection() {
        selectedItems.clear();
        lastClickedItem = null;
    }
    
    /**
     * 全选所有片段（包括所有轨道的所有 Clip）
     */
    public void selectAll() {
        selectedItems.clear();
        
        // 使用 Stream API 收集所有轨道的所有片段
        List<TimelineClip> allClips = tracks.stream()
            .flatMap(track -> track.getClips().stream())
            .toList();
        
        selectedItems.addAll(allClips);
    }
    
    /**
     * 获取所有选中的项目
     */
    public List<Object> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }
    
    /**
     * 获取选中项目的数量
     */
    public int getSelectedCount() {
        return selectedItems.size();
    }
    
    /**
     * 检查项目是否被选中
     */
    public boolean isSelected(Object item) {
        return selectedItems.contains(item);
    }
    
    /**
     * 设置多选模式（Ctrl键状态）
     */
    public void setMultiSelectMode(boolean enabled) {
        this.multiSelectMode = enabled;
    }
    
    /**
     * 获取多选模式状态
     */
    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }
}