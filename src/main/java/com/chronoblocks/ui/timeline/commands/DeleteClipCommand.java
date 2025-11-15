package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 删除片段命令。
 */
public class DeleteClipCommand implements TimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteClipCommand.class);

    private final Object clip;
    private final List<Object> clipList;
    private int removedIndex = -1;

    public DeleteClipCommand(Object clip, List<Object> clipList) {
        this.clip = clip;
        this.clipList = clipList;
    }

    @Override
    public void execute() {
        removedIndex = clipList.indexOf(clip);
        if (removedIndex >= 0) {
            clipList.remove(removedIndex);
            LOGGER.debug("[Timeline] 删除片段，索引: {}", removedIndex);
        }
    }

    @Override
    public void undo() {
        if (removedIndex >= 0 && removedIndex <= clipList.size()) {
            clipList.add(removedIndex, clip);
            LOGGER.debug("[Timeline] 恢复片段到索引: {}", removedIndex);
        }
    }

    @Override
    public String getDescription() {
        return "删除片段";
    }
}


