package com.chronoblocks.ui.timeline;

import com.chronoblocks.core.animation.AnimationState;
import com.chronoblocks.core.playback.PlaybackController;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final float minPps = 5.0f;   // 降低最小值，支持查看更长的时间线
    private final float maxPps = 800.0f;
    
    // ========== 吸附设置 ==========
    private boolean snapEnabled = true;
    private float snapInterval = 0.25f;
    
    // ========== 轨道数据 ==========
    private final List<TimelineTrack> tracks = new ArrayList<>();
    
    // ========== 选中状态 ==========
    private TimelineClip selectedClip = null;
    
    // ========== v4.5 多选支持 ==========
    private final Set<Object> selectedItems = new HashSet<>();  // 可以是 Keyframe, Clip 等（使用 Set 避免重复）
    private Object lastClickedItem = null;
    private boolean multiSelectMode = false;  // Ctrl 键是否按下
    
    // ========== 构造函数 ==========
    public TimelineModel() {
        // 尝试加载保存的数据
        if (TimelineModelPersistence.hasSavedData()) {
            TimelineModelPersistence.loadFromFile(this);
        } else {
            // 如果没有保存的数据，创建演示轨道
            createDemoTracks();
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
    
    /**
     * 创建演示轨道（仅用于测试和演示）
     * 生产代码不应调用此方法，应从项目数据加载轨道
     * @deprecated 仅供测试使用，请在测试类中调用
     */
    @Deprecated
    public void createDemoTracks() {
        // 容器轨道（方块动画） - 扩展到20秒
        TimelineTrack containerTrack = new TimelineTrack("建筑物出现", TrackType.CONTAINER);
        TimelineClip containerClip1 = new TimelineClip("建筑A", 0.0f, 5.0f, TrackType.CONTAINER.getClipColor());
        TimelineClip containerClip2 = new TimelineClip("建筑B", 6.0f, 12.0f, TrackType.CONTAINER.getClipColor());
        TimelineClip containerClip3 = new TimelineClip("建筑C", 13.0f, 18.0f, TrackType.CONTAINER.getClipColor());
        containerClip1.addKeyframe(0.5f);
        containerClip1.addKeyframe(2.5f);
        containerTrack.addClip(containerClip1);
        containerTrack.addClip(containerClip2);
        containerTrack.addClip(containerClip3);
        
        // 摄像机轨道 - 扩展到20秒
        TimelineTrack cameraTrack = new TimelineTrack("主摄像机", TrackType.CAMERA);
        TimelineClip cameraClip = new TimelineClip("环绕飞行", 0.0f, 20.0f, TrackType.CAMERA.getClipColor());
        cameraClip.addKeyframe(0.0f);
        cameraClip.addKeyframe(5.0f);
        cameraClip.addKeyframe(10.0f);
        cameraClip.addKeyframe(15.0f);
        cameraClip.addKeyframe(20.0f);
        cameraTrack.addClip(cameraClip);
        
        // 天气轨道 - 扩展到20秒
        TimelineTrack weatherTrack = new TimelineTrack("时间与天气", TrackType.WEATHER);
        TimelineClip weatherClip1 = new TimelineClip("黎明", 0.0f, 5.0f, TrackType.WEATHER.getClipColor());
        TimelineClip weatherClip2 = new TimelineClip("正午", 5.0f, 12.0f, TrackType.WEATHER.getClipColor());
        TimelineClip weatherClip3 = new TimelineClip("黄昏", 12.0f, 17.0f, TrackType.WEATHER.getClipColor());
        TimelineClip weatherClip4 = new TimelineClip("夜晚", 17.0f, 20.0f, TrackType.WEATHER.getClipColor());
        weatherTrack.addClip(weatherClip1);
        weatherTrack.addClip(weatherClip2);
        weatherTrack.addClip(weatherClip3);
        weatherTrack.addClip(weatherClip4);
        
        // 特效轨道 - 扩展到20秒
        TimelineTrack effectTrack = new TimelineTrack("粒子特效", TrackType.EFFECT);
        TimelineClip effectClip1 = new TimelineClip("烟雾", 2.0f, 8.0f, TrackType.EFFECT.getClipColor());
        TimelineClip effectClip2 = new TimelineClip("火焰", 10.0f, 16.0f, TrackType.EFFECT.getClipColor());
        effectTrack.addClip(effectClip1);
        effectTrack.addClip(effectClip2);
        
        tracks.add(containerTrack);
        tracks.add(cameraTrack);
        tracks.add(weatherTrack);
        tracks.add(effectTrack);
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
     * 获取持续时间（取用户设定值和片段最大结束时间中的较大值）
     * 这样既能自动适应片段长度，又允许用户手动设置更长的时长
     */
    public float getDuration() {
        float maxEndTime = calculateDynamicDuration();
        float userSetDuration = AnimationState.get().getDuration();
        
        // 返回两者中的较大值，这样用户可以手动设置超过片段长度的时长
        return Math.max(maxEndTime, userSetDuration); 
    }
    
    /**
     * 动态计算持续时间：遍历所有轨道和片段，找到最大的结束时间
     * @return 最大的片段结束时间，如果没有片段则返回 0
     */
    public float calculateDynamicDuration() {
        float maxEndTime = 0f;
        
        for (TimelineTrack track : tracks) {
            for (TimelineClip clip : track.getClips()) {
                maxEndTime = Math.max(maxEndTime, clip.getEndTime());
            }
        }
        
        return maxEndTime;
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
     * 全选所有片段（包括所有轨道的所有 Clip）
     */
    public void selectAll() {
        selectedItems.clear();
        
        // 使用 Stream API 收集所有轨道的所有片段
        List<TimelineClip> allClips = tracks.stream()
            .flatMap(track -> track.getClips().stream())
            .collect(Collectors.toList());
        
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


