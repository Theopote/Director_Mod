package com.director.timeline.container;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 容器和时间线之间的关联服务
 * 用于查询容器在时间线上的片段信息
 */
public class ContainerTimelineService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerTimelineService.class);
    private static ContainerTimelineService instance;
    private TimelineModel timelineModel;
    private ContainerClipAudioSync audioSync;
    /** 原始重复 UUID -> 所有恢复目标（恢复后 UUID -> 容器名）。 */
    private Map<UUID, Map<UUID, String>> recoveryTargets = Map.of();

    private ContainerTimelineService() {
    }

    /**
     * 在模组启动时显式初始化（幂等）。由 {@link com.director.core.DirectorServices} 统一调用。
     */
    public static void initialize() {
        if (instance != null) {
            return;
        }
        synchronized (ContainerTimelineService.class) {
            if (instance == null) {
                instance = new ContainerTimelineService();
            }
        }
    }

    /**
     * @throws IllegalStateException 若尚未初始化
     * @deprecated 新代码请使用 {@link com.director.core.DirectorServices#get()}{@code .containerTimeline()}
     */
    @Deprecated
    public static ContainerTimelineService getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "[ContainerTimelineService] Not initialized — call DirectorServices.initialize() during mod startup");
        }
        return instance;
    }

    /** 供 {@link com.director.core.DirectorServices} 启动装配；业务代码请用 {@link com.director.core.DirectorServices#get()}{@code .containerTimeline()}。 */
    @ApiStatus.Internal
    public static ContainerTimelineService requireInitialized() {
        if (instance == null) {
            throw new IllegalStateException(
                    "[ContainerTimelineService] Not initialized — call DirectorServices.initialize() during mod startup");
        }
        return instance;
    }

    /**
     * 设置时间线模型（由TimelinePanel初始化时调用）
     */
    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
        remapContainerReferences(true);
    }

    /**
     * 替换当前世界的重复 UUID 恢复映射。容器加载期间不标记时间线脏，避免覆盖旧世界时间线存档。
     */
    public void setContainerRecoveryMapping(Map<UUID, Map<UUID, String>> recoveryTargets) {
        this.recoveryTargets = copyRecoveryTargets(recoveryTargets);
        remapContainerReferences(false);
    }

    /**
     * 合并管理器在挂载阶段发现的重复 UUID，并立即修复当前时间线引用。
     */
    public void registerContainerRecoveryTargets(Map<UUID, Map<UUID, String>> recoveryTargets) {
        if (recoveryTargets == null || recoveryTargets.isEmpty()) {
            return;
        }
        Map<UUID, Map<UUID, String>> merged = new LinkedHashMap<>();
        this.recoveryTargets.forEach((id, targets) -> merged.put(id, new LinkedHashMap<>(targets)));
        for (Map.Entry<UUID, Map<UUID, String>> entry : recoveryTargets.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().size() < 2) {
                continue;
            }
            Map<UUID, String> targets = merged.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>());
            targets.putAll(entry.getValue());
        }
        this.recoveryTargets = Collections.unmodifiableMap(merged);
        remapContainerReferences(true);
    }

    public void clearContainerRecoveryMapping() {
        recoveryTargets = Map.of();
    }

    /** 供容器存档持久化恢复映射，避免容器修复落盘后、时间线迁移前进程退出丢失别名。 */
    @ApiStatus.Internal
    public Map<UUID, Map<UUID, String>> getContainerRecoveryMapping() {
        return copyRecoveryTargets(recoveryTargets);
    }

    /**
     * 将重复 UUID 的容器片段、音频片段和轨道级绑定统一迁移到恢复后的 ID。
     * <p>只按绑定名做唯一匹配；名称缺失、过期或有重名时解除绑定并告警，避免静默指向错误容器。</p>
     *
     * @return 发生迁移或隔离的引用数量
     */
    public int remapContainerReferences() {
        return remapContainerReferences(true);
    }

    private int remapContainerReferences(boolean markDirty) {
        if (timelineModel == null || recoveryTargets.isEmpty()) {
            return 0;
        }
        int changedReferences = 0;
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (clip == null || clip.getBoundContainerId() == null
                        || !recoveryTargets.containsKey(clip.getBoundContainerId())) {
                    continue;
                }
                UUID originalId = clip.getBoundContainerId();
                RecoveryTarget target = findUniqueTarget(originalId, clip.getBoundContainerName());
                if (target == null) {
                    LOGGER.warn("[ContainerTimelineService] 片段 {} 的容器绑定 {} 无法唯一确定，已解除绑定",
                        clip.getLabel(), originalId);
                    clip.setBoundContainerId(null);
                    clip.setBoundContainerName(null);
                    changedReferences++;
                } else if (!originalId.equals(target.id())
                        || !Objects.equals(clip.getBoundContainerName(), target.name())) {
                    clip.setBoundContainerId(target.id());
                    clip.setBoundContainerName(target.name());
                    LOGGER.warn("[ContainerTimelineService] 片段 {} 的容器绑定已重映射: {} -> {} ({})",
                        clip.getLabel(), originalId, target.id(), target.name());
                    changedReferences++;
                }
            }

            String boundId = track.getBoundContainerId();
            if (boundId == null || boundId.isBlank()) {
                continue;
            }
            UUID originalId;
            try {
                originalId = UUID.fromString(boundId);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!recoveryTargets.containsKey(originalId)) {
                continue;
            }
            RecoveryTarget target = findUniqueTarget(originalId, track.getBoundContainerName());
            if (target == null) {
                LOGGER.warn("[ContainerTimelineService] 轨道 {} 的容器绑定 {} 无法唯一确定，已解除绑定",
                    track.getName(), originalId);
                track.setBoundContainerId(null);
                track.setBoundContainerName(null);
                changedReferences++;
            } else if (!originalId.equals(target.id())
                    || !Objects.equals(track.getBoundContainerName(), target.name())) {
                track.setBoundContainerId(target.id().toString());
                track.setBoundContainerName(target.name());
                LOGGER.warn("[ContainerTimelineService] 轨道 {} 的容器绑定已重映射: {} -> {} ({})",
                    track.getName(), originalId, target.id(), target.name());
                changedReferences++;
            }
        }
        if (changedReferences > 0 && markDirty) {
            timelineModel.markDirty();
        }
        return changedReferences;
    }

    private RecoveryTarget findUniqueTarget(UUID originalId, String boundName) {
        Map<UUID, String> targets = recoveryTargets.get(originalId);
        if (targets == null || boundName == null || boundName.isBlank()) {
            return null;
        }
        String expectedName = boundName.trim();
        RecoveryTarget match = null;
        for (Map.Entry<UUID, String> entry : targets.entrySet()) {
            if (!expectedName.equals(entry.getValue())) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = new RecoveryTarget(entry.getKey(), entry.getValue());
        }
        return match;
    }

    private static Map<UUID, Map<UUID, String>> copyRecoveryTargets(
            Map<UUID, Map<UUID, String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<UUID, String>> copy = new LinkedHashMap<>();
        source.forEach((originalId, targets) -> {
            if (originalId != null && targets != null && targets.size() >= 2) {
                copy.put(originalId, Collections.unmodifiableMap(new LinkedHashMap<>(targets)));
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private record RecoveryTarget(UUID id, String name) {}

    /**
     * 注册音频同步端口（由客户端 TimelinePanel 初始化时调用）。
     */
    public void setAudioSync(ContainerClipAudioSync audioSync) {
        this.audioSync = audioSync;
    }

    /**
     * 获取当前绑定的时间线模型（可能为 null）
     */
    public TimelineModel getTimelineModel() {
        return timelineModel;
    }

    /**
     * 检查容器是否在时间线上（是否有对应的片段）
     * 
     * @param containerId 容器ID
     * @return 如果容器在时间线上返回true，否则返回false
     */
    public boolean isContainerOnTimeline(UUID containerId) {
        if (timelineModel == null || containerId == null) {
            return false;
        }

        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null || track.getType() != TrackType.CONTAINER) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (containerId.equals(clip.getBoundContainerId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<UUID> getContainersOnTimeline() {
        if (timelineModel == null) {
            return Set.of();
        }
        Set<UUID> containerIds = new HashSet<>();
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null || track.getType() != TrackType.CONTAINER) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                UUID containerId = clip.getBoundContainerId();
                if (containerId != null) {
                    containerIds.add(containerId);
                }
            }
        }
        return containerIds;
    }

    /**
     * 查找容器在时间线上的所有片段
     * 
     * @param containerId 容器ID
     * @return 该容器对应的所有片段列表
     */
    public List<TimelineClip> findClipsForContainer(UUID containerId) {
        List<TimelineClip> clips = new ArrayList<>();

        if (timelineModel == null || containerId == null) {
            return clips;
        }

        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type != TrackType.CONTAINER) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (containerId.equals(clip.getBoundContainerId())) {
                    clips.add(clip);
                }
            }
        }

        return clips;
    }

    public Set<TimelineTrack> findTracksForContainer(UUID containerId) {
        Set<TimelineTrack> tracks = new HashSet<>();

        if (timelineModel == null || containerId == null) {
            return tracks;
        }

        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type != TrackType.CONTAINER) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (containerId.equals(clip.getBoundContainerId())) {
                    tracks.add(track);
                    break;
                }
            }
        }

        return tracks;
    }

    public int removeClipsForContainer(UUID containerId) {
        if (timelineModel == null || containerId == null) {
            return 0;
        }

        List<TimelineClip> clipsToRemove = new ArrayList<>();
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type != TrackType.CONTAINER) {
                // 音频轨道等不直接按 containerId 清理 clip：由音频同步端口统一处理
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (containerId.equals(clip.getBoundContainerId())) {
                    clipsToRemove.add(clip);
                }
            }
        }

        int count = 0;
        for (TimelineClip clip : clipsToRemove) {
            if (timelineModel.getSelectedClip() == clip) {
                timelineModel.setSelectedClip(null);
            }
            if (audioSync != null) {
                audioSync.onClipRemoved(clip);
            }
            TimelineTrack track = timelineModel.findTrackForClip(clip);
            if (track != null) {
                track.removeClip(clip);
            }
            count++;
        }

        if (count > 0) {
            timelineModel.markDirty();
            timelineModel.markDurationDirty();
            invalidateClipBlockController();
        }

        return count;
    }

    /** 片段删除后立即让播放/预览缓存失效，不能等待模型监听间接刷新。 */
    private void invalidateClipBlockController() {
        try {
            com.director.timeline.playback.block.TimelineClipBlockController.getInstance().invalidateState();
        } catch (Exception ex) {
            LOGGER.warn("[ContainerTimelineService] 失效片段方块控制器失败: {}", ex.getMessage());
        }
    }

    /** 供释放/清空命令在撤销删除后统一刷新模型与播放缓存。 */
    public void invalidateAfterClipRestore() {
        if (timelineModel == null) {
            return;
        }
        timelineModel.markDirty();
        timelineModel.markDurationDirty();
        invalidateClipBlockController();
    }

    /** 供释放/清空命令在删除前捕获音频恢复状态。 */
    public Runnable captureAudioRemovalState(TimelineClip clip) {
        return audioSync != null ? audioSync.captureRemovalState(clip) : () -> { };
    }

    public void updateClipNamesForContainer(UUID containerId, String newName) {
        if (timelineModel == null || containerId == null || newName == null) {
            return;
        }

        boolean changed = false;
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type != TrackType.CONTAINER) {
                // 音频轨道的“音频片段”会有独立的显示名（例如带 🔊 前缀），不应被容器重命名逻辑覆盖
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (!containerId.equals(clip.getBoundContainerId())) {
                    continue;
                }
                boolean clipChanged = false;
                if (!newName.equals(clip.getLabel())) {
                    clip.setLabel(newName);
                    clipChanged = true;
                }
                if (!newName.equals(clip.getBoundContainerName())) {
                    clip.setBoundContainerName(newName);
                    clipChanged = true;
                }
                if (clipChanged) {
                    changed = true;
                }
            }
        }

        if (audioSync != null) {
            audioSync.updateLinkedAudioClipLabel(containerId, newName);
        }

        if (changed) {
            timelineModel.markDirty();
        }
    }
}
