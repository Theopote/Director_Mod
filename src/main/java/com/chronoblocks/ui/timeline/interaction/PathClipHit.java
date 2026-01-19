package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip;

/**
 * Path Clip 命中检测结果
 * 
 * <p>用于描述鼠标点击命中的 Path Clip 区域（主体、左边缘、右边缘）。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public record PathClipHit(
        CameraPathClip clip,
        TimelineTrack track,
        int trackIndex,
        HitZone zone
) {
    
    /**
     * 命中区域类型
     */
    public enum HitZone {
        /** 块主体（拖动移动） */
        BODY,
        /** 左边缘（拉伸开始时间） */
        RESIZE_L,
        /** 右边缘（拉伸结束时间） */
        RESIZE_R
    }
    
    public static PathClipHit body(CameraPathClip clip, TimelineTrack track, int trackIndex) {
        return new PathClipHit(clip, track, trackIndex, HitZone.BODY);
    }
    
    public static PathClipHit resizeLeft(CameraPathClip clip, TimelineTrack track, int trackIndex) {
        return new PathClipHit(clip, track, trackIndex, HitZone.RESIZE_L);
    }
    
    public static PathClipHit resizeRight(CameraPathClip clip, TimelineTrack track, int trackIndex) {
        return new PathClipHit(clip, track, trackIndex, HitZone.RESIZE_R);
    }
}
