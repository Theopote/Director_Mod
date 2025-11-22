package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

/**
 * 折叠/展开轨道命令（支持撤销）。
 */
public class CollapseTrackCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollapseTrackCommand.class);

    private final TimelineTrack track;
    private boolean wasCollapsed;

    public CollapseTrackCommand(TimelineModel model, TimelineTrack track) {
        super(model, "track_collapse_changed");
        this.track = track;
    }

    @Override
    protected void doExecute() {
        if (track != null) {
            wasCollapsed = track.isCollapsed();
            track.setCollapsed(!wasCollapsed);
            LOGGER.debug("[Timeline] {}轨道: {}", wasCollapsed ? "展开" : "折叠", track.getName());
        }
    }

    @Override
    protected void doUndo() {
        if (track != null) {
            track.setCollapsed(wasCollapsed);
            LOGGER.debug("[Timeline] 撤销{}轨道: {}", wasCollapsed ? "折叠" : "展开", track.getName());
        }
    }

    @Override
    public String getDescription() {
        return String.format("%s轨道: %s", wasCollapsed ? "展开" : "折叠", 
                           track != null ? track.getName() : "?");
    }
}

