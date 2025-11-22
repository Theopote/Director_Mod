package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

/**
 * 切换轨道锁定状态命令（支持撤销）。
 */
public class ToggleTrackLockCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleTrackLockCommand.class);

    private final TimelineTrack track;
    private boolean oldLockedState;

    public ToggleTrackLockCommand(TimelineModel model, TimelineTrack track) {
        super(model, "track_lock_toggled");
        this.track = track;
    }

    @Override
    protected void doExecute() {
        if (track != null) {
            oldLockedState = track.isLocked();
            track.setLocked(!oldLockedState);
            LOGGER.debug("[Timeline] {}轨道: {}", oldLockedState ? "解锁" : "锁定", track.getName());
        }
    }

    @Override
    protected void doUndo() {
        if (track != null) {
            track.setLocked(oldLockedState);
            LOGGER.debug("[Timeline] 撤销{}轨道: {}", oldLockedState ? "锁定" : "解锁", track.getName());
        }
    }

    @Override
    public String getDescription() {
        return String.format("%s轨道: %s", oldLockedState ? "解锁" : "锁定", 
                           track != null ? track.getName() : "?");
    }
}

