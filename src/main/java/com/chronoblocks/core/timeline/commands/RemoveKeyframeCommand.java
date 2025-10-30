package com.chronoblocks.core.timeline.commands;

import com.chronoblocks.core.timeline.TimelineCommand;
import com.chronoblocks.ui.timeline.TimelineClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 删除关键帧命令
 *
 * <p>支持 {@link TimelineClip} TreeSet 结构，记录删除状态以便撤销。</p>
 */
public class RemoveKeyframeCommand implements TimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveKeyframeCommand.class);

    private final TimelineClip clip;
    private final float time;
    private boolean removed = false;

    public RemoveKeyframeCommand(TimelineClip clip, float time) {
        this.clip = clip;
        this.time = time;
    }

    @Override
    public void execute() {
        removed = clip.removeKeyframe(time);
        if (removed) {
            LOGGER.debug("[Timeline] 删除关键帧 {:.2f}", time);
        }
    }

    @Override
    public void undo() {
        if (!removed) {
            return;
        }
        clip.addKeyframe(time);
        LOGGER.debug("[Timeline] 恢复关键帧 {:.2f}", time);
    }

    @Override
    public String getDescription() {
        return String.format("删除关键帧(%.2fs)", time);
    }
}
