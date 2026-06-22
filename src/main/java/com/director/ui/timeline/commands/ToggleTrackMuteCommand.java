package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;

/**
 * 切换轨道静音状态命令（支持撤销）。
 */
public class ToggleTrackMuteCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleTrackMuteCommand.class);

    private final TimelineTrack track;
    private boolean oldMutedState;

    public ToggleTrackMuteCommand(TimelineModel model, TimelineTrack track) {
        super(model, "track_mute_toggled");
        this.track = track;
    }

    @Override
    protected void doExecute() {
        if (track != null) {
            oldMutedState = track.isMuted();
            track.setMuted(!oldMutedState);
            LOGGER.debug("[Timeline] {}轨道: {}", oldMutedState ? "取消静音" : "静音", track.getName());
        }
    }

    @Override
    protected void doUndo() {
        if (track != null) {
            track.setMuted(oldMutedState);
            LOGGER.debug("[Timeline] 撤销{}轨道: {}", oldMutedState ? "静音" : "取消静音", track.getName());
        }
    }

    @Override
    public String getDescription() {
        return String.format("%s轨道: %s", oldMutedState ? "取消静音" : "静音", 
                           track != null ? track.getName() : "?");
    }
}

