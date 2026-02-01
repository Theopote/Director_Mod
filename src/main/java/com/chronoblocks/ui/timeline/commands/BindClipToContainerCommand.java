package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;

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
        clip.bindToContainer(newContainerId, newContainerName);
        if (clip instanceof com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip) {
            com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
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
        if (clip instanceof com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip) {
            com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                    .onAudioClipBindingChanged(clip, newContainerId, oldContainerId);
        }
        LOGGER.debug("[Timeline] 撤销片段 {} 容器绑定，恢复为 {}", clip.getLabel(), oldContainerName);
    }

    @Override
    public String getDescription() {
        return newContainerId == null ? "取消片段容器绑定" : "绑定片段到容器";
    }
}

