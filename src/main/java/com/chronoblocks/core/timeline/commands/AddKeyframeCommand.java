package com.chronoblocks.core.timeline.commands;

import com.chronoblocks.core.timeline.TimelineCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 添加关键帧命令
 * 
 * @author ChronoBlocks Team
 */
public class AddKeyframeCommand implements TimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddKeyframeCommand.class);
    
    private final List<Float> keyframes;
    private final float time;
    
    public AddKeyframeCommand(List<Float> keyframes, float time) {
        this.keyframes = keyframes;
        this.time = time;
    }
    
    @Override
    public void execute() {
        if (!keyframes.contains(time)) {
            keyframes.add(time);
            keyframes.sort(Float::compareTo);
            LOGGER.debug("[Timeline] 添加关键帧: {:.2f}", time);
        }
    }
    
    @Override
    public void undo() {
        keyframes.remove(time);
        LOGGER.debug("[Timeline] 移除关键帧: {:.2f}", time);
    }
    
    @Override
    public String getDescription() {
        return String.format("添加关键帧 (%.2fs)", time);
    }
}

