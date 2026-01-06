package com.chronoblocks.ui.timeline.model;

import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

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
    
    // ========== 实体绑定信息（仅对实体轨道有效）==========
    /** 绑定的演员ID（用于标识这个轨道控制的是哪个演员） */
    private java.util.UUID boundActorId;
    
    /** 实体类型（仅对实体轨道有效） */
    private com.chronoblocks.ui.timeline.model.entity.EntityKeyframe.EntityType boundEntityType;
    
    /** 演员显示名称（如 "主角 Steve", "守卫 A"） */
    private String actorDisplayName;
    
    /** 是否跨剪辑存在（persistent = true 表示实体在轨道外的时间段也存在） */
    private boolean actorPersistent = false;
    
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
            // ✅ 轨道类型约束（防止“拆除容器/清除片段”被拖到其他轨道）
            // - DEMOLITION 容器只能在 ERASE 轨道
            // - 非 DEMOLITION 容器不能进 ERASE 轨道
            // 说明：这里做成“模型层硬规则”，避免任何交互路径（拖拽/粘贴/命令）绕过限制。
            // 注意：该约束只应作用于“容器类轨道”（CONTAINER/ERASE），不能误伤 AUDIO 轨道上的 AudioTimelineClip。
            // 额外规则：普通容器片段只能在 CONTAINER 轨道，拆除容器片段只能在 ERASE 轨道；
            // 且“容器片段”不允许被拖到其它轨道（相机/天气/时间等）。
            //
            // 重要：AudioTimelineClip 也可能绑定容器（用于音效同步），不能被当成容器片段处理。
            boolean isAudioClip = clip instanceof com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip;
            if (!isAudioClip && clip.getBoundContainerId() != null) {
                // 容器片段只允许出现在 CONTAINER/ERASE 两类轨道
                if (this.type != TrackType.CONTAINER && this.type != TrackType.ERASE) {
                    return;
                }
            }

            if ((this.type == TrackType.CONTAINER || this.type == TrackType.ERASE) && !isAudioClip && clip.getBoundContainerId() != null) {
                try {
                    Container c = ContainerManager.getInstance() != null
                            ? ContainerManager.getInstance().getContainer(clip.getBoundContainerId())
                            : null;
                    if (c != null) {
                        boolean isDemolition = (c.getType() == com.chronoblocks.core.container.ContainerType.DEMOLITION);
                        if (this.type == TrackType.ERASE && !isDemolition) {
                            return; // 拒绝：清除轨道只允许拆除容器
                        }
                        if (this.type != TrackType.ERASE && isDemolition) {
                            return; // 拒绝：拆除容器只能在清除轨道
                        }
                    }
                } catch (Exception ignored) {
                    // 如果容器系统不可用，宁可不拦截（保持向后兼容）
                }
            }
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

    /**
     * 获取旧版 TimelineTrack 内部维护的音频事件列表（仅用于数据迁移/向后兼容）。
     *
     * <p>注意：当轨道为 {@code AudioTrack} 时，应使用 {@code AudioTrack.getEvents()}。
     * 此方法仅用于在"尚未转换为 AudioTrack"的旧数据上读取事件。
     *
     * @return 不可修改的旧音频事件列表
     */
    public List<AudioEvent> getLegacyAudioEventsForMigration() {
        if (this instanceof com.chronoblocks.ui.timeline.model.audio.AudioTrack at) {
            return at.getEvents();
        }
        return Collections.unmodifiableList(audioEvents);
    }

    // ========== 实体绑定信息 getter/setter（仅对实体轨道有效）==========

    /**
     * 获取绑定的演员ID。
     *
     * @return 演员ID，如果未绑定则返回 null
     */
    public java.util.UUID getBoundActorId() {
        return boundActorId;
    }

    /**
     * 设置绑定的演员ID。
     *
     * @param boundActorId 演员ID
     */
    public void setBoundActorId(java.util.UUID boundActorId) {
        this.boundActorId = boundActorId;
    }

    /**
     * 获取实体类型。
     *
     * @return 实体类型，如果未设置则返回 null
     */
    public com.chronoblocks.ui.timeline.model.entity.EntityKeyframe.EntityType getBoundEntityType() {
        return boundEntityType;
    }

    /**
     * 设置实体类型。
     *
     * @param boundEntityType 实体类型
     */
    public void setBoundEntityType(com.chronoblocks.ui.timeline.model.entity.EntityKeyframe.EntityType boundEntityType) {
        this.boundEntityType = boundEntityType;
    }

    /**
     * 获取演员显示名称。
     *
     * @return 显示名称，如果未设置则返回 null
     */
    public String getActorDisplayName() {
        return actorDisplayName;
    }

    /**
     * 设置演员显示名称。
     *
     * @param actorDisplayName 显示名称
     */
    public void setActorDisplayName(String actorDisplayName) {
        this.actorDisplayName = (actorDisplayName == null || actorDisplayName.isBlank())
            ? null
            : actorDisplayName.trim();
    }

    /**
     * 检查演员是否跨剪辑存在。
     *
     * @return 如果 persistent 则返回 true
     */
    public boolean isActorPersistent() {
        return actorPersistent;
    }

    /**
     * 设置演员是否跨剪辑存在。
     *
     * @param actorPersistent 是否跨剪辑存在
     */
    public void setActorPersistent(boolean actorPersistent) {
        this.actorPersistent = actorPersistent;
    }

    /**
     * 检查是否绑定了演员。
     *
     * @return 如果绑定了演员则返回 true
     */
    public boolean hasBoundActor() {
        return boundActorId != null;
    }
}
