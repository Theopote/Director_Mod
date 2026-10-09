package com.director.timeline.model;

import com.director.core.DirectorServices;
import com.director.timeline.audio.TimelineClipSoundCollector;
import com.director.timeline.layout.TimelineClipLayoutResolver;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.camera.CameraViewportProfile;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CameraPathClip;
import com.director.timeline.model.camera.path.CustomPathClip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线轨道（多类型支持）。
 * 包含多个 TimelineClip，支持容器片段与轨道级关键帧。
 */
public class TimelineTrack {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineTrack.class);

    private static volatile TimelineClipSoundCollector clipSoundCollector = TimelineClipSoundCollector.NOOP;

    public static void setClipSoundCollector(TimelineClipSoundCollector collector) {
        clipSoundCollector = collector != null ? collector : TimelineClipSoundCollector.NOOP;
    }

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
    
    // ========== 实体绑定信息（仅对演员轨道有效）==========
    /** 绑定的演员ID（用于标识这个轨道控制的是哪个演员） */
    private java.util.UUID boundActorId;
    
    /** 实体类型（仅对演员轨道有效） */
    private com.director.timeline.model.entity.EntityKeyframe.EntityType boundEntityType;

    /** 演员显示名称（如 "主角 Steve", "守卫 A"） */
    private String actorDisplayName;

    /** ActorTrack payload（仅对演员轨道有效，用于存储 EntityClip 系统） */
    private com.director.timeline.model.entity.ActorTrack actorTrackPayload;
    
    // ========== 音频事件（仅对音频轨道有效）==========
    private final List<AudioEvent> audioEvents = new ArrayList<>();
    
    // ========== 摄像机属性（仅对摄像机轨道有效）==========
    /** 摄像机视口配置（画幅比例、安全区、预览分辨率等） */
    private CameraViewportProfile cameraViewportProfile;
    
    /** 摄像机路径片段列表（仅对摄像机轨道有效，用于程序化路径生成） */
    private final List<CameraPathClip> cameraPathClips = new ArrayList<>();

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
        this.name = com.director.util.DisplayNames.clean(name);
        this.type = type != null ? type : TrackType.CONTAINER;
    }

    // ========== Clip 管理 ==========

    /**
     * 添加片段到轨道，并自动设置父引用。
     */
    public boolean addClip(TimelineClip clip) {
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
            boolean isAudioClip = clip instanceof com.director.timeline.model.audio.AudioTimelineClip;
            if (!isAudioClip && clip.getBoundContainerId() != null) {
                // 容器片段只允许出现在 CONTAINER 轨道
                if (this.type != TrackType.CONTAINER) {
                    LOGGER.warn("[TimelineTrack] 拒绝添加容器片段到非容器轨道: clip={}, track={}",
                        clip.getLabel(), this.type);
                    return false;
                }

                if (isContainerAlreadyOnOtherTimelineClip(clip)) {
                    LOGGER.warn("[TimelineTrack] 容器 {} 已存在时间线片段，拒绝重复添加: clip={}",
                        clip.getBoundContainerId(), clip.getLabel());
                    return false;
                }
            }
            this.clips.add(clip);
            clip.setParentTrack(this); // 设置父引用，实现 O(1) 反向查找
            // 按开始时间排序，保持时间线有序
            clips.sort(Comparator.comparing(TimelineClip::getStartTime));
            
            // 如果片段绑定了容器：仅当时间线已存在音频轨道时才自动收集声音事件。
            // 没有音频轨道/没有对应事件时，播放应保持静音（由音频轨道事件驱动）。
            if (clip.getBoundContainerId() != null
                    && type == TrackType.CONTAINER) {
                clipSoundCollector.collectAndAddSoundEvents(clip);
            }
            return true;
        }
        return false;
    }

    /**
     * 从轨道移除片段，并清除父引用。
     */
    public void removeClip(TimelineClip clip) {
        if (clip != null && this.clips.remove(clip)) {
            clip.setParentTrack(null); // 清除父引用
        }
    }

    private boolean isContainerAlreadyOnOtherTimelineClip(TimelineClip candidate) {
        UUID containerId = candidate != null ? candidate.getBoundContainerId() : null;
        if (containerId == null) {
            return false;
        }
        var service = DirectorServices.get().containerTimeline();
        for (TimelineClip existing : service.findClipsForContainer(containerId)) {
            if (existing != null && existing != candidate) {
                return true;
            }
        }
        return false;
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

    public boolean hasAutomationKeyframes() {
        return !automationKeyframes.isEmpty();
    }

    // ========== Getters & Setters ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = com.director.util.DisplayNames.clean(name);
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
        if (this instanceof com.director.timeline.model.audio.AudioTrack at) {
            return at.getEvents();
        }
        return Collections.unmodifiableList(audioEvents);
    }

    // ========== ActorTrack payload getter/setter（仅对演员轨道有效）==========

    /**
     * 获取 ActorTrack payload。
     *
     * @return ActorTrack，如果未设置则返回 null
     */
    public com.director.timeline.model.entity.ActorTrack getActorTrackPayload() {
        return actorTrackPayload;
    }

    /**
     * 设置 ActorTrack payload。
     *
     * @param actorTrack ActorTrack
     */
    public void setActorTrackPayload(com.director.timeline.model.entity.ActorTrack actorTrack) {
        this.actorTrackPayload = actorTrack;
    }

    /**
     * 获取或创建 ActorTrack payload（如果不存在则创建）。
     *
     * @return ActorTrack
     */
    public com.director.timeline.model.entity.ActorTrack getOrCreateActorTrackPayload() {
        if (actorTrackPayload == null) {
            String displayName = actorDisplayName != null ? actorDisplayName : name;
            actorTrackPayload = new com.director.timeline.model.entity.ActorTrack(displayName);
            // 同步绑定信息
            if (boundActorId != null) {
                // 注意：ActorTrack 的 actorId 是自动生成的，这里只同步显示名称
            }
            if (boundEntityType != null) {
                // 可以在这里设置 EntityBinding
                com.director.timeline.model.entity.EntityBinding binding = 
                    new com.director.timeline.model.entity.EntityBinding();
                // 根据 boundEntityType 设置 entityTypeId（简化处理）
                binding.setEntityTypeId("minecraft:villager"); // 默认值
                actorTrackPayload.setBinding(binding);
            }
        }
        return actorTrackPayload;
    }

    // ========== 摄像机属性 getter/setter（仅对摄像机轨道有效）==========
    
    /**
     * 获取摄像机视口配置。
     *
     * @return CameraViewportProfile，如果未设置则返回默认值
     */
    public CameraViewportProfile getCameraViewportProfile() {
        if (cameraViewportProfile == null) {
            cameraViewportProfile = new CameraViewportProfile();
        }
        return cameraViewportProfile;
    }
    
    // ========== 摄像机路径片段管理（仅对摄像机轨道有效）==========
    
    /**
     * 获取摄像机路径片段列表
     */
    public List<CameraPathClip> getCameraPathClips() {
        return Collections.unmodifiableList(cameraPathClips);
    }
    
    /**
     * 添加路径片段
     */
    public void addCameraPathClip(CameraPathClip clip) {
        if (clip != null && !cameraPathClips.contains(clip)) {
            cameraPathClips.add(clip);
            // 按开始时间排序
            cameraPathClips.sort(java.util.Comparator.comparingDouble(CameraPathClip::getStartTime));
        }
    }
    
    /**
     * 移除路径片段
     */
    public void removeCameraPathClip(CameraPathClip clip) {
        cameraPathClips.remove(clip);
    }
    
    /**
     * 查找指定时间点激活的路径片段
     */
    public CameraPathClip findActivePathClip(float timeSeconds) {
        for (CameraPathClip clip : cameraPathClips) {
            if (clip.isActiveAt(timeSeconds)) {
                return clip;
            }
        }
        return null;
    }

    /**
     * 查找指定时间点可写入关键帧的自定义路径片段。
     */
    public CustomPathClip findCustomPathClipAt(float timeSeconds) {
        for (CameraPathClip clip : cameraPathClips) {
            if (clip instanceof CustomPathClip custom && custom.isActiveAt(timeSeconds)) {
                return custom;
            }
        }
        return null;
    }

    public CustomPathClip findCustomPathClipForKeyframe(CameraKeyframe keyframe) {
        if (keyframe == null) {
            return null;
        }
        for (CameraPathClip clip : cameraPathClips) {
            if (clip instanceof CustomPathClip custom && custom.containsKeyframe(keyframe)) {
                return custom;
            }
        }
        return null;
    }
    
    /**
     * 检查是否有路径片段
     */
    public boolean hasCameraPathClips() {
        return !cameraPathClips.isEmpty();
    }
    
    /**
     * ⭐ 检查 Path Clip 是否与其他 Path Clip 重叠（用于拖动/拉伸时的约束检查）
     * 
     * @param start 开始时间
     * @param end 结束时间
     * @param exclude 要排除的 Path Clip（通常是正在拖动的那个）
     * @return 如果存在重叠则返回 true
     */
    public boolean hasCameraPathOverlap(float start, float end, CameraPathClip exclude) {
        for (CameraPathClip clip : cameraPathClips) {
            if (clip == null || clip == exclude || !clip.isEnabled()) {
                continue;
            }
            float clipStart = clip.getStartTime();
            float clipEnd = clip.getEndTime();
            // 检查是否有重叠：end > clipStart && start < clipEnd
            if (end > clipStart && start < clipEnd) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查容器/演员/音频等 {@link TimelineClip} 是否与其他片段重叠。
     */
    public boolean hasClipOverlap(float start, float end, TimelineClip exclude) {
        for (TimelineClip clip : clips) {
            if (clip == null || clip == exclude) {
                continue;
            }
            if (end > clip.getStartTime() && start < clip.getEndTime()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析不重叠的片段起点（就近贴在相邻片段前/后）。
     */
    public float resolveClipStartTime(float candidateStart, float length, TimelineClip exclude) {
        return TimelineClipLayoutResolver.resolveNearestStart(
                TimelineClipLayoutResolver.clipIntervalsOnTrack(this, exclude),
                candidateStart,
                length,
                0f);
    }

    /**
     * 解析不重叠的 Path Clip 起点。
     */
    public float resolvePathClipStartTime(float candidateStart, float length, CameraPathClip exclude) {
        return TimelineClipLayoutResolver.resolveNearestStart(
                TimelineClipLayoutResolver.pathClipIntervalsOnTrack(this, exclude),
                candidateStart,
                length,
                0f);
    }

}
