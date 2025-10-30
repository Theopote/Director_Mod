package com.chronoblocks.core.timeline.commands;

import com.chronoblocks.core.timeline.TimelineCommand;
import com.chronoblocks.ui.timeline.TimelineClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 添加关键帧命令
 *
 * <p>适配 {@link TimelineClip} 的 TreeSet 关键帧存储结构，保证排序与去重。</p>
 */
public class AddKeyframeCommand implements TimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddKeyframeCommand.class);

    private final TimelineClip clip;
    private final float time;
    private boolean added = false;

    public AddKeyframeCommand(TimelineClip clip, float time) {
        this.clip = clip;
        this.time = time;
    }

    @Override
    public void execute() {
        if (clip.hasKeyframeAt(time, 0.0001f)) {
            added = false;
            return;
        }
        clip.addKeyframe(time);
        added = true;
        LOGGER.debug("[Timeline] 添加关键帧 {:.2f}", time);
    }

    @Override
    public void undo() {
        if (!added) {
            return;
        }
        clip.removeKeyframe(time);
        LOGGER.debug("[Timeline] 撤销关键帧 {:.2f}", time);
    }

    @Override
    public String getDescription() {
        return String.format("添加关键帧(%.2fs)", time);
    }
}
