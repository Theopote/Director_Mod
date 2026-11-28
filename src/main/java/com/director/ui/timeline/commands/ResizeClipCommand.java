package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.ui.timeline.audio.TimelineAudioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 调整片段边缘命令（支持撤销）。
 */
public class ResizeClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResizeClipCommand.class);

    private final TimelineClip clip;
    private final float oldStartTime;
    private final float oldEndTime;
    private final float newStartTime;
    private final float newEndTime;
    private TimelineClip oldState;
    private TimelineClip newState;
    private boolean applied;

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
        this.oldState = clip != null ? clip.snapshot() : null;
    }

    public ResizeClipCommand(TimelineModel model, TimelineClip clip,
            float oldStartTime, float oldEndTime, float newStartTime, float newEndTime,
            TimelineClip gestureStart) {
        super(model, "clip_resized");
        this.clip = clip;
        this.oldStartTime = oldStartTime;
        this.oldEndTime = oldEndTime;
        this.newStartTime = newStartTime;
        this.newEndTime = newEndTime;
        this.oldState = gestureStart;
        this.newState = clip != null ? clip.snapshot() : null;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        if (Float.compare(oldStartTime, newStartTime) == 0
                && Float.compare(oldEndTime, newEndTime) == 0) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        applied = true;
        if (newState == null) {
            if (clip instanceof AudioTimelineClip) {
                TimelineAudioService.getInstance().remapAudioClipKeyframes(
                        clip, oldStartTime, oldEndTime, newStartTime, newEndTime);
            }
            clip.setTimeRange(newStartTime, newEndTime);
            newState = clip.snapshot();
        } else {
            clip.restoreStateFrom(newState);
        }
        TimelineAudioService.getInstance().onClipTimeRangeChanged(
                clip, oldStartTime, oldEndTime, newStartTime, newEndTime, false);
        LOGGER.debug("[Timeline] 调整片段范围: {} -> {}", oldStartTime, newStartTime);
    }

    @Override
    protected void doUndo() {
        if (!applied || clip == null || oldState == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        clip.restoreStateFrom(oldState);
        TimelineAudioService.getInstance().onClipTimeRangeChanged(
                clip, newStartTime, newEndTime, oldStartTime, oldEndTime, false);
        applied = false;
        LOGGER.debug("[Timeline] 撤销调整片段范围");
    }

    @Override
    protected void compensateExecute() {
        if (applied && clip != null && oldState != null) {
            clip.restoreStateFrom(oldState);
        }
        applied = false;
    }

    @Override
    protected void compensateUndo() {
        if (clip != null && newState != null) {
            clip.restoreStateFrom(newState);
        }
        applied = true;
    }

    @Override
    public String getDescription() {
        return String.format("调整片段范围: %.2fs-%.2fs -> %.2fs-%.2fs",
                oldStartTime, oldEndTime, newStartTime, newEndTime);
    }
}
