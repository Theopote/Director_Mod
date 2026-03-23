package com.director.ui.timeline.snapping;

import java.util.List;

import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;

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
    public static Result snapTime(TimelineModel model,
                                  float rawTime,
                                  TimelineTrack track,
                                  TimelineClip activeClip,
                                  float pixelThreshold) {
        if (model == null) {
            return new Result(rawTime, false, false, false);
        }

        // 使用统一配置系统
        TimelineSnapSettings settings = model.getSnapSettings();
        if (!settings.isEnabled()) {
            return new Result(rawTime, false, false, false);
        }

        float pps = model.getPixelsPerSecond();
        if (pps <= 0.0001f) {
            return new Result(rawTime, false, false, false);
        }

        // 像素阈值 -> 时间阈值（优先使用传入的参数，否则使用 model 的默认值）
        float effectiveThreshold = pixelThreshold > 0 
            ? pixelThreshold 
            : model.getSnapPixelThreshold();
        float timeThreshold = effectiveThreshold / pps;

        float bestTime = rawTime;
        float bestDelta = Float.MAX_VALUE;
        boolean snappedGrid = false;
        boolean snappedClip = false;
        boolean snappedKey = false;

        // 0) 优先同轨道吸附（片段边缘 / 关键帧）
        if (track != null) {
            if (model.snapToClipEdges()) {
                for (TimelineClip clip : track.getClips()) {
                    if (clip == null) continue;
                    if (activeClip != null && clip == activeClip) continue;

                    float start = clip.getStartTime();
                    float end = clip.getEndTime();

                    float ds = Math.abs(start - rawTime);
                    if (ds < bestDelta && ds <= timeThreshold) {
                        bestTime = start;
                        bestDelta = ds;
                        snappedGrid = false;
                        snappedClip = true;
                        snappedKey = false;
                    }

                    float de = Math.abs(end - rawTime);
                    if (de < bestDelta && de <= timeThreshold) {
                        bestTime = end;
                        bestDelta = de;
                        snappedGrid = false;
                        snappedClip = true;
                        snappedKey = false;
                    }
                }
            }

            if (model.snapToKeyframes()) {
                for (TimelineClip clip : track.getClips()) {
                    if (clip == null) continue;
                    for (Float kf : clip.getKeyframeTimes()) {
                        if (kf == null) continue;
                        float dk = Math.abs(kf - rawTime);
                        if (dk < bestDelta && dk <= timeThreshold) {
                            bestTime = kf;
                            bestDelta = dk;
                            snappedGrid = false;
                            snappedClip = false;
                            snappedKey = true;
                        }
                    }
                }
            }

            if (snappedClip || snappedKey) {
                return new Result(bestTime, snappedGrid, snappedClip, snappedKey);
            }
        }
        // 1) 网格吸附（使用统一 API）
        if (model.isSnapToGridEnabled()) {
            float gridCandidate = model.snapToGrid(rawTime);
            float gridDelta = Math.abs(gridCandidate - rawTime);
            if (gridDelta < bestDelta && gridDelta <= timeThreshold) {
                bestTime = gridCandidate;
                bestDelta = gridDelta;
                snappedGrid = true;
            }
        }

        // 2) 片段边缘吸附（使用统一 API 检查开关）
        // 如果 track 不为空，可以限制只吸附当前轨道（可选功能）
        if (model.snapToClipEdges()) {
            List<TimelineTrack> tracks = model.getAllTracks();
            if (tracks != null && !tracks.isEmpty()) {
                for (TimelineTrack t : tracks) {
                    // 可选：如果只想吸附同轨道，取消下面的注释
                    // if (track != null && t != track) continue;
                    
                    for (TimelineClip clip : t.getClips()) {
                        if (clip == null) continue;

                        // 避免吸附到正在拖动的那个 clip 自己的边缘
                        // 使用对象引用比较（更安全，避免 ID 比较的问题）
                        if (activeClip != null && clip == activeClip) {
                            continue;
                        }

                        float start = clip.getStartTime();
                        float end = clip.getEndTime();

                        float ds = Math.abs(start - rawTime);
                        if (ds < bestDelta && ds <= timeThreshold) {
                            bestTime = start;
                            bestDelta = ds;
                            snappedGrid = false;
                            snappedClip = true;
                            snappedKey = false;
                        }

                        float de = Math.abs(end - rawTime);
                        if (de < bestDelta && de <= timeThreshold) {
                            bestTime = end;
                            bestDelta = de;
                            snappedGrid = false;
                            snappedClip = true;
                            snappedKey = false;
                        }
                    }
                }
            }
        }

        // 3) 关键帧吸附（使用统一 API 检查开关）
        if (model.snapToKeyframes()) {
            List<TimelineTrack> tracks = model.getAllTracks();
            if (tracks != null && !tracks.isEmpty()) {
                for (TimelineTrack t : tracks) {
                    for (TimelineClip clip : t.getClips()) {
                        if (clip == null) continue;
                        for (Float kf : clip.getKeyframeTimes()) {
                            if (kf == null) continue;
                            float dk = Math.abs(kf - rawTime);
                            if (dk < bestDelta && dk <= timeThreshold) {
                                bestTime = kf;
                                bestDelta = dk;
                                snappedGrid = false;
                                snappedClip = false;
                                snappedKey = true;
                            }
                        }
                    }
                }
            }
        }

        // 如果什么也没吸附成功，返回原值
        return new Result(bestTime, snappedGrid, snappedClip, snappedKey);
    }
}
