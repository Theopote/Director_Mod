package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

/**
 * 命中检测结果，用于统一描述鼠标点击命中的元素。
 */
public record TimelineHitResult(
        HitType type,
        TimelineTrack track,
        TimelineClip clip,
        TimelineSelectable automationKeyframe,
        float time,
        int trackIndex,
        int keyframeIndex
) {

    public enum HitType {
        NONE,
        EMPTY,
        TRACK_HEADER,
        CLIP,
        KEYFRAME,
        AUTOMATION_KEYFRAME
    }

    public static TimelineHitResult none() {
        return new TimelineHitResult(HitType.NONE, null, null, null, -1f, -1, -1);
    }

    public static TimelineHitResult empty(float time) {
        return new TimelineHitResult(HitType.EMPTY, null, null, null, time, -1, -1);
    }

    public static TimelineHitResult track(TimelineTrack track, int trackIndex) {
        return new TimelineHitResult(HitType.TRACK_HEADER, track, null, null, -1f, trackIndex, -1);
    }

    public static TimelineHitResult clip(TimelineTrack track, TimelineClip clip, float time, int trackIndex) {
        return new TimelineHitResult(HitType.CLIP, track, clip, null, time, trackIndex, -1);
    }

    public static TimelineHitResult keyframe(TimelineTrack track, TimelineClip clip, float time, int trackIndex, int keyframeIndex) {
        return new TimelineHitResult(HitType.KEYFRAME, track, clip, null, time, trackIndex, keyframeIndex);
    }

    /**
     * 创建自动化关键帧的命中结果（轨道级别的关键帧，如摄像机关键帧）
     */
    public static TimelineHitResult automationKeyframe(TimelineTrack track, TimelineSelectable keyframe, int trackIndex) {
        return new TimelineHitResult(HitType.AUTOMATION_KEYFRAME, track, null, keyframe, 
                keyframe != null ? keyframe.getStartTime() : -1f, trackIndex, -1);
    }
}