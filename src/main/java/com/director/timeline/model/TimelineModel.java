package com.director.timeline.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.director.persistence.PersistenceSaveStatus;
import com.director.timeline.actor.ActorTimelineSource;
import com.director.timeline.audio.TimelineAudioLoadHook;
import com.director.timeline.audio.TimelineClipSoundCollector;
import com.director.timeline.audio.TimelineTrackPlaybackListener;
import com.director.timeline.playback.TimelinePlaybackLifecycle;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.director.timeline.snapping.TimelineSnapSettings;
import com.director.timeline.snapping.TimelineSnapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Timeline 数据模型（v7.4 统一真相源）
 * <p>
 * 负责管理时间轴的所有业务数据，与 UI 渲染完全解耦
 * <p>
 * 架构改进：
 * - ✅ 单一真相源：TimelineModel 拥有所有数据，不再委托
 * - ✅ 消除单例依赖：不依赖旧 AnimationState 或 core 播放路径
 * - ✅ 完全自洽：模型管理自己的状态
 * - ✅ 易于测试：无全局依赖
 * 
 * @author Director Team
 * @version 7.4
 */
public class TimelineModel implements ITimelineProject, ActorTimelineSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineModel.class);

    private float frameRate = 30.0f;
    private int timeUnit = 0;
    // ========== 核心动画状态（现在直接拥有）==========
    private boolean playing = false;
    private float currentTime = 0f;
    private float duration = 20.0f; // 默认持续时间
    private boolean loopEnabled = false; // 循环播放
    private float playbackSpeed = 1.0f; // 播放速度倍率（0.5x/1x/2x/3x...）
    private boolean scrubbing = false; // 是否正在拖动播放头
    private Float inPoint = null; // 播放入点（null 表示未设置）
    private Float outPoint = null; // 播放出点（null 表示未设置）
    // 持久化反序列化期间抑制写回，避免加载过程中的 setter 触发空数据覆盖存档。
    private boolean suppressPersistenceWrites = false;
    
    // ========== 视图状态（由外部注入）==========
    private final TimelineViewportState viewportState;
    
    // ========== 吸附设置（统一配置系统）==========
    private final TimelineSnapSettings snapSettings = new TimelineSnapSettings();

    private TimelineTrackPlaybackListener trackPlaybackListener = TimelineTrackPlaybackListener.NOOP;
    private TimelineAudioLoadHook audioLoadHook = TimelineAudioLoadHook.NOOP;
    private TimelinePlaybackLifecycle playbackLifecycle = TimelinePlaybackLifecycle.NOOP;
    
    // ========== 轨道数据 ==========
    private final List<TimelineTrack> tracks = new ArrayList<>();
    
    // ========== 标记数据（全局标记，不属于任何轨道）==========
    private final List<TimelineMarker> markers = new ArrayList<>();
    
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
    private final List<ActorTimelineSource.TrackStructureListener> trackStructureListeners = new CopyOnWriteArrayList<>();
    
    // ⭐ 性能优化：防止 notifyModelChanged 的无限循环
    private boolean isNotifyingListeners = false;
    private volatile long lastSaveTime = 0;
    private static final long MIN_SAVE_INTERVAL_MS = 1000; // 最小保存间隔：1秒（避免频繁保存导致崩溃）

    /**
     * 通知所有监听器模型已变更
     * @param changeType 变更类型
     */
    public void notifyModelChanged(String changeType) {
        // ⭐ 性能优化：防止监听器回调中再次调用 markDirty() 导致的无限循环
        if (isNotifyingListeners) {
            // 如果正在通知监听器，跳过本次通知（防止循环）
            return;
        }
        
        isNotifyingListeners = true;
        try {
            for (ModelChangeListener listener : listeners) {
                try {
                    listener.onModelChanged(changeType);
                } catch (RuntimeException e) {
                    LOGGER.warn("[TimelineModel] 监听器异常 (changeType={}): {}", changeType, e.getMessage());
                    LOGGER.debug("[TimelineModel] 监听器异常详情", e);
                }
            }
            if ("track_added".equals(changeType) || "track_removed".equals(changeType) || "track_moved".equals(changeType)) {
                for (ActorTimelineSource.TrackStructureListener listener : trackStructureListeners) {
                    try {
                        listener.onTrackStructureChanged();
                    } catch (RuntimeException e) {
                        LOGGER.warn("[TimelineModel] 轨道结构监听器异常: {}", e.getMessage());
                        LOGGER.debug("[TimelineModel] 轨道结构监听器异常详情", e);
                    }
                }
            }
        } finally {
            isNotifyingListeners = false;
        }
    }
    
    /**
     * 添加模型变更监听器
     * @param listener 要添加的监听器
     */
    public void addModelChangeListener(ModelChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * 移除模型变更监听器
     * @param listener 要移除的监听器
     */
    public void removeModelChangeListener(ModelChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public void addTrackStructureListener(TrackStructureListener listener) {
        if (listener != null && !trackStructureListeners.contains(listener)) {
            trackStructureListeners.add(listener);
        }
    }

    @Override
    public void removeTrackStructureListener(TrackStructureListener listener) {
        if (listener != null) {
            trackStructureListeners.remove(listener);
        }
    }
    
    /**
     * 标记模型已变更（同时标记时长需要重新计算）
     * ⭐ 性能优化：添加防重复调用机制和节流保存
     */
    public void markDirty() {
        if (suppressPersistenceWrites) {
            return;
        }

        markDurationDirty();
        notifyModelChanged("model_changed");

        // ⭐ 性能优化：节流保存操作，避免频繁的文件 I/O 阻塞主线程
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSaveTime >= MIN_SAVE_INTERVAL_MS) {
            if (save()) {
                lastSaveTime = currentTime;
            }
        }
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
     *
     * @return 时间线与 Actor 数据是否均成功落盘
     */
    public boolean save() {
        if (suppressPersistenceWrites) {
            return true;
        }

        boolean timelineOk = TimelineModelPersistence.saveToFile(this, viewportState);
        boolean actorOk = com.director.actor.ActorRegistryManager.save();
        PersistenceSaveStatus.reportTimelineResult(timelineOk);
        return timelineOk && actorOk;
    }

    /**
     * 设置是否抑制持久化写入（仅供持久化层在加载阶段使用）。
     */
    public void setSuppressPersistenceWrites(boolean suppress) {
        this.suppressPersistenceWrites = suppress;
    }

    public void setClipSoundCollector(TimelineClipSoundCollector collector) {
        TimelineClipSoundCollector clipSoundCollector = collector != null ? collector : TimelineClipSoundCollector.NOOP;
        TimelineTrack.setClipSoundCollector(clipSoundCollector);
    }

    public void setTrackPlaybackListener(TimelineTrackPlaybackListener listener) {
        this.trackPlaybackListener = listener != null ? listener : TimelineTrackPlaybackListener.NOOP;
    }

    public void setAudioLoadHook(TimelineAudioLoadHook hook) {
        this.audioLoadHook = hook != null ? hook : TimelineAudioLoadHook.NOOP;
    }

    public void setPlaybackLifecycle(TimelinePlaybackLifecycle lifecycle) {
        this.playbackLifecycle = lifecycle != null ? lifecycle : TimelinePlaybackLifecycle.NOOP;
    }

    public TimelineAudioLoadHook getAudioLoadHook() {
        return audioLoadHook;
    }

    
    /**
     * 重新加载保存的数据
     */
    public void reload() {
        TimelineModelPersistence.loadFromFile(this, viewportState);
        // 同时重新加载 ActorRegistry
        com.director.actor.ActorRegistry registry = 
            com.director.actor.ActorRegistryManager.getInstance();
        com.director.actor.persistence.ActorRegistryPersistence.loadFromFile(registry);
    }

    /**
     * ⭐ 按“当前存档”重新加载（用于存档切换时：新存档为空；老存档恢复上次工程）。
     * <p>
     * 注意：如果当前存档没有任何保存文件，本方法会清空内存并保持空工程，避免把上一个存档的数据带过来。
     * </p>
     */
    public void reloadForCurrentWorld() {
        resetInMemoryToDefaults();
        if (TimelineModelPersistence.hasSavedData()) {
            TimelineModelPersistence.loadFromFile(this, viewportState);
        }

        // 同步 ActorRegistry：先清空内存，再加载当前存档的数据（若存在）。
        try {
            com.director.actor.ActorRegistry registry = com.director.actor.ActorRegistryManager.getInstance();
            registry.clear();
            if (com.director.actor.persistence.ActorRegistryPersistence.hasSavedData()) {
                com.director.actor.persistence.ActorRegistryPersistence.loadFromFile(registry);
            }
        } catch (Exception e) {
            LOGGER.error("[TimelineModel] 切档重载 ActorRegistry 失败", e);
        }

        try {
            com.director.actor.path.ActorPathLibraryManager.reloadForCurrentWorld();
        } catch (Exception e) {
            LOGGER.error("[TimelineModel] 切档重载 ActorPathLibrary 失败", e);
        }
    }

    /**
     * 仅重置内存状态（不删除/写入任何持久化文件）。
     * 用于“切换世界”这类场景：让 UI 从干净状态开始，然后再选择性加载该世界的存档。
     */
    private void resetInMemoryToDefaults() {
        playing = false;
        scrubbing = false;
        loopEnabled = false;
        playbackSpeed = 1.0f;
        currentTime = 0f;
        frameRate = 30.0f;
        timeUnit = 0;
        duration = 20.0f;
        inPoint = null;
        outPoint = null;
        cachedDynamicDuration = 0f;
        isDurationDirty = true;
        tracks.clear();
        markers.clear();
        selectedItems.clear();
        lastClickedItem = null;
        multiSelectMode = false;

        // 重置视图
        viewportState.setPixelsPerSecond(80f);
        viewportState.setScrollX(0f);
        viewportState.setScrollY(0f);

        // 重置吸附
        snapSettings.setEnabled(true);
        snapSettings.setSnapToGrid(true);
        snapSettings.setSnapToClipEdges(true);
        snapSettings.setSnapToKeyframes(true);
        snapSettings.setGridInterval(0.1f);
        snapSettings.setPixelThreshold(8f);

        // 断开摄像机驱动（避免切档瞬间残留状态）
        playbackLifecycle.onTimelineMemoryReset();
    }

    /**
     * 重置为全新的空时间线（用于新存档或“新建动画”）。
     */
    public void resetToEmpty() {
        resetInMemoryToDefaults();

        // 删除旧存档，随后保存全新状态
        com.director.timeline.persistence.TimelineModelPersistence.resetForCurrentWorld();
        save();
    }
    
    // ========== 播放控制（现在直接管理自己的状态）==========
    
    public void play() {
        setPlaying(true);
    }
    
    public void pause() {
        setPlaying(false);
    }
    
    public void togglePlay() {
        boolean newState = !this.playing;
        setPlaying(newState);
    }

    @Override
    public float getFrameRate() {
        return frameRate;
    }

    @Override
    public void setFrameRate(float frameRate) {
        this.frameRate = Math.clamp(frameRate, 1.0f, 240.0f);
        markDirty();
    }

    @Override
    public int getTimeUnit() {
        return timeUnit;
    }

    @Override
    public void setTimeUnit(int timeUnit) {
        this.timeUnit = Math.clamp(timeUnit, 0, 2);
        markDirty();
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
        if (this.loopEnabled == loopEnabled) {
            return;
        }
        this.loopEnabled = loopEnabled;
        markDirty();
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPlaybackSpeed(float playbackSpeed) {
        // 合理范围：防止 0 或负数导致卡死/倒放（如需倒放以后再单独实现）
        float clamped = Math.max(0.1f, Math.min(8.0f, playbackSpeed));
        if (Math.abs(this.playbackSpeed - clamped) < 0.0001f) {
            return;
        }
        this.playbackSpeed = clamped;
        markDirty();
    }

    public boolean hasInPoint() {
        return inPoint != null;
    }

    public boolean hasOutPoint() {
        return outPoint != null;
    }

    public float getInPoint() {
        return inPoint != null ? inPoint : 0f;
    }

    public float getOutPoint() {
        return outPoint != null ? outPoint : getDuration();
    }

    public void setInPoint(float point) {
        Float oldInPoint = inPoint;
        Float oldOutPoint = outPoint;
        float clamped = clamp(point, getDuration());
        inPoint = clamped;
        if (outPoint != null && outPoint < clamped) {
            outPoint = clamped;
        }
        if (currentTime < clamped) {
            setCurrentTime(clamped);
        }
        if (!java.util.Objects.equals(oldInPoint, inPoint) || !java.util.Objects.equals(oldOutPoint, outPoint)) {
            markDirty();
        }
    }

    public void setOutPoint(float point) {
        Float oldOutPoint = outPoint;
        float clamped = clamp(point, getDuration());
        if (inPoint != null && clamped < inPoint) {
            clamped = inPoint;
        }
        outPoint = clamped;
        if (currentTime > clamped) {
            setCurrentTime(clamped);
        }
        if (!java.util.Objects.equals(oldOutPoint, outPoint)) {
            markDirty();
        }
    }

    public void clearInOut() {
        if (inPoint == null && outPoint == null) {
            return;
        }
        inPoint = null;
        outPoint = null;
        markDirty();
    }

    public float getPlaybackRangeStart() {
        return inPoint != null ? inPoint : 0f;
    }

    public float getPlaybackRangeEnd() {
        float start = getPlaybackRangeStart();
        float durationValue = getDuration();
        float end = outPoint != null ? outPoint : durationValue;
        end = Math.min(end, durationValue);
        return Math.max(start, end);
    }

    public void setZoom(float pps) {
        viewportState.setPixelsPerSecond(pps);
    }

    // ========== 坐标转换 ==========
    
    public float screenToTime(float mouseX, float contentX) {
        return screenToTime(mouseX, contentX, viewportState.getPixelsPerSecond());
    }
    
    private float screenToTime(float mouseX, float contentX, float pps) {
        return (mouseX - contentX + viewportState.getScrollX()) / pps;
    }
    
    // ========== 吸附配置 API（Director 统一风格）==========
    
    /**
     * 获取吸附配置对象（UI / 面板可以直接修改这里）
     * 
     * @return 吸附配置对象
     */
    public TimelineSnapSettings getSnapSettings() {
        return snapSettings;
    }

    /**
     * 像素吸附阈值（px）——给工具栏 / 设置面板使用
     * 
     * @return 像素吸附阈值
     */
    public float getSnapPixelThreshold() {
        return snapSettings.getPixelThreshold();
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
        if (snapSettings.isEnabled() == enabled) {
            return;
        }
        snapSettings.setEnabled(enabled);
        markDirty();
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
        setSnappingEnabled(enabled);
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
        float oldValue = snapSettings.getGridInterval();
        snapSettings.setGridInterval(interval);
        if (Math.abs(oldValue - snapSettings.getGridInterval()) >= 0.0001f) {
            markDirty();
        }
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
        notifyModelChanged("track_added"); // ⭐ 通知监听器（如 TimelineCameraController）轨道已添加
        trackPlaybackListener.onTrackAdded(track);
    }
    
    public void removeTrack(TimelineTrack track) {
        tracks.remove(track);
        markDurationDirty(); // 轨道移除可能影响总时长
        notifyModelChanged("track_removed"); // ⭐ 通知监听器轨道已移除
        trackPlaybackListener.onTrackRemoved(track);
    }
    
    public TimelineTrack getTrackAt(int index) {
        if (index >= 0 && index < tracks.size()) {
            return tracks.get(index);
        }
        return null;
    }

    /**
     * 返回轨道在列表中的索引。
     *
     * @return 索引；未找到时返回 -1
     */
    public int getTrackIndex(TimelineTrack track) {
        if (track == null) {
            return -1;
        }
        return tracks.indexOf(track);
    }

    /**
     * 将轨道从 {@code fromIndex} 移动到 {@code toIndex}。
     */
    public void moveTrack(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= tracks.size()
                || toIndex < 0 || toIndex >= tracks.size()
                || fromIndex == toIndex) {
            return;
        }
        TimelineTrack track = tracks.remove(fromIndex);
        tracks.add(toIndex, track);
        notifyModelChanged("track_moved");
    }
    
    // ========== 标记管理 ==========
    
    /**
     * 添加标记（全局标记，不属于任何轨道）
     */
    public void addMarker(TimelineMarker marker) {
        if (marker == null) {
            return;
        }
        markers.add(marker);
        notifyModelChanged("marker_added");
    }
    
    /**
     * 移除标记
     */
    public void removeMarker(java.util.UUID markerId) {
        markers.removeIf(m -> m.getId().equals(markerId));
        notifyModelChanged("marker_removed");
    }
    
    /**
     * 获取所有标记
     */
    public List<TimelineMarker> getMarkers() {
        return new ArrayList<>(markers);
    }

    /**
     * 根据 Actor ID 查找对应的 ActorTrack
     * <p>用于世界点击选中演员时，自动定位到时间线上的对应轨道
     *
     * @param actorId Actor ID
     * @return TimelineTrack，如果未找到则返回 null
     */
    public TimelineTrack findActorTrackByActorId(java.util.UUID actorId) {
        if (actorId == null) {
            return null;
        }

        for (TimelineTrack track : tracks) {
            if (track.getType() == TrackType.ACTOR) {
                com.director.timeline.model.entity.ActorTrack actorTrack = track.getActorTrackPayload();
                if (actorTrack != null) {
                    com.director.actor.ActorRef ref = actorTrack.getActorRef();
                    if (ref != null && actorId.equals(ref.getActorId())) {
                        return track;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 获取轨道数量（语义方法）
     * 
     * @return 轨道数量
     */
    public int getTrackCount() {
        return tracks.size();
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
    
    /**
     * 查找自动化关键帧所在的轨道。
     * 遍历所有轨道查找包含该关键帧的轨道。
     * @param keyframe 要查找的关键帧
     * @return 关键帧所属的轨道，如果未找到则返回 null
     */
    public TimelineTrack findTrackForKeyframe(TimelineSelectable keyframe) {
        if (keyframe == null) {
            return null;
        }
        
        for (TimelineTrack track : tracks) {
            for (TimelineSelectable kf : track.getAutomationKeyframes()) {
                if (kf != null && kf.getId().equals(keyframe.getId())) {
                    return track;
                }
            }
            if (keyframe instanceof com.director.timeline.model.camera.CameraKeyframe cameraKeyframe
                    && track.findCustomPathClipForKeyframe(cameraKeyframe) != null) {
                return track;
            }
        }
        
        return null;
    }

    public com.director.timeline.model.camera.path.CustomPathClip findCustomPathClipForKeyframe(
            com.director.timeline.model.camera.CameraKeyframe keyframe) {
        if (keyframe == null) {
            return null;
        }
        for (TimelineTrack track : tracks) {
            com.director.timeline.model.camera.path.CustomPathClip clip = track.findCustomPathClipForKeyframe(keyframe);
            if (clip != null) {
                return clip;
            }
        }
        return null;
    }

    // ========== Clip 管理（语义方法，Director 风格）==========

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
            if (track.addClip(clip)) {
                markDirty();
                markDurationDirty();
            }
        }
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
     * 
     * <p>⭐ 摄像机模式切换：
     * <ul>
     *   <li>播放开始时通知 TimelineCameraController 进入播放模式</li>
     *   <li>播放停止时通知 TimelineCameraController 停止播放</li>
     * </ul>
     */
    public void setPlaying(boolean playing) {
        boolean wasPlaying = this.playing;
        this.playing = playing;
        
        // ⭐ 关键修复：播放状态改变时通知摄像机控制器
        if (wasPlaying != playing) {
            com.director.timeline.playback.camera.TimelineCameraController controller = 
                com.director.timeline.playback.camera.TimelineCameraController.getInstance();
            
            if (playing) {
                // ⭐ UI 事件驱动：开始播放
                controller.startPlayback();
            } else {
                // ⭐ UI 事件驱动：停止播放
                controller.stopPlayback();
            }
        }
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
     * 获取是否正在拖动播放头（scrubbing）
     */
    public boolean isScrubbing() {
        return this.scrubbing;
    }
    
    /**
     * 设置是否正在拖动播放头（scrubbing）
     * 
     * <p>⭐ UI 状态管理：
     * <ul>
     *   <li>scrubbing 和 playing 应该是互斥的</li>
     *   <li>UI 层负责设置此状态，CameraController 根据此状态控制摄像机</li>
     * </ul>
     */
    public void setScrubbing(boolean scrubbing) {
        boolean wasScrubbing = this.scrubbing;
        if (scrubbing && this.playing) {
            // scrubbing 和 playing 互斥，如果开始 scrubbing，先停止播放
            this.playing = false;
            com.director.timeline.playback.camera.TimelineCameraController.getInstance().onPlaybackStopped();
        }
        this.scrubbing = scrubbing;
        if (wasScrubbing && !scrubbing && !this.playing) {
            com.director.timeline.playback.camera.TimelineCameraController.getInstance().endScrubbing(currentTime);
        }
    }
    
    /**
     * 获取持续时间（取用户设定值和片段最大结束时间中的较大值）
     * 使用缓存机制避免频繁重新计算，提升性能。
     * 这样既能自动适应片段长度，又允许用户手动设置更长的时长
     * 
     * <p>★ 功能：当片段的长度超过预定义的时长时，总时长自动扩张到片段的末尾
     */
    public float getDuration() {
        if (isDurationDirty) {
            this.cachedDynamicDuration = calculateDynamicDuration();
            this.isDurationDirty = false;
            
            // ★ 功能：当动态时长超过用户设定的时长时，自动更新用户设定的时长
            // 这样既能自动适应片段长度，又能保持用户设置
            if (this.cachedDynamicDuration > this.duration) {
                // 自动扩张总时长到片段的末尾
                this.duration = this.cachedDynamicDuration;
            }
        }
        // 返回用户设定的时长（已经自动更新为较大的值）
        return this.duration;
    }
    
    /**
     * 动态计算持续时间：遍历所有轨道和片段，找到最大的结束时间
     * @return 最大的片段结束时间，如果没有片段则返回 0
     */
    private float calculateDynamicDuration() {
        float maxEndTime = 0f;
        
        for (TimelineTrack track : tracks) {
            // 轨道级关键帧（摄像机/天气/时间/季节等）
            if (track != null) {
                for (TimelineSelectable kf : track.getAutomationKeyframes()) {
                    if (kf == null) continue;
                    maxEndTime = Math.max(maxEndTime, kf.getStartTime());
                }
                // 音频事件（音频轨道）
                if (track instanceof com.director.timeline.model.audio.AudioTrack at) {
                    for (com.director.timeline.model.audio.AudioEvent e : at.getEvents()) {
                        if (e == null) continue;
                        maxEndTime = Math.max(maxEndTime, e.getStartTime());
                    }
                } else {
                    for (com.director.timeline.model.audio.AudioEvent e : track.getLegacyAudioEventsForMigration()) {
                        if (e == null) continue;
                        maxEndTime = Math.max(maxEndTime, e.getStartTime());
                    }
                }
            }

            if (track != null) {
                for (com.director.timeline.model.camera.path.CameraPathClip pathClip : track.getCameraPathClips()) {
                    if (pathClip == null) continue;
                    maxEndTime = Math.max(maxEndTime, pathClip.getEndTime());
                    if (pathClip instanceof com.director.timeline.model.camera.path.CustomPathClip customPathClip) {
                        for (com.director.timeline.model.camera.CameraKeyframe keyframe : customPathClip.getKeyframes()) {
                            if (keyframe == null) continue;
                            maxEndTime = Math.max(maxEndTime, keyframe.getTime());
                        }
                    }
                }
                for (TimelineClip clip : track.getClips()) {
                    maxEndTime = Math.max(maxEndTime, clip.getEndTime());
                    // clip 内关键帧（理论上应在 clip 范围内，但这里做兜底）
                    for (com.director.timeline.model.clip.ClipKeyframe kf : clip.getClipKeyframes()) {
                        if (kf == null) continue;
                        maxEndTime = Math.max(maxEndTime, kf.getTime());
                    }
                    for (Float t : clip.getKeyframes()) {
                        if (t == null) continue;
                        maxEndTime = Math.max(maxEndTime, t);
                    }
                }
            }
        }
        
        return maxEndTime;
    }
    
    /**
     * 标记时长需要重新计算（当片段添加/删除/修改时调用）
     * 
     * <p>应该在以下情况下调用：
     * <ul>
     *   <li>片段添加/删除时</li>
     *   <li>片段移动时</li>
     *   <li>片段调整大小时（resize）</li>
     *   <li>片段时间范围改变时</li>
     * </ul>
     */
    public void markDurationDirty() {
        this.isDurationDirty = true;
    }
    
    /**
     * 设置持续时间（用户手动设置）
     * 
     * <p>注意：如果动态计算的时长大于此值，实际时长会使用动态值
     */
    public void setDuration(float duration) { 
        this.duration = Math.max(0.1f, duration); 
        // 标记为需要重新计算，这样下次 getDuration() 时会重新计算并自动扩张
        this.isDurationDirty = true;
    }
    
    public TimelineViewportState getViewportState() {
        return viewportState;
    }

    public float getPixelsPerSecond() { return viewportState.getPixelsPerSecond(); }
    public float getScrollX() { return viewportState.getScrollX(); }
    public void setScrollX(float scrollX) { viewportState.setScrollX(scrollX); }

    
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
     * 批量添加到选择。
     * @param items 要添加的项目
     * @param clearFirst 是否先清空现有选择（框选默认会清空，按住 Ctrl 则不清空）
     */
    public void addItemsToSelection(java.util.Collection<?> items, boolean clearFirst) {
        if (clearFirst) {
            selectedItems.clear();
        }
        if (items == null || items.isEmpty()) {
            if (clearFirst) {
                lastClickedItem = null;
            }
            return;
        }
        Object last = null;
        for (Object it : items) {
            if (it == null) continue;
            selectedItems.add(it);
            last = it;
        }
        if (last != null) {
            lastClickedItem = last;
        }
    }


    /**
     * 清除所有选择
     */
    public void clearSelection() {
        selectedItems.clear();
        lastClickedItem = null;
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

}
