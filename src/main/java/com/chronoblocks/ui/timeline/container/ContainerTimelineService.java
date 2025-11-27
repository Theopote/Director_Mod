package com.chronoblocks.ui.timeline.container;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

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

        // 遍历所有轨道和片段
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            for (TimelineClip clip : track.getClips()) {
                if (containerId.equals(clip.getBoundContainerId())) {
                    clips.add(clip);
                }
            }
        }

        return clips;
    }

    /**
     * 查找包含指定容器ID片段的所有轨道
     * 
     * @param containerId 容器ID
     * @return 包含该容器片段的轨道集合
     */
    public Set<TimelineTrack> findTracksForContainer(UUID containerId) {
        Set<TimelineTrack> tracks = new HashSet<>();
        
        if (timelineModel == null || containerId == null) {
            return tracks;
        }

        // 遍历所有轨道和片段
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            for (TimelineClip clip : track.getClips()) {
                if (containerId.equals(clip.getBoundContainerId())) {
                    tracks.add(track);
                    break; // 该轨道已包含该容器，继续下一个轨道
                }
            }
        }

        return tracks;
    }

    /**
     * 删除容器在时间线上的所有片段
     * 
     * @param containerId 容器ID
     * @return 被删除的片段数量
     */
    public int removeClipsForContainer(UUID containerId) {
        if (timelineModel == null || containerId == null) {
            return 0;
        }

        List<TimelineClip> clipsToRemove = findClipsForContainer(containerId);
        int count = 0;

        for (TimelineClip clip : clipsToRemove) {
            TimelineTrack track = timelineModel.findTrackForClip(clip);
            if (track != null) {
                track.removeClip(clip);
                count++;
            }
        }

        if (count > 0) {
            timelineModel.markDirty();
        }

        return count;
    }

    /**
     * 更新容器在时间线上的所有片段的名称
     * 
     * @param containerId 容器ID
     * @param newName 新的容器名称
     */
    public void updateClipNamesForContainer(UUID containerId, String newName) {
        if (timelineModel == null || containerId == null || newName == null) {
            return;
        }

        List<TimelineClip> clips = findClipsForContainer(containerId);
        for (TimelineClip clip : clips) {
            // 只有当片段名称与容器名称不一致时才更新，避免循环更新
            if (!newName.equals(clip.getLabel())) {
                clip.setLabel(newName);
                clip.setBoundContainerName(newName);
            }
        }

        if (!clips.isEmpty()) {
            timelineModel.markDirty();
        }
    }
}

