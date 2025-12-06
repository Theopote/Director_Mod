package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.clip.ClipKeyframe;

/**
 * 命中检测结果，用于统一描述鼠标点击命中的元素。
 * 
 * <p>⭐ v2.0 修复：添加 ClipKeyframe 字段，确保 Clip 内关键帧有独立的对象引用。
 */
public record TimelineHitResult(
        HitType type,
        TimelineTrack track,
        TimelineClip clip,
        ClipKeyframe clipKeyframe,  // ⭐ 新增：Clip 内关键帧对象
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
        return new TimelineHitResult(HitType.NONE, null, null, null, null, -1f, -1, -1);
    }

    public static TimelineHitResult empty(float time) {
        return new TimelineHitResult(HitType.EMPTY, null, null, null, null, time, -1, -1);
    }

    public static TimelineHitResult track(TimelineTrack track, int trackIndex) {
        return new TimelineHitResult(HitType.TRACK_HEADER, track, null, null, null, -1f, trackIndex, -1);
    }

    public static TimelineHitResult clip(TimelineTrack track, TimelineClip clip, float time, int trackIndex) {
        return new TimelineHitResult(HitType.CLIP, track, clip, null, null, time, trackIndex, -1);
    }

    /**
     * 创建 Clip 内关键帧的命中结果
     * 
     * @param track 轨道
     * @param clip 片段
     * @param clipKeyframe ClipKeyframe 对象（不能为 null）
     * @param trackIndex 轨道索引
     */
    public static TimelineHitResult keyframe(TimelineTrack track, TimelineClip clip, ClipKeyframe clipKeyframe, int trackIndex) {
        if (clipKeyframe == null) {
            throw new IllegalArgumentException("ClipKeyframe cannot be null");
        }
        return new TimelineHitResult(HitType.KEYFRAME, track, clip, clipKeyframe, null, 
                clipKeyframe.getTime(), trackIndex, -1);
    }

    /**
     * 创建自动化关键帧的命中结果（轨道级别的关键帧，如摄像机关键帧）
     */
    public static TimelineHitResult automationKeyframe(TimelineTrack track, TimelineSelectable keyframe, int trackIndex) {
        return new TimelineHitResult(HitType.AUTOMATION_KEYFRAME, track, null, null, keyframe, 
                keyframe != null ? keyframe.getStartTime() : -1f, trackIndex, -1);
    }
}