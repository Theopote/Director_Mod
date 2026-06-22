package com.director.ui.timeline.commands;

import com.director.core.DirectorServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TrackType;

import java.util.UUID;

/**
 * 绑定/解绑片段到容器的命令。
 */
public class BindClipToContainerCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BindClipToContainerCommand.class);

    private final TimelineClip clip;
    private final UUID newContainerId;
    private final String newContainerName;
    private UUID oldContainerId;
    private String oldContainerName;

    public BindClipToContainerCommand(
            TimelineModel model,
            TimelineClip clip,
            UUID newContainerId,
            String newContainerName
    ) {
        super(model, "clip_bind_container");
        this.clip = clip;
        this.newContainerId = newContainerId;
        this.newContainerName = newContainerName;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            return;
        }
        oldContainerId = clip.getBoundContainerId();
        oldContainerName = clip.getBoundContainerName();
        if (!canBind(newContainerId)) {
            LOGGER.warn("[Timeline] 拒绝绑定片段 {} 到容器 {}：轨道类型不匹配或容器已有片段",
                clip.getLabel(), newContainerId);
            return;
        }
        clip.bindToContainer(newContainerId, newContainerName);
        if (clip instanceof com.director.timeline.model.audio.AudioTimelineClip) {
            com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                    .onAudioClipBindingChanged(clip, oldContainerId, newContainerId);
        }
        LOGGER.debug("[Timeline] 片段 {} 绑定容器: {} ({})",
                clip.getLabel(),
                newContainerName,
                newContainerId);
    }

    @Override
    protected void doUndo() {
        if (clip == null) {
            return;
        }
        clip.bindToContainer(oldContainerId, oldContainerName);
        if (clip instanceof com.director.timeline.model.audio.AudioTimelineClip) {
            com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                    .onAudioClipBindingChanged(clip, newContainerId, oldContainerId);
        }
        LOGGER.debug("[Timeline] 撤销片段 {} 容器绑定，恢复为 {}", clip.getLabel(), oldContainerName);
    }

    @Override
    public String getDescription() {
        return newContainerId == null ? "取消片段容器绑定" : "绑定片段到容器";
    }

    private boolean canBind(UUID containerId) {
        if (containerId == null || clip instanceof com.director.timeline.model.audio.AudioTimelineClip) {
            return true;
        }
        var track = clip.getParentTrack();
        if (track == null && model != null) {
            track = model.findTrackForClip(clip);
        }
        if (track == null || track.getType() != TrackType.CONTAINER) {
            return false;
        }
        if (containerId.equals(oldContainerId)) {
            return true;
        }
        for (TimelineClip existing : DirectorServices.get().containerTimeline()
                .findClipsForContainer(containerId)) {
            if (existing != null && existing != clip) {
                return false;
            }
        }
        return true;
    }
}

