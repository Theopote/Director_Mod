package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

/**
 * 添加轨道命令（支持撤销）。
 */
public class AddTrackCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddTrackCommand.class);

    private final TimelineTrack track;
    private boolean added = false;

    public AddTrackCommand(TimelineModel model, TimelineTrack track) {
        super(model, "track_added");
        this.track = track;
    }

    @Override
    protected void doExecute() {
        if (track != null && model != null) {
            model.addTrack(track);
            added = true;
            LOGGER.debug("[Timeline] 添加轨道: {}", track.getName());
        }
    }

    @Override
    protected void doUndo() {
        if (added && track != null && model != null) {
            model.removeTrack(track);
            added = false;
            LOGGER.debug("[Timeline] 撤销添加轨道: {}", track.getName());
        }
    }

    @Override
    public String getDescription() {
        return track != null ? "添加轨道: " + track.getName() : "添加轨道";
    }
}

