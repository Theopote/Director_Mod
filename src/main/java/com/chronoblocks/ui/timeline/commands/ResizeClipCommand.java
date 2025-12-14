package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;

/**
 * 调整片段边缘命令（支持撤销）。
 * 
 * <p>用于调整片段的开始或结束时间，与 MoveClipCommand 不同，
 * ResizeClipCommand 只改变片段的一个边缘，保持另一个边缘不变。
 */
public class ResizeClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResizeClipCommand.class);

    private final TimelineClip clip;
    private final float oldStartTime;
    private final float oldEndTime;
    private final float newStartTime;
    private final float newEndTime;

    /**
     * 构造函数
     * @param model Timeline 模型
     * @param clip 要调整的片段
     * @param edge 要调整的边缘（START 或 END）
     * @param newTime 新的时间位置
     */
    public ResizeClipCommand(
            TimelineModel model,
            TimelineClip clip,
            float oldStartTime,
            float oldEndTime,
            float newStartTime,
            float newEndTime) {
        super(model, "clip_resized");
        this.clip = clip;
        this.oldStartTime = oldStartTime;
        this.oldEndTime = oldEndTime;
        this.newStartTime = newStartTime;
        this.newEndTime = newEndTime;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            return;
        }
        clip.setTimeRange(newStartTime, newEndTime);
        com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                .onClipTimeRangeChanged(clip, oldStartTime, oldEndTime, newStartTime, newEndTime);
        LOGGER.debug("[Timeline] 调整片段范围: {:.2f}-{:.2f} -> {:.2f}-{:.2f}",
                oldStartTime, oldEndTime, newStartTime, newEndTime);
    }

    @Override
    protected void doUndo() {
        if (clip == null) {
            return;
        }
        clip.setTimeRange(oldStartTime, oldEndTime);
        com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                .onClipTimeRangeChanged(clip, newStartTime, newEndTime, oldStartTime, oldEndTime);
        LOGGER.debug("[Timeline] 撤销调整片段范围");
    }

    @Override
    public String getDescription() {
        return String.format("调整片段范围: %.2fs-%.2fs -> %.2fs-%.2fs",
                oldStartTime, oldEndTime, newStartTime, newEndTime);
    }
}

