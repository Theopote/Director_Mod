package com.chronoblocks.ui.timeline;

import com.chronoblocks.core.animation.AnimationState;
import com.chronoblocks.core.playback.PlaybackController;
import java.util.ArrayList;
import java.util.List;

/**
 * Timeline 数据模型（v4.5 一体化交互系统）
 * 
 * 负责管理时间轴的所有业务数据，与 UI 渲染完全解耦
 * 
 * 数据同步策略：
 * - 播放状态：委托给全局 AnimationState
 * - 时间轴状态：委托给全局 AnimationState
 * - 视图状态：本地管理（缩放、滚动）
 * - 轨道数据：本地管理
 * 
 * @author ChronoBlocks Team
 * @version 6.0
 */
public class TimelineModel {
    // ========== 视图状态（本地管理）==========
    private float pixelsPerSecond = 80.0f;
    private float scrollX = 0f;
    private final float minPps = 20.0f;
    private final float maxPps = 800.0f;
    
    // ========== 吸附设置 ==========
    private boolean snapEnabled = true;
    private float snapInterval = 0.25f;
    
    // ========== 轨道数据 ==========
    private final List<TimelineTrack> tracks = new ArrayList<>();
    
    // ========== 选中状态 ==========
    private TimelineClip selectedClip = null;
    
    // ========== v4.5 多选支持 ==========
    private final List<Object> selectedItems = new ArrayList<>();  // 可以是 Keyframe, Clip 等
    private Object lastClickedItem = null;
    private boolean multiSelectMode = false;  // Ctrl 键是否按下
    
    // ========== 构造函数 ==========
    public TimelineModel() {
        createDemoTracks();
    }
    
    // ========== 播放控制（委托给 PlaybackController）==========
    
    public void play() {
        PlaybackController.get().play();
    }
    
    public void pause() {
        PlaybackController.get().pause();
    }
    
    public void togglePlay() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }
    
    public void jumpToStart() {
        AnimationState.get().setCurrentTime(0f);
    }
    
    public void jumpToEnd() {
        AnimationState state = AnimationState.get();
        state.setCurrentTime(state.getDuration());
        state.setPlaying(false);
    }
    
    /**
     * 更新播放逻辑（已废弃，现在由 PlaybackController 统一驱动）
     * @deprecated 保留用于兼容性，实际播放由 PlaybackController.update() 处理
     */
    @Deprecated
    public void updatePlayback(float deltaTime) {
        // 保留空实现，播放逻辑已移到 PlaybackController
        // Timeline 只负责读取 AnimationState 的状态并显示
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
    }
    
    public void removeTrack(TimelineTrack track) {
        tracks.remove(track);
    }
    
    public TimelineTrack getTrackAt(int index) {
        if (index >= 0 && index < tracks.size()) {
            return tracks.get(index);
        }
        return null;
    }
    
    // ========== 片段查找 ==========
    
    public TimelineTrack findTrackForClip(TimelineClip clip) {
        for (TimelineTrack track : tracks) {
            if (track.getClips().contains(clip)) {
                return track;
            }
        }
        return null;
    }
    
    // ========== 工具方法 ==========
    
    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
    
    private void createDemoTracks() {
        TimelineTrack t1 = new TimelineTrack("方块动画");
        TimelineTrack t2 = new TimelineTrack("粒子特效");
        
        TimelineClip clip1 = new TimelineClip("移动", 0.0f, 2.5f, rgba(200,160,80,255));
        TimelineClip clip2 = new TimelineClip("旋转", 3.0f, 5.0f, rgba(120, 200, 140,255));
        TimelineClip clip3 = new TimelineClip("烟雾", 1.0f, 4.0f, rgba(120,180,255,255));
        
        clip1.addKeyframe(0.5f);
        clip1.addKeyframe(1.5f);
        clip2.addKeyframe(3.5f);
        
        t1.addClip(clip1);
        t1.addClip(clip2);
        t2.addClip(clip3);
        
        tracks.add(t1);
        tracks.add(t2);
    }
    
    private static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }
    
    // ========== Getters & Setters（同步到全局 AnimationState）==========
    
    /**
     * 获取播放状态（从全局 AnimationState）
     */
    public boolean isPlaying() { 
        return AnimationState.get().isPlaying(); 
    }
    
    /**
     * 设置播放状态（同步到全局 AnimationState）
     */
    public void setPlaying(boolean playing) { 
        AnimationState.get().setPlaying(playing); 
    }
    
    /**
     * 获取当前时间（从全局 AnimationState）
     */
    public float getCurrentTime() { 
        return AnimationState.get().getCurrentTime(); 
    }
    
    /**
     * 设置当前时间（同步到全局 AnimationState）
     */
    public void setCurrentTime(float currentTime) { 
        AnimationState.get().setCurrentTime(currentTime);
    }
    
    /**
     * 获取持续时间（从全局 AnimationState）
     */
    public float getDuration() { 
        return AnimationState.get().getDuration(); 
    }
    
    /**
     * 设置持续时间（同步到全局 AnimationState）
     */
    public void setDuration(float duration) { 
        AnimationState.get().setDuration(duration); 
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
    
    public TimelineClip getSelectedClip() { return selectedClip; }
    public void setSelectedClip(TimelineClip selectedClip) { 
        this.selectedClip = selectedClip; 
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
            return;
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
     * 全选当前轨道的所有关键帧
     */
    public void selectAll() {
        selectedItems.clear();
        // TODO: 实现全选逻辑
        // for (TimelineTrack track : tracks) {
        //     selectedItems.addAll(track.getKeyframes());
        // }
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


