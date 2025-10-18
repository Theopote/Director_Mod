package com.chronoblocks.core.timeline.commands;

import com.chronoblocks.core.timeline.TimelineCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 删除关键帧命令
 * 
 * @author ChronoBlocks Team
 */
public class RemoveKeyframeCommand implements TimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveKeyframeCommand.class);
    
    private final List<Float> keyframes;
    private final float time;
    private int removedIndex = -1;
    
    public RemoveKeyframeCommand(List<Float> keyframes, float time) {
        this.keyframes = keyframes;
        this.time = time;
    }
    
    @Override
    public void execute() {
        removedIndex = keyframes.indexOf(time);
        if (removedIndex >= 0) {
            keyframes.remove(removedIndex);
            LOGGER.debug("[Timeline] 删除关键帧: {:.2f}, 索引: {}", time, removedIndex);
        }
    }
    
    @Override
    public void undo() {
        if (removedIndex >= 0) {
            keyframes.add(removedIndex, time);
            LOGGER.debug("[Timeline] 恢复关键帧: {:.2f}", time);
        }
    }
    
    @Override
    public String getDescription() {
        return String.format("删除关键帧 (%.2fs)", time);
    }
}

