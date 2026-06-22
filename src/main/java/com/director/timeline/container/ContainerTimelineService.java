package com.director.timeline.container;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;

import java.util.*;

/**
 * 容器和时间线之间的关联服务
 * 用于查询容器在时间线上的片段信息
 */
public class ContainerTimelineService {
    private static ContainerTimelineService instance;
    private TimelineModel timelineModel;
    private ContainerClipAudioSync audioSync;

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

    /** 供框架内部使用的非废弃入口。 */
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
    }

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

        return !findClipsForContainer(containerId).isEmpty();
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
        }

        return count;
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
                if (!newName.equals(clip.getLabel())) {
                    clip.setLabel(newName);
                    clip.setBoundContainerName(newName);
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
