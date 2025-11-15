package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 删除轨道命令（支持撤销）。
 */
public class DeleteTrackCommand implements TimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteTrackCommand.class);

    private final Object track;
    private final List<Object> trackList;
    private int removedIndex = -1;

    public DeleteTrackCommand(Object track, List<Object> trackList) {
        this.track = track;
        this.trackList = trackList;
    }

    @Override
    public void execute() {
        removedIndex = trackList.indexOf(track);
        if (removedIndex >= 0) {
            trackList.remove(removedIndex);
            LOGGER.info("[Timeline] 删除轨道，索引: {}", removedIndex);
        }
    }

    @Override
    public void undo() {
        if (removedIndex >= 0 && removedIndex <= trackList.size()) {
            trackList.add(removedIndex, track);
            LOGGER.info("[Timeline] 恢复轨道到索引: {}", removedIndex);
        }
    }

    @Override
    public String getDescription() {
        return "删除轨道";
    }
}


