package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;

/**
 * 设置片段时长命令（支持撤销）。
 * 
 * <p>优化：继承 BaseTimelineCommand 以确保模型更新和一致性。
 */
public class SetClipLengthCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetClipLengthCommand.class);
    private static final float MIN_CLIP_LENGTH = 0.05f;

    private final TimelineClip clip;
    private final float newLength;
    private float oldLength;
    private boolean executed = false;

    public SetClipLengthCommand(TimelineModel model, TimelineClip clip, float newLength) {
        super(model, "clip_length_changed");
        this.clip = clip;
        // 确保长度在合理范围内
        this.newLength = Math.max(MIN_CLIP_LENGTH, newLength);
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 设置片段时长失败：clip 为 null");
            return;
        }
        oldLength = clip.getLength();
        clip.setLength(newLength);
        executed = true;
        LOGGER.debug("[Timeline] 设置片段时长为 {:.2f}s (原: {:.2f}s)", newLength, oldLength);
    }

    @Override
    protected void doUndo() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 恢复片段时长失败：clip 为 null");
            return;
        }
        if (!executed) {
            return;
        }
        clip.setLength(oldLength);
        LOGGER.debug("[Timeline] 恢复片段时长为 {:.2f}s", oldLength);
    }

    @Override
    public String getDescription() {
        return String.format("设置时长为 %.2fs", newLength);
    }

    @Override
    public boolean canUndo() {
        return executed;
    }
}

