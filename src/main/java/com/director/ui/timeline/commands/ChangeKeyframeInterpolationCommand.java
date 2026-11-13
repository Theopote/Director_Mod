package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.clip.ClipKeyframe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 修改关键帧插值类型命令（支持撤销）。
 *
 * <p>插值类型直接写在 {@link ClipKeyframe#getInterpolation()} 上。
 */
public class ChangeKeyframeInterpolationCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeKeyframeInterpolationCommand.class);
    private static final float KEYFRAME_TOLERANCE = 0.0001f;

    @SuppressWarnings("unused")
    private final TimelineTrack track;
    private final TimelineClip clip;
    private final float keyframeTime;
    private final TimelineInterpolation newInterpolation;
    private TimelineInterpolation oldInterpolation;
    private boolean executed = false;

    public ChangeKeyframeInterpolationCommand(
            TimelineModel model,
            TimelineTrack track,
            TimelineClip clip,
            float keyframeTime,
            TimelineInterpolation interpolation) {
        super(model, "keyframe_interpolation_changed");
        this.track = track;
        this.clip = clip;
        this.keyframeTime = keyframeTime;
        this.newInterpolation = interpolation;
    }

    @Override
    protected void doExecute() {
        if (clip == null || newInterpolation == null) {
            LOGGER.warn("[Timeline] 设置关键帧插值失败：clip 或 interpolation 为 null");
            return;
        }
        ClipKeyframe keyframe = clip.findKeyframeAt(keyframeTime, KEYFRAME_TOLERANCE);
        if (keyframe == null) {
            LOGGER.warn("[Timeline] 设置关键帧插值失败：关键帧不存在于时间 {:.2f}", keyframeTime);
            return;
        }
        oldInterpolation = keyframe.getInterpolation() != null
                ? keyframe.getInterpolation()
                : TimelineInterpolation.LINEAR;
        if (newInterpolation == oldInterpolation) {
            LOGGER.debug("[Timeline] 关键帧 {:.2f}s 插值类型未改变，跳过: {}", keyframeTime, newInterpolation.getDisplayName());
            return;
        }
        keyframe.setInterpolation(newInterpolation);
        executed = true;
        LOGGER.debug("[Timeline] 设置关键帧 {:.2f}s 插值类型为 {} (原: {})",
                keyframeTime, newInterpolation.getDisplayName(), oldInterpolation.getDisplayName());
    }

    @Override
    protected void doUndo() {
        if (!executed || clip == null || oldInterpolation == null) {
            return;
        }
        ClipKeyframe keyframe = clip.findKeyframeAt(keyframeTime, KEYFRAME_TOLERANCE);
        if (keyframe == null) {
            return;
        }
        keyframe.setInterpolation(oldInterpolation);
        LOGGER.debug("[Timeline] 恢复关键帧 {:.2f}s 插值类型为 {}", keyframeTime, oldInterpolation.getDisplayName());
    }

    @Override
    public String getDescription() {
        return String.format("设置插值类型为 %s", newInterpolation.getDisplayName());
    }

    @Override
    public boolean canUndo() {
        return executed;
    }

    public static TimelineInterpolation getKeyframeInterpolation(TimelineClip clip, float time) {
        if (clip == null) {
            return TimelineInterpolation.LINEAR;
        }
        ClipKeyframe keyframe = clip.findKeyframeAt(time, KEYFRAME_TOLERANCE);
        if (keyframe == null || keyframe.getInterpolation() == null) {
            return TimelineInterpolation.LINEAR;
        }
        return keyframe.getInterpolation();
    }

    public static void setKeyframeInterpolationDirectly(TimelineClip clip, float time, TimelineInterpolation interpolation) {
        if (clip == null || interpolation == null) {
            return;
        }
        ClipKeyframe keyframe = clip.findKeyframeAt(time, KEYFRAME_TOLERANCE);
        if (keyframe != null) {
            keyframe.setInterpolation(interpolation);
        }
    }
}
