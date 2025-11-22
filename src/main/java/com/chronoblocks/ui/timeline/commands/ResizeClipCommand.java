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

    public enum Edge {
        START,  // 调整开始边缘
        END     // 调整结束边缘
    }

    private final TimelineClip clip;
    private final Edge edge;
    private final float newTime;
    private float oldTime;

    /**
     * 构造函数
     * @param model Timeline 模型
     * @param clip 要调整的片段
     * @param edge 要调整的边缘（START 或 END）
     * @param newTime 新的时间位置
     */
    public ResizeClipCommand(TimelineModel model, TimelineClip clip, Edge edge, float newTime) {
        super(model, "clip_resized");
        this.clip = clip;
        this.edge = edge;
        this.newTime = newTime;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            return;
        }

        if (edge == Edge.START) {
            oldTime = clip.getStartTime();
            float endTime = clip.getEndTime();
            // 确保开始时间不超过结束时间
            float clampedNewTime = Math.min(newTime, endTime - 0.05f);
            clip.setTimeRange(clampedNewTime, endTime);
            LOGGER.debug("[Timeline] 调整片段开始边缘: {:.2f} -> {:.2f}", oldTime, clampedNewTime);
        } else {
            oldTime = clip.getEndTime();
            float startTime = clip.getStartTime();
            // 确保结束时间不小于开始时间
            float clampedNewTime = Math.max(newTime, startTime + 0.05f);
            clip.setTimeRange(startTime, clampedNewTime);
            LOGGER.debug("[Timeline] 调整片段结束边缘: {:.2f} -> {:.2f}", oldTime, clampedNewTime);
        }
    }

    @Override
    protected void doUndo() {
        if (clip == null) {
            return;
        }

        if (edge == Edge.START) {
            float endTime = clip.getEndTime();
            clip.setTimeRange(oldTime, endTime);
            LOGGER.debug("[Timeline] 撤销调整片段开始边缘: {:.2f}", oldTime);
        } else {
            float startTime = clip.getStartTime();
            clip.setTimeRange(startTime, oldTime);
            LOGGER.debug("[Timeline] 撤销调整片段结束边缘: {:.2f}", oldTime);
        }
    }

    @Override
    public String getDescription() {
        String edgeName = edge == Edge.START ? "开始" : "结束";
        return String.format("调整片段%s边缘: %.2fs -> %.2fs", edgeName, oldTime, newTime);
    }
}

