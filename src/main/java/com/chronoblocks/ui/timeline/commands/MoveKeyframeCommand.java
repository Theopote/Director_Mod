package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import com.chronoblocks.ui.timeline.model.TimelineModel;

/**
 * 移动关键帧命令（支持撤销）。
 * 
 * <p>与使用 RemoveKeyframeCommand + AddKeyframeCommand 不同，
 * 此命令专门用于移动关键帧，保留关键帧的所有属性（如插值类型）。
 */
public class MoveKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveKeyframeCommand.class);
    private static final float KEYFRAME_TOLERANCE = 0.0001f;

    private final TimelineClip clip;
    private final float oldTime;
    private final float newTime;
    private TimelineInterpolation oldInterpolation;
    private boolean moved = false;

    public MoveKeyframeCommand(TimelineModel model, TimelineClip clip, float oldTime, float newTime) {
        super(model, "keyframe_moved");
        this.clip = clip;
        this.oldTime = oldTime;
        this.newTime = newTime;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 移动关键帧失败：clip 为 null");
            return;
        }

        // 检查旧位置是否有关键帧
        if (!clip.hasKeyframeAt(oldTime, KEYFRAME_TOLERANCE)) {
            LOGGER.warn("[Timeline] 关键帧不存在于位置 {:.2f}", oldTime);
            return;
        }

        // 检查新位置是否已有关键帧
        if (clip.hasKeyframeAt(newTime, KEYFRAME_TOLERANCE)) {
            LOGGER.warn("[Timeline] 新位置 {:.2f} 已有关键帧", newTime);
            return;
        }

        // 保存旧位置的插值信息
        oldInterpolation = ChangeKeyframeInterpolationCommand.getKeyframeInterpolation(clip, oldTime);

        // 移除旧关键帧
        clip.removeKeyframe(oldTime);
        // 在新位置添加关键帧
        clip.addKeyframe(newTime);

        // 迁移插值信息到新位置
        if (oldInterpolation != null && oldInterpolation != TimelineInterpolation.LINEAR) {
            ChangeKeyframeInterpolationCommand.setKeyframeInterpolationDirectly(clip, newTime, oldInterpolation);
        }

        moved = true;
        LOGGER.debug("[Timeline] 移动关键帧: {:.2f} -> {:.2f} (插值: {})", 
                oldTime, newTime, oldInterpolation != null ? oldInterpolation.getDisplayName() : "默认");
    }

    @Override
    protected void doUndo() {
        if (!moved || clip == null) {
            return;
        }

        // 清理新位置的插值（如果存在）
        ChangeKeyframeInterpolationCommand.removeKeyframeInterpolation(clip, newTime);

        // 移除新位置的关键帧
        clip.removeKeyframe(newTime);
        // 恢复旧位置的关键帧
        clip.addKeyframe(oldTime);

        // 恢复旧位置的插值信息
        if (oldInterpolation != null && oldInterpolation != TimelineInterpolation.LINEAR) {
            ChangeKeyframeInterpolationCommand.setKeyframeInterpolationDirectly(clip, oldTime, oldInterpolation);
        }

        LOGGER.debug("[Timeline] 撤销移动关键帧: {:.2f} -> {:.2f}", newTime, oldTime);
    }

    @Override
    public String getDescription() {
        return String.format("移动关键帧: %.2fs -> %.2fs", oldTime, newTime);
    }
}

