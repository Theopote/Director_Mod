package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 移动片段命令。
 */
public class MoveClipCommand implements TimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveClipCommand.class);

    public interface Clip {
        float getStartTime();
        void setStartTime(float time);
        float getEndTime();
        void setEndTime(float time);
        float getLength();
    }

    private final Clip clip;
    private final float oldStartTime;
    private final float oldEndTime;
    private final float newStartTime;
    private final float newEndTime;

    public MoveClipCommand(Clip clip, float newStartTime, float newEndTime) {
        this.clip = clip;
        this.oldStartTime = clip.getStartTime();
        this.oldEndTime = clip.getEndTime();
        this.newStartTime = newStartTime;
        this.newEndTime = newEndTime;
    }

    @Override
    public void execute() {
        clip.setStartTime(newStartTime);
        clip.setEndTime(newEndTime);
        LOGGER.debug("[Timeline] 移动片段: {:.2f}-{:.2f} -> {:.2f}-{:.2f}",
                oldStartTime, oldEndTime, newStartTime, newEndTime);
    }

    @Override
    public void undo() {
        clip.setStartTime(oldStartTime);
        clip.setEndTime(oldEndTime);
        LOGGER.debug("[Timeline] 撤销移动片段");
    }

    @Override
    public String getDescription() {
        return String.format("移动片段 (%.2fs -> %.2fs)", oldStartTime, newStartTime);
    }
}


