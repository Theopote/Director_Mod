package com.chronoblocks.ui.timeline.model;

import com.chronoblocks.ui.timeline.model.audio.AudioEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 时间线轨道（多类型支持）。
 * 包含多个 TimelineClip，支持容器片段与轨道级关键帧。
 */
public class TimelineTrack {
    private String name;
    private TrackType type;  // 轨道类型
    private final List<TimelineClip> clips = new ArrayList<>();
    private final List<TimelineSelectable> automationKeyframes = new ArrayList<>();
    private boolean locked = false;
    private boolean visible = true;
    private int zOrder = 0;  // Z 轴排序
    private boolean muted = false;  // 静音（对音频轨道有效）
    private boolean solo = false;   // 独奏（只播放此轨道）
    private boolean collapsed = false;  // 折叠状态（用于 UI 显示）
    private String boundContainerName;
    private String boundContainerId;
    
    // ========== 音频事件（仅对音频轨道有效）==========
    private final List<AudioEvent> audioEvents = new ArrayList<>();

    /**
     * 构造函数（默认为容器轨道）。
     */
    public TimelineTrack(String name) {
        this(name, TrackType.CONTAINER);
    }

    /**
     * 构造函数（指定轨道类型）。
     */
    public TimelineTrack(String name, TrackType type) {
        this.name = name;
        this.type = type != null ? type : TrackType.CONTAINER;
    }

    // ========== Clip 管理 ==========

    /**
     * 添加片段到轨道，并自动设置父引用。
     */
    public void addClip(TimelineClip clip) {
        if (clip != null && !this.clips.contains(clip)) {
            this.clips.add(clip);
            clip.setParentTrack(this); // 设置父引用，实现 O(1) 反向查找
            // 按开始时间排序，保持时间线有序
            clips.sort(Comparator.comparing(TimelineClip::getStartTime));
            
            // 如果片段绑定了容器：仅当时间线已存在音频轨道时才自动收集声音事件。
            // 没有音频轨道/没有对应事件时，播放应保持静音（由音频轨道事件驱动）。
            if (clip.getBoundContainerId() != null
                    && (type == TrackType.CONTAINER || type == TrackType.ERASE)) {
                try {
                    com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                            .collectAndAddSoundEvents(clip);
                } catch (Exception e) {
                    // 如果服务未初始化，忽略错误（向后兼容）
                    // 正常情况下服务应该在 TimelinePanel 初始化时注册
                }
            }
        }
    }

    /**
     * 从轨道移除片段，并清除父引用。
     */
    public void removeClip(TimelineClip clip) {
        if (clip != null && this.clips.remove(clip)) {
            clip.setParentTrack(null); // 清除父引用
        }
    }

    /**
     * 根据索引移除片段，并清除父引用。
     */
    public void removeClipAt(int index) {
        if (index >= 0 && index < clips.size()) {
            TimelineClip clip = clips.remove(index);
            if (clip != null) {
                clip.setParentTrack(null); // 清除父引用
            }
        }
    }

    /**
     * 查找指定时间点上的片段。
     *
     * @param time 时间点（秒）
     * @return 该时间点上的片段，如果没有则返回 null
     */
    public TimelineClip findClipAtTime(float time) {
        for (TimelineClip clip : clips) {
            if (time >= clip.getStartTime() && time <= clip.getEndTime()) {
                return clip;
            }
        }
        return null;
    }

    public int getClipIndex(TimelineClip clip) {
        return clips.indexOf(clip);
    }

    /**
     * 查找指定时间范围内的片段。
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
     * 检查时间范围是否有重叠的片段。
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

    // ========== 轨道级关键帧（摄像机、天气、标记等）==========

    /**
     * 添加轨道级关键帧。
     */
    public void addAutomationKeyframe(TimelineSelectable keyframe) {
        if (keyframe == null) {
            return;
        }
        automationKeyframes.removeIf(k -> k.getId().equals(keyframe.getId()));
        automationKeyframes.add(keyframe);
        automationKeyframes.sort(Comparator.comparing(TimelineSelectable::getStartTime));
    }

    /**
     * 移除轨道级关键帧。
     */
    public boolean removeAutomationKeyframe(UUID keyframeId) {
        if (keyframeId == null) {
            return false;
        }
        return automationKeyframes.removeIf(k -> keyframeId.equals(k.getId()));
    }

    /**
     * 获取轨道上的所有关键帧。
     */
    public List<TimelineSelectable> getAutomationKeyframes() {
        return Collections.unmodifiableList(automationKeyframes);
    }

    /**
     * 查找时间附近的关键帧，方便吸附或 UI 高亮。
     */
    public TimelineSelectable findAutomationKeyframeNear(float time, float tolerance) {
        for (TimelineSelectable selectable : automationKeyframes) {
            if (Math.abs(selectable.getStartTime() - time) <= tolerance) {
                return selectable;
            }
        }
        return null;
    }

    public boolean hasAutomationKeyframes() {
        return !automationKeyframes.isEmpty();
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

    public TrackType getType() {
        return type;
    }

    public void setType(TrackType type) {
        this.type = type != null ? type : TrackType.CONTAINER;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isSolo() {
        return solo;
    }

    public void setSolo(boolean solo) {
        this.solo = solo;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public String getBoundContainerName() {
        return boundContainerName;
    }

    public void setBoundContainerName(String boundContainerName) {
        this.boundContainerName = (boundContainerName == null || boundContainerName.isBlank())
            ? null
            : boundContainerName.trim();
    }

    public String getBoundContainerId() {
        return boundContainerId;
    }

    public void setBoundContainerId(String boundContainerId) {
        this.boundContainerId = (boundContainerId == null || boundContainerId.isBlank())
            ? null
            : boundContainerId.trim();
    }

    // ========== 音频事件管理（向后兼容，推荐使用 AudioTrack）==========

    /**
     * 添加音频事件到轨道（向后兼容方法）
     * 
     * @deprecated 推荐使用 AudioTrack.addEvent() 方法
     */
    @Deprecated
    public void addAudioEvent(AudioEvent event) {
        // 如果这是 AudioTrack，使用 AudioTrack 的方法
        if (this instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack) {
            ((com.chronoblocks.ui.timeline.model.audio.AudioTrack) this).addEvent(event);
            return;
        }
        
        // 向后兼容：对于普通 TimelineTrack，使用旧的方式
        if (event != null && !audioEvents.contains(event)) {
            audioEvents.add(event);
            audioEvents.sort(Comparator.comparing(AudioEvent::getTime));
        }
    }

    /**
     * 移除音频事件（向后兼容方法）
     * 
     * @deprecated 推荐使用 AudioTrack.removeEvent() 方法
     */
    @Deprecated
    public boolean removeAudioEvent(UUID eventId) {
        // 如果这是 AudioTrack，使用 AudioTrack 的方法
        if (this instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack audioTrack) {
            AudioEvent event = audioTrack.getEventById(eventId);
            if (event != null) {
                audioTrack.removeEvent(event);
                return true;
            }
            return false;
        }
        
        // 向后兼容
        if (eventId == null) {
            return false;
        }
        return audioEvents.removeIf(e -> e != null && e.getId().equals(eventId));
    }

    /**
     * 获取所有音频事件（向后兼容方法）
     * 
     * @deprecated 推荐使用 AudioTrack.getEvents() 方法
     */
    @Deprecated
    public List<AudioEvent> getAudioEvents() {
        // 如果这是 AudioTrack，使用 AudioTrack 的方法
        if (this instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack) {
            return ((com.chronoblocks.ui.timeline.model.audio.AudioTrack) this).getEvents();
        }
        
        // 向后兼容
        return Collections.unmodifiableList(audioEvents);
    }

    /**
     * 获取旧版 TimelineTrack 内部维护的音频事件列表（仅用于数据迁移/向后兼容）。
     *
     * <p>注意：当轨道为 {@code AudioTrack} 时，应使用 {@code AudioTrack.getEvents()}。
     * 此方法仅用于在“尚未转换为 AudioTrack”的旧数据上读取事件，避免外部代码调用已弃用的 {@link #getAudioEvents()}。
     *
     * @return 不可修改的旧音频事件列表
     */
    public List<AudioEvent> getLegacyAudioEventsForMigration() {
        if (this instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack at) {
            return at.getEvents();
        }
        return Collections.unmodifiableList(audioEvents);
    }

    /**
     * 查找指定时间范围内的音频事件（向后兼容方法）
     * 
     * @deprecated 推荐使用 AudioTrack.getEventsInRange() 方法
     */
    @Deprecated
    public List<AudioEvent> getAudioEventsInRange(float startTime, float endTime) {
        // 如果这是 AudioTrack，使用 AudioTrack 的方法
        if (this instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack) {
            return ((com.chronoblocks.ui.timeline.model.audio.AudioTrack) this).getEventsInRange(startTime, endTime);
        }
        
        // 向后兼容
        List<AudioEvent> result = new ArrayList<>();
        for (AudioEvent event : audioEvents) {
            float eventTime = event.getTime();
            if (eventTime >= startTime && eventTime <= endTime) {
                result.add(event);
            }
        }
        return result;
    }

    /**
     * 清空所有音频事件（向后兼容方法）
     * 
     * @deprecated 推荐使用 AudioTrack.clearEvents() 方法
     */
    @Deprecated
    public void clearAudioEvents() {
        // 如果这是 AudioTrack，使用 AudioTrack 的方法
        if (this instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack) {
            ((com.chronoblocks.ui.timeline.model.audio.AudioTrack) this).clearEvents();
            return;
        }
        
        // 向后兼容
        audioEvents.clear();
    }

    /**
     * 检查是否有音频事件（向后兼容方法）
     * 
     * @deprecated 推荐使用 AudioTrack.hasEvents() 方法
     */
    @Deprecated
    public boolean hasAudioEvents() {
        // 如果这是 AudioTrack，使用 AudioTrack 的方法
        if (this instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack) {
            return ((com.chronoblocks.ui.timeline.model.audio.AudioTrack) this).hasEvents();
        }
        
        // 向后兼容
        return !audioEvents.isEmpty();
    }
}
