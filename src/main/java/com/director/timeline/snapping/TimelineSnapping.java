package com.director.timeline.snapping;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;

/**
 * 时间线吸附工具类（Director 风格）
 *
 * <p>功能：
 * <ul>
 *   <li>吸附到网格（时间间隔）</li>
 *   <li>吸附到同一轨道或全局的片段边缘</li>
 *   <li>吸附到关键帧</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>只负责计算，不关心配置</li>
 *   <li>配置全部由 TimelineModel → TimelineSnapSettings 统一管理</li>
 *   <li>使用统一的 Snap API（model.snapToGrid()、model.snapToClipEdges() 等）</li>
 * </ul>
 *
 * @author Director Team
 * @version 2.0 - 统一配置系统
 */
public final class TimelineSnapping {

    private TimelineSnapping() {}

    /**
     * 吸附结果
     */
    public static final class Result {
        public final float snappedTime;
        public final boolean snappedToGrid;
        public final boolean snappedToClipEdge;
        public final boolean snappedToKeyframe;

        public Result(float snappedTime,
                      boolean snappedToGrid,
                      boolean snappedToClipEdge,
                      boolean snappedToKeyframe) {
            this.snappedTime = snappedTime;
            this.snappedToGrid = snappedToGrid;
            this.snappedToClipEdge = snappedToClipEdge;
            this.snappedToKeyframe = snappedToKeyframe;
        }
    }

    /**
     * 对时间进行吸附（使用统一配置系统）。
     *
     * <p>使用 TimelineModel 的统一 Snap API：
     * <ul>
     *   <li>model.getSnapSettings() - 获取配置对象</li>
     *   <li>model.snapToGrid() - 网格吸附</li>
     *   <li>model.snapToClipEdges() - 检查是否吸附片段边缘</li>
     *   <li>model.snapToKeyframes() - 检查是否吸附关键帧</li>
     *   <li>model.getSnapPixelThreshold() - 获取像素阈值</li>
     * </ul>
     *
     * @param model           时间线数据模型
     * @param rawTime         原始时间（秒）
     * @param track           当前所在轨道（可为空，为空则只做全局吸附）
     * @param activeClip      当前正在拖动的片段（避免吸附到自身边缘；可为空）
     * @param pixelThreshold 像素阈值（如果为 0 或负数，则使用 model 的默认阈值）
     * @return 吸附结果
     */
    public static Result snapTime(TimelineModel model, float rawTime, TimelineTrack track,
                                  TimelineClip activeClip, float pixelThreshold) {
        return snapTime(model, rawTime, track, activeClip, pixelThreshold, activeClip);
    }

    public static Result snapTime(TimelineModel model, float rawTime, TimelineTrack track,
                                  TimelineClip activeClip, float pixelThreshold, Object excluded) {
        if (model == null || !model.getSnapSettings().isEnabled() || model.getPixelsPerSecond() <= 0.0001f)
            return new Result(rawTime, false, false, false);
        float threshold = (pixelThreshold > 0 ? pixelThreshold : model.getSnapPixelThreshold()) / model.getPixelsPerSecond();
        Candidate best = new Candidate(rawTime, threshold);
        if (track != null) {
            collect(model, track, activeClip, excluded, best);
            if (best.kind != 0) return best.result();
        }
        if (model.isSnapToGridEnabled()) best.offer(model.snapToGrid(rawTime), 1);
        for (TimelineTrack item : model.getAllTracks()) collect(model, item, activeClip, excluded, best);
        return best.result();
    }

    private static void collect(TimelineModel model, TimelineTrack track, TimelineClip activeClip,
                                Object excluded, Candidate best) {
        for (TimelineClip clip : track.getClips()) {
            if (clip == null) continue;
            if (model.snapToClipEdges() && clip != activeClip && clip != excluded) {
                best.offer(clip.getStartTime(), 2); best.offer(clip.getEndTime(), 2);
            }
            if (model.snapToKeyframes() && clip != excluded) {
                for (var key : clip.getClipKeyframes()) if (key != excluded) best.offer(key.getTime(), 3);
                for (Float time : clip.getKeyframeTimes()) {
                    if (time == null) continue;
                    if (excluded instanceof com.director.timeline.model.clip.ClipKeyframe key
                            && clip.getClipKeyframes().contains(key) && Math.abs(time - key.getTime()) < 0.0001f) continue;
                    best.offer(time, 3);
                }
            }
        }
        if (model.snapToKeyframes())
            for (var key : track.getAutomationKeyframes()) if (key != excluded) best.offer(key.getStartTime(), 3);
        for (var path : track.getCameraPathClips()) {
            if (path == null || !path.isEnabled() || path == excluded) continue;
            if (model.snapToClipEdges()) { best.offer(path.getStartTime(), 2); best.offer(path.getEndTime(), 2); }
            if (model.snapToKeyframes() && path instanceof com.director.timeline.model.camera.path.CustomPathClip custom)
                for (var key : custom.getKeyframes()) if (key != excluded) best.offer(key.getTime(), 3);
        }
    }

    private static final class Candidate {
        final float raw;
        final float threshold;
        float time;
        float distance = Float.POSITIVE_INFINITY;
        int kind;
        Candidate(float raw, float threshold) { this.raw = raw; this.time = raw; this.threshold = threshold; }
        void offer(float candidate, int type) {
            float delta = Math.abs(candidate - raw);
            if (delta <= threshold && delta < distance) { time = candidate; distance = delta; kind = type; }
        }
        Result result() { return new Result(time, kind == 1, kind == 2, kind == 3); }
    }
}
