package com.director.ui.timeline.commands;

import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * 重命名片段命令（支持撤销）。
 */
public class RenameClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenameClipCommand.class);

    private final TimelineClip clip;
    private final String newLabel;
    private String oldLabel;
    private String oldContainerName;
    private UUID oldContainerId;
    private boolean applied;

    public RenameClipCommand(TimelineModel model, TimelineClip clip, String newLabel) {
        super(model, "clip_renamed");
        this.clip = clip;
        this.newLabel = newLabel;
    }

    @Override
    protected void doExecute() {
        if (clip == null || newLabel == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        oldLabel = clip.getLabel();
        oldContainerId = clip.getBoundContainerId();
        oldContainerName = clip.getBoundContainerName();
        if (Objects.equals(newLabel, oldLabel)) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        applied = true;
        clip.setLabel(newLabel);
        if (oldContainerId != null) {
            var container = DirectorServices.get().containers().getContainer(oldContainerId);
            if (container != null) {
                clip.setBoundContainerName(newLabel);
                if (!newLabel.equals(container.getName())) {
                    DirectorServices.get().containers().renameContainer(container, newLabel);
                }
            }
        }
        LOGGER.debug("[Timeline] 重命名片段: {} -> {}", oldLabel, newLabel);
    }

    @Override
    protected void doUndo() {
        if (!applied || clip == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        clip.setLabel(oldLabel);
        if (oldContainerId != null) {
            clip.setBoundContainerName(oldContainerName);
            var container = DirectorServices.get().containers().getContainer(oldContainerId);
            if (container != null && !Objects.equals(container.getName(), oldContainerName)) {
                DirectorServices.get().containers().renameContainer(container, oldContainerName);
            }
        }
        applied = false;
        LOGGER.debug("[Timeline] 撤销片段重命名: {} -> {}", newLabel, oldLabel);
    }

    @Override
    protected void compensateExecute() {
        if (!applied || clip == null) {
            return;
        }
        clip.setLabel(oldLabel);
        if (oldContainerId != null) {
            clip.setBoundContainerName(oldContainerName);
            var container = DirectorServices.get().containers().getContainer(oldContainerId);
            if (container != null && Objects.equals(container.getName(), newLabel)) {
                DirectorServices.get().containers().renameContainer(container, oldContainerName);
            }
        }
        applied = false;
    }

    @Override
    public String getDescription() {
        return "重命名片段";
    }
}
