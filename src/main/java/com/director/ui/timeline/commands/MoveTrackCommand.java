package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;

import java.util.List;

/**
 * 移动轨道命令（支持撤销）。
 * 
 * <p>用于在轨道列表中上下移动轨道位置。
 */
public class MoveTrackCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveTrackCommand.class);

    private final List<TimelineTrack> tracks;
    private final int fromIndex;
    private final int toIndex;

    public MoveTrackCommand(TimelineModel model, int fromIndex, int toIndex) {
        super(model, "track_moved");
        this.tracks = model.getTracks();
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    @Override
    protected void doExecute() {
        if (fromIndex >= 0 && fromIndex < tracks.size() && 
            toIndex >= 0 && toIndex < tracks.size() && 
            fromIndex != toIndex) {
            TimelineTrack track = tracks.remove(fromIndex);
            tracks.add(toIndex, track);
            LOGGER.debug("[Timeline] 移动轨道: 索引 {} -> {}", fromIndex, toIndex);
        }
    }

    @Override
    protected void doUndo() {
        if (fromIndex >= 0 && fromIndex < tracks.size() && 
            toIndex >= 0 && toIndex < tracks.size() && 
            fromIndex != toIndex) {
            TimelineTrack track = tracks.remove(toIndex);
            tracks.add(fromIndex, track);
            LOGGER.debug("[Timeline] 撤销移动轨道: 索引 {} -> {}", toIndex, fromIndex);
        }
    }

    @Override
    public String getDescription() {
        return String.format("移动轨道: 索引 %d -> %d", fromIndex, toIndex);
    }
}

