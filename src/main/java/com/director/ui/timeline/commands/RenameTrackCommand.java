package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;

/**
 * 重命名轨道命令（支持撤销）。
 */
public class RenameTrackCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenameTrackCommand.class);

    private final TimelineTrack track;
    private final String newName;
    private String oldName;

    public RenameTrackCommand(TimelineModel model, TimelineTrack track, String newName) {
        super(model, "track_renamed");
        this.track = track;
        this.newName = newName;
    }

    @Override
    protected void doExecute() {
        if (track != null) {
            oldName = track.getName();
            track.setName(newName);
            LOGGER.debug("[Timeline] 重命名轨道: {} -> {}", oldName, newName);
        }
    }

    @Override
    protected void doUndo() {
        if (track != null && oldName != null) {
            track.setName(oldName);
            LOGGER.debug("[Timeline] 撤销重命名轨道: {} -> {}", newName, oldName);
        }
    }

    @Override
    public String getDescription() {
        return String.format("重命名轨道: %s -> %s", oldName != null ? oldName : "?", newName);
    }
}

