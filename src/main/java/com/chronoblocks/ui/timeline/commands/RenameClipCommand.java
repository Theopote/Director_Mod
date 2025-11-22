package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;

/**
 * 重命名片段命令（支持撤销）。
 */
public class RenameClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenameClipCommand.class);

    private final TimelineClip clip;
    private final String newLabel;
    private String oldLabel;

    public RenameClipCommand(TimelineModel model, TimelineClip clip, String newLabel) {
        super(model, "clip_renamed");
        this.clip = clip;
        this.newLabel = newLabel;
    }

    @Override
    protected void doExecute() {
        if (clip == null || newLabel == null) {
            return;
        }
        oldLabel = clip.getLabel();
        if (newLabel.equals(oldLabel)) {
            return;
        }
        clip.setLabel(newLabel);
        LOGGER.debug("[Timeline] 重命名片段: {} -> {}", oldLabel, newLabel);
    }

    @Override
    protected void doUndo() {
        if (clip == null || oldLabel == null) {
            return;
        }
        clip.setLabel(oldLabel);
        LOGGER.debug("[Timeline] 撤销片段重命名: {} -> {}", newLabel, oldLabel);
    }

    @Override
    public String getDescription() {
        return "重命名片段";
    }
}

