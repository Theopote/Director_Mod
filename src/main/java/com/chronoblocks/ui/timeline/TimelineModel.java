package com.chronoblocks.ui.timeline;

import com.chronoblocks.testing.DemoTimelineFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    
    // ========== 视图状态 ==========
    private float pixelsPerSecond = 80.0f;
    private float scrollX = 0f;
    private final float minPps = 5.0f;
    private final float maxPps = 800.0f;
    
    // ========== 吸附设置 ==========
    private boolean snapEnabled = true;
    private float snapInterval = 0.25f;
    
    // ========== 轨道数据 ==========
    private final List<TimelineTrack> tracks = new ArrayList<>();
    
    // ========== 性能优化：缓存动态时长 ==========
    private float cachedDynamicDuration = 0f;
    private boolean isDurationDirty = true; // 初始时需要计算
    
    // ========== 统一的选择状态（单一真相源）==========
    private final Set<Object> selectedItems = new HashSet<>();  // 可以是 Keyframe, Clip 等（使用 Set 避免重复）
    private Object lastClickedItem = null;
    private boolean multiSelectMode = false;  // Ctrl 键是否按下
    
    // ========== 构造函数 ==========
    public TimelineModel() {
        // 尝试加载保存的数据
        if (TimelineModelPersistence.hasSavedData()) {
            TimelineModelPersistence.loadFromFile(this);
        } else {
            // 如果没有保存的数据，使用工厂创建演示数据
            DemoTimelineFactory.populateWithDemoData(this);
        }
    }
    
    /**
     * 保存当前时间线数据
     */
    public void save() {
        TimelineModelPersistence.saveToFile(this);
    }
    
    /**
     * 重新加载保存的数据
     */
    public void reload() {
        TimelineModelPersistence.loadFromFile(this);
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
        pixelsPerSecond = clamp(pixelsPerSecond * factor, minPps, maxPps);
    }
    
    public void setZoom(float pps) {
        pixelsPerSecond = clamp(pps, minPps, maxPps);
    }
    
    public void adjustScrollAfterZoom(float mouseX, float contentX, float contentW, float oldPps) {
        float beforeTime = screenToTime(mouseX, contentX, oldPps);
        float afterTime = screenToTime(mouseX, contentX, pixelsPerSecond);
        scrollX += (afterTime - beforeTime) * pixelsPerSecond;
        scrollX = clamp(scrollX, 0f, Math.max(0f, getDuration() * pixelsPerSecond - contentW));
    }
    
    // ========== 滚动控制 ==========
    
    public void scroll(float delta) {
        scrollX += delta;
    }
    
    public void clampScroll(float contentW) {
        scrollX = clamp(scrollX, 0f, Math.max(0f, getDuration() * pixelsPerSecond - contentW));
    }
    
    // ========== 坐标转换 ==========
    
    public float screenToTime(float mouseX, float contentX) {
        return screenToTime(mouseX, contentX, pixelsPerSecond);
    }
    
    private float screenToTime(float mouseX, float contentX, float pps) {
        return (mouseX - contentX + scrollX) / pps;
    }
    
    public float timeToScreen(float time, float contentX) {
        return contentX + (time * pixelsPerSecond) - scrollX;
    }
    
    // ========== 吸附工具 ==========
    
    public float snapToInterval(float time) {
        if (snapInterval <= 0f) return time;
        return Math.round(time / snapInterval) * snapInterval;
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
    
    // ========== 工具方法 ==========
    
    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
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
        this.currentTime = clamp(currentTime, 0f, getDuration());
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
    
    public float getPixelsPerSecond() { return pixelsPerSecond; }
    public float getScrollX() { return scrollX; }
    public void setScrollX(float scrollX) { this.scrollX = scrollX; }
    
    public float getMinPps() { return minPps; }
    public float getMaxPps() { return maxPps; }
    
    public boolean isSnapEnabled() { return snapEnabled; }
    public void setSnapEnabled(boolean snapEnabled) { this.snapEnabled = snapEnabled; }
    
    public float getSnapInterval() { return snapInterval; }
    public void setSnapInterval(float snapInterval) { 
        this.snapInterval = Math.max(0.01f, snapInterval); 
    }
    
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