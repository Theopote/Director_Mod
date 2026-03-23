package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;

/**
 * 切换轨道 Solo 状态命令（支持撤销）。
 */
public class ToggleTrackSoloCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleTrackSoloCommand.class);

    private final TimelineTrack track;
    private boolean oldSoloState;

    public ToggleTrackSoloCommand(TimelineModel model, TimelineTrack track) {
        super(model, "track_solo_toggled");
        this.track = track;
    }

    @Override
    protected void doExecute() {
        if (track != null) {
            oldSoloState = track.isSolo();
            track.setSolo(!oldSoloState);
            LOGGER.debug("[Timeline] {}轨道: {}", oldSoloState ? "取消 Solo" : "Solo", track.getName());
        }
    }

    @Override
    protected void doUndo() {
        if (track != null) {
            track.setSolo(oldSoloState);
            LOGGER.debug("[Timeline] 撤销{}轨道: {}", oldSoloState ? "Solo" : "取消 Solo", track.getName());
        }
    }

    @Override
    public String getDescription() {
        return String.format("%s轨道: %s", oldSoloState ? "取消 Solo" : "Solo", 
                           track != null ? track.getName() : "?");
    }
}

