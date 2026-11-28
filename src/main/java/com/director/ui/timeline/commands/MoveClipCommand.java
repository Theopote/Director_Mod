package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.audio.TimelineAudioService;

/**
 * 移动片段命令。
 */
public class MoveClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveClipCommand.class);

    public interface Clip {
        float getStartTime();
        void setStartTime(float time);
        float getEndTime();
        void setEndTime(float time);
        float getLength();
    }

    private final Clip clip;
    private final TimelineTrack oldTrack;
    private final TimelineTrack newTrack;
    private final float oldStartTime;
    private final float oldEndTime;
    private final float newStartTime;
    private final float newEndTime;
    private TimelineClip oldState;
    private TimelineClip newState;
    private boolean applied;

    public MoveClipCommand(TimelineModel model, Clip clip, float newStartTime, float newEndTime) {
        this(model, clip, clip != null ? clip.getStartTime() : 0f,
                clip != null ? clip.getEndTime() : 0f, newStartTime, newEndTime);
        if (clip instanceof ClipAdapter adapter) {
            oldState = adapter.getTimelineClip().snapshot();
        }
    }

    public MoveClipCommand(TimelineModel model, Clip clip,
            float oldStartTime, float oldEndTime, float newStartTime, float newEndTime) {
        super(model, "clip_moved");
        this.clip = clip;
        this.oldTrack = null;
        this.newTrack = null;
        this.oldStartTime = oldStartTime;
        this.oldEndTime = oldEndTime;
        this.newStartTime = newStartTime;
        this.newEndTime = newEndTime;
    }

    public MoveClipCommand(TimelineModel model, TimelineClip clip,
            TimelineTrack oldTrack, TimelineTrack newTrack,
            float oldStart, float oldEnd, float newStart, float newEnd) {
        super(model, "clip_moved");
        this.clip = new ClipAdapter(clip);
        this.oldTrack = oldTrack;
        this.newTrack = newTrack;
        this.oldStartTime = oldStart;
        this.oldEndTime = oldEnd;
        this.newStartTime = newStart;
        this.newEndTime = newEnd;
        this.oldState = clip != null ? clip.snapshot() : null;
    }

    public MoveClipCommand(TimelineModel model, TimelineClip clip,
            TimelineTrack oldTrack, TimelineTrack newTrack,
            float oldStart, float oldEnd, float newStart, float newEnd,
            TimelineClip gestureStart) {
        super(model, "clip_moved");
        this.clip = new ClipAdapter(clip);
        this.oldTrack = oldTrack;
        this.newTrack = newTrack;
        this.oldStartTime = oldStart;
        this.oldEndTime = oldEnd;
        this.newStartTime = newStart;
        this.newEndTime = newEnd;
        this.oldState = gestureStart != null ? gestureStart : clip != null ? clip.snapshot() : null;
        this.newState = clip != null ? clip.snapshot() : null;
    }

    private TimelineClip timelineClip() {
        return clip instanceof ClipAdapter adapter ? adapter.getTimelineClip() : null;
    }

    private void moveTo(TimelineTrack target) {
        TimelineClip item = timelineClip();
        if (item == null) {
            return;
        }
        var current = item.getParentTrack();
        if (current == target) {
            return;
        }
        if (target == null) {
            if (oldTrack == null && newTrack == null) {
                return;
            }
            throw new IllegalStateException("Clip move requires a target track");
        }
        if (!target.canAddClip(item)) {
            throw new IllegalStateException("Target track rejected clip: " + item.getLabel());
        }
        if (current != null) {
            current.removeClip(item);
        }
        try {
            if (!target.addClip(item)) {
                throw new IllegalStateException("Target track rejected clip");
            }
        } catch (RuntimeException | Error e) {
            if (current != null && !current.addClip(item)) {
                e.addSuppressed(new IllegalStateException("Source track rejected move rollback"));
            }
            throw e;
        }
        TimelineAudioService.getInstance().moveLinkedEventsToTrack(item, target);
    }

    private void restore(TimelineClip item, TimelineClip state) {
        item.restoreStateFrom(state);
        if (item instanceof EntityTimelineClip) {
            item.setTimeRange(state.getStartTime(), state.getEndTime());
        }
    }

    private void applyRange(TimelineClip timelineClip, float start, float end) {
        if (timelineClip != null) {
            if (Float.compare(end - start, timelineClip.getLength()) == 0) {
                timelineClip.offsetTime(start - timelineClip.getStartTime());
            } else {
                if (timelineClip instanceof AudioTimelineClip) {
                    TimelineAudioService.getInstance().remapAudioClipKeyframes(
                            timelineClip, timelineClip.getStartTime(), timelineClip.getEndTime(), start, end);
                }
                timelineClip.setTimeRange(start, end);
            }
        } else {
            clip.setStartTime(start);
            clip.setEndTime(end);
        }
    }

    @Override
    protected void doExecute() {
        TimelineClip timelineClip = timelineClip();
        if (clip == null || clip instanceof ClipAdapter adapter && adapter.getTimelineClip() == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        if (Float.compare(oldStartTime, newStartTime) == 0
                && Float.compare(oldEndTime, newEndTime) == 0 && oldTrack == newTrack) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        applied = true;
        moveTo(newTrack);
        if (timelineClip != null) {
            if (newState != null) {
                restore(timelineClip, newState);
            } else {
                if (oldState != null) {
                    restore(timelineClip, oldState);
                }
                applyRange(timelineClip, newStartTime, newEndTime);
                newState = timelineClip.snapshot();
            }
        } else {
            applyRange(null, newStartTime, newEndTime);
        }
        if (timelineClip != null) {
            TimelineAudioService.getInstance().onClipTimeRangeChanged(
                    timelineClip, oldStartTime, oldEndTime, newStartTime, newEndTime, false);
        }
        LOGGER.debug("[Timeline] 移动片段: {} -> {}", oldStartTime, newStartTime);
    }

    @Override
    protected void doUndo() {
        if (!applied || clip == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        TimelineClip timelineClip = timelineClip();
        moveTo(oldTrack);
        if (timelineClip != null && oldState != null) {
            restore(timelineClip, oldState);
        } else {
            applyRange(timelineClip, oldStartTime, oldEndTime);
        }
        if (timelineClip != null) {
            TimelineAudioService.getInstance().onClipTimeRangeChanged(
                    timelineClip, newStartTime, newEndTime, oldStartTime, oldEndTime, false);
        }
        applied = false;
        LOGGER.debug("[Timeline] 撤销移动片段");
    }

    @Override
    protected void compensateExecute() {
        if (!applied) {
            return;
        }
        TimelineClip timelineClip = timelineClip();
        moveTo(oldTrack);
        if (timelineClip != null && oldState != null) {
            restore(timelineClip, oldState);
        } else {
            applyRange(timelineClip, oldStartTime, oldEndTime);
        }
        applied = false;
    }

    @Override
    protected void compensateUndo() {
        TimelineClip timelineClip = timelineClip();
        moveTo(newTrack);
        if (timelineClip != null && newState != null) {
            restore(timelineClip, newState);
        } else {
            applyRange(timelineClip, newStartTime, newEndTime);
        }
        applied = true;
    }

    @Override
    public String getDescription() {
        return String.format("移动片段 (%.2fs -> %.2fs)", oldStartTime, newStartTime);
    }

    @Override
    public boolean canUndo() {
        return applied;
    }
}
