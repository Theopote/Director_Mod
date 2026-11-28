package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 移动轨道命令（支持撤销）。
 */
public class MoveTrackCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveTrackCommand.class);

    private final TimelineModel model;
    private final int fromIndex;
    private final int toIndex;

    public MoveTrackCommand(TimelineModel model, int fromIndex, int toIndex) {
        super(model, "track_moved");
        this.model = model;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    @Override
    protected void doExecute() {
        if (!canMove(fromIndex, toIndex)) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        model.moveTrack(fromIndex, toIndex);
        LOGGER.debug("[Timeline] 移动轨道: 索引 {} -> {}", fromIndex, toIndex);
    }

    @Override
    protected void doUndo() {
        if (!canMove(toIndex, fromIndex)) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        model.moveTrack(toIndex, fromIndex);
        LOGGER.debug("[Timeline] 撤销移动轨道: 索引 {} -> {}", toIndex, fromIndex);
    }

    private boolean canMove(int from, int to) {
        return model != null && from >= 0 && from < model.getTrackCount()
                && to >= 0 && to < model.getTrackCount() && from != to;
    }

    @Override
    public String getDescription() {
        return String.format("移动轨道: 索引 %d -> %d", fromIndex, toIndex);
    }
}
