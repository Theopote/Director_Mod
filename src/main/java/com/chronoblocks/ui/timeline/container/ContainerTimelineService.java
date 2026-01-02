package com.chronoblocks.ui.timeline.container;

import com.chronoblocks.ui.timeline.audio.TimelineAudioService;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;

import java.util.*;

/**
 * 容器和时间线之间的关联服务
 * 用于查询容器在时间线上的片段信息
 */
public class ContainerTimelineService {
    private static ContainerTimelineService instance;
    private TimelineModel timelineModel;

    private ContainerTimelineService() {
    }

    /**
     * 获取单例实例
     */
    public static ContainerTimelineService getInstance() {
        if (instance == null) {
            instance = new ContainerTimelineService();
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
            if (type != TrackType.CONTAINER && type != TrackType.ERASE) {
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
            if (type != TrackType.CONTAINER && type != TrackType.ERASE) {
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
            for (TimelineClip clip : track.getClips()) {
                if (containerId.equals(clip.getBoundContainerId())) {
                    clipsToRemove.add(clip);
                }
            }
        }

        TimelineAudioService audioService = TimelineAudioService.getInstance();
        int count = 0;
        for (TimelineClip clip : clipsToRemove) {
            audioService.onClipRemoved(clip);
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

        if (changed) {
            timelineModel.markDirty();
        }
    }
}
