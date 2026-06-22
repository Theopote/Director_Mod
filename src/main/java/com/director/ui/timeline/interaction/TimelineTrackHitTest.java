package com.director.ui.timeline.interaction;

import java.util.List;

import com.director.timeline.model.TimelineTrack;
import com.director.ui.timeline.render.TimelineRenderer;
import com.director.ui.timeline.render.TimelineTrackControlHit;

/**
 * 轨道头部命中检测（锁 / 可见性等 Track 控件）
 *
 * <p>依赖 {@link TimelineRenderer} 在渲染阶段生成的
 * {@link TimelineTrackControlHit.Hit} 列表。</p>
 *
 * <p>只负责"计算"，不改动任何 Model / View。</p>
 */
public final class TimelineTrackHitTest {

    private TimelineTrackHitTest() {}

    /**
     * 轨道控件命中结果
     */
    public record TrackControlHitResult(
            TimelineTrack track,
            TimelineTrackControlHit.TrackControlType type
    ) {
        public static TrackControlHitResult none() {
            return new TrackControlHitResult(null, null);
        }

        public boolean isHit() {
            return track != null && type != null;
        }
    }

    /**
     * 命中检测：判断鼠标是否命中轨道头部的锁 / 眼睛按钮。
     *
     * @param renderer 渲染器（内含 Track 控件的 Hit 区域）
     * @param mouseX   鼠标屏幕 X
     * @param mouseY   鼠标屏幕 Y
     * @return 命中结果，如果未命中返回 {@link TrackControlHitResult#none()}
     */
    public static TrackControlHitResult hitTrackControls(
            TimelineRenderer renderer,
            float mouseX,
            float mouseY
    ) {
        if (renderer == null) {
            return TrackControlHitResult.none();
        }

        List<TimelineTrackControlHit.Hit> areas = renderer.getTrackControlHitAreas();
        if (areas == null || areas.isEmpty()) {
            return TrackControlHitResult.none();
        }

        for (TimelineTrackControlHit.Hit hit : areas) {
            if (hit == null || hit.track == null || hit.type == null) {
                continue;
            }

            if (mouseX >= hit.x1 && mouseX <= hit.x2 &&
                mouseY >= hit.y1 && mouseY <= hit.y2) {
                return new TrackControlHitResult(hit.track, hit.type);
            }
        }

        return TrackControlHitResult.none();
    }

    /**
     * 检查轨道控件命中并返回 TimelineHitResult（用于统一命中检测）
     *
     * @param renderer 渲染器
     * @param track    轨道
     * @param trackIndex 轨道索引
     * @param mouseX   鼠标屏幕 X
     * @param mouseY   鼠标屏幕 Y
     * @return 命中结果，如果未命中返回 null
     */
    public static TimelineHitResult checkTrackControlHit(
            TimelineRenderer renderer,
            TimelineTrack track,
            int trackIndex,
            float mouseX,
            float mouseY
    ) {
        TrackControlHitResult result = hitTrackControls(renderer, mouseX, mouseY);
        // 返回一个特殊的命中结果表示控件按钮
        // 注意：这里可能需要根据实际的 TimelineHitResult 结构来调整
        // 暂时返回 null，让调用方处理
        // 控件按钮命中由 InteractionHandler 单独处理
        return null;
    }
}