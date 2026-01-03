package com.chronoblocks.ui.timeline.model.selection;

import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.clip.ClipKeyframe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 时间线关键帧多选占位对象（用于属性面板）
 *
 * <p>当用户一次性选中多个关键帧时，属性面板不应显示某一个关键帧的属性，
 * 否则容易造成“编辑的是谁”的混乱。此对象用于让属性面板显示统一的多选提示（或共同属性）。</p>
 */
public final class TimelineKeyframeMultiSelection {

    private final List<ClipKeyframe> clipKeyframes;
    private final List<TimelineSelectable> automationKeyframes;

    public TimelineKeyframeMultiSelection(List<ClipKeyframe> clipKeyframes,
                                          List<TimelineSelectable> automationKeyframes) {
        this.clipKeyframes = clipKeyframes != null ? new ArrayList<>(clipKeyframes) : new ArrayList<>();
        this.automationKeyframes = automationKeyframes != null ? new ArrayList<>(automationKeyframes) : new ArrayList<>();
    }

    public int getTotalCount() {
        return clipKeyframes.size() + automationKeyframes.size();
    }

    public List<ClipKeyframe> getClipKeyframes() {
        return Collections.unmodifiableList(clipKeyframes);
    }

    public List<TimelineSelectable> getAutomationKeyframes() {
        return Collections.unmodifiableList(automationKeyframes);
    }
}


