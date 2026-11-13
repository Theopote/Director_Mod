package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.clip.ClipKeyframe;

/**
 * 移动关键帧命令（支持撤销）。
 *
 * <p>直接移动 {@link ClipKeyframe}，保留对象身份与插值等属性。
 */
public class MoveKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveKeyframeCommand.class);
    private static final float KEYFRAME_TOLERANCE = 0.0001f;

    private final TimelineClip clip;
    private final float oldTime;
    private final float newTime;
    private ClipKeyframe movedKeyframe;
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

        ClipKeyframe keyframe = clip.findKeyframeAt(oldTime, KEYFRAME_TOLERANCE);
        if (keyframe == null) {
            LOGGER.warn("[Timeline] 关键帧不存在于位置 {:.2f}", oldTime);
            return;
        }

        if (clip.hasKeyframeAt(newTime, KEYFRAME_TOLERANCE)) {
            LOGGER.warn("[Timeline] 新位置 {:.2f} 已有关键帧", newTime);
            return;
        }

        movedKeyframe = keyframe;
        clip.moveKeyframe(keyframe, newTime);
        moved = true;
        LOGGER.debug("[Timeline] 移动关键帧: {:.2f} -> {:.2f} (插值: {})",
                oldTime, newTime,
                keyframe.getInterpolation() != null ? keyframe.getInterpolation().getDisplayName() : "默认");
    }

    @Override
    protected void doUndo() {
        if (!moved || clip == null || movedKeyframe == null) {
            return;
        }

        clip.moveKeyframe(movedKeyframe, oldTime);
        LOGGER.debug("[Timeline] 撤销移动关键帧: {:.2f} -> {:.2f}", newTime, oldTime);
    }

    @Override
    public String getDescription() {
        return String.format("移动关键帧: %.2fs -> %.2fs", oldTime, newTime);
    }
}
