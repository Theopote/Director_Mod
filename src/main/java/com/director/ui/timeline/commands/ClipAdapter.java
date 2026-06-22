package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineClip;

/**
 * {@link TimelineClip} 到 {@link MoveClipCommand.Clip} 的适配器。
 */
public class ClipAdapter implements MoveClipCommand.Clip {
    private final TimelineClip clip;

    public ClipAdapter(TimelineClip clip) {
        this.clip = clip;
    }

    public TimelineClip getTimelineClip() {
        return clip;
    }

    @Override
    public float getStartTime() {
        return clip.getStartTime();
    }

    @Override
    public void setStartTime(float time) {
        clip.setStartTime(time);
    }

    @Override
    public float getEndTime() {
        return clip.getEndTime();
    }

    @Override
    public void setEndTime(float time) {
        clip.setEndTime(time);
    }

    @Override
    public float getLength() {
        return clip.getLength();
    }
}


