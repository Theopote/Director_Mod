package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;

/**
 * 删除关键帧命令。
 * 
 * <p>优化：继承 BaseTimelineCommand 以确保模型更新和一致性。
 */
public class RemoveKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveKeyframeCommand.class);

    private final TimelineClip clip;
    private final float time;
    private boolean removed = false;

    public RemoveKeyframeCommand(TimelineModel model, TimelineClip clip, float time) {
        super(model, "keyframe_removed");
        this.clip = clip;
        this.time = time;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 删除关键帧失败：clip 为 null");
            return;
        }
        removed = clip.removeKeyframe(time);
        if (removed) {
            // 清理该关键帧的插值信息
            ChangeKeyframeInterpolationCommand.removeKeyframeInterpolation(clip, time);
            LOGGER.debug("[Timeline] 删除关键帧 {:.2f}", time);
        } else {
            LOGGER.debug("[Timeline] 关键帧不存在，跳过删除: {:.2f}", time);
        }
    }

    @Override
    protected void doUndo() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 恢复关键帧失败：clip 为 null");
            return;
        }
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

    @Override
    public boolean canUndo() {
        return removed;
    }
}


