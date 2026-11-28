package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;

/**
 * 设置轨道可见性（支持撤销）。
 */
public class SetTrackVisibleCommand extends BaseTimelineCommand {
    private final TimelineTrack track;
    private final boolean visible;
    private boolean oldVisible;

    public SetTrackVisibleCommand(TimelineModel model, TimelineTrack track, boolean visible) {
        super(model, "track_visibility_changed");
        this.track = track;
        this.visible = visible;
    }

    @Override
    protected void doExecute() {
        if (track == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        oldVisible = track.isVisible();
        if (oldVisible == visible) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        track.setVisible(visible);
    }

    @Override
    protected void doUndo() {
        track.setVisible(oldVisible);
    }

    @Override
    public String getDescription() {
        return String.format("%s轨道: %s", track != null ? track.getName() : "?", visible ? "显示" : "隐藏");
    }
}
