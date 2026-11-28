package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.ui.timeline.audio.TimelineAudioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设置片段时长命令（支持撤销）。
 */
public class SetClipLengthCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetClipLengthCommand.class);
    private static final float MIN_CLIP_LENGTH = 0.05f;

    private final TimelineClip clip;
    private final float newLength;
    private TimelineClip oldState;
    private TimelineClip newState;
    private boolean applied;

    public SetClipLengthCommand(TimelineModel model, TimelineClip clip, float newLength) {
        super(model, "clip_length_changed");
        this.clip = clip;
        this.newLength = Math.max(MIN_CLIP_LENGTH, newLength);
        this.oldState = clip != null ? clip.snapshot() : null;
    }

    @Override
    protected void doExecute() {
        if (clip == null || !Float.isFinite(newLength)) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        if (Float.compare(clip.getLength(), newLength) == 0) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        applied = true;
        if (newState != null) {
            clip.restoreStateFrom(newState);
        } else {
            if (oldState != null) {
                clip.restoreStateFrom(oldState);
            }
            float oldStart = clip.getStartTime();
            float oldEnd = clip.getEndTime();
            if (clip instanceof AudioTimelineClip) {
                TimelineAudioService.getInstance().remapAudioClipKeyframes(
                        clip, oldStart, oldEnd, oldStart, oldStart + newLength);
            }
            clip.setLength(newLength);
            newState = clip.snapshot();
        }
        TimelineAudioService.getInstance().onClipTimeRangeChanged(
                clip, oldState.getStartTime(), oldState.getEndTime(),
                clip.getStartTime(), clip.getEndTime(), false);
        LOGGER.debug("[Timeline] 设置片段时长为 {}s", newLength);
    }

    @Override
    protected void doUndo() {
        if (!applied || clip == null || oldState == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        float previousStart = clip.getStartTime();
        float previousEnd = clip.getEndTime();
        clip.restoreStateFrom(oldState);
        TimelineAudioService.getInstance().onClipTimeRangeChanged(
                clip, previousStart, previousEnd, oldState.getStartTime(), oldState.getEndTime(), false);
        applied = false;
        LOGGER.debug("[Timeline] 恢复片段时长为 {}s", oldState.getLength());
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
        return String.format("设置时长为 %.2fs", newLength);
    }

    @Override
    public boolean canUndo() {
        return applied;
    }
}
