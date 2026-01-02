package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.ui.timeline.interaction.TimelineTrackHitTest.TrackControlHitResult;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.render.TimelineRenderer;

import java.util.List;

/**
 * 统一时间线命中检测工具（ChronoBlocks 风格）
 *
 * <p>统一入口：TimelineHitTest.compute(renderer, model, mouseX, mouseY)</p>
 *
 * <p>负责命中：</p>
 * <ul>
 *   <li>Track Header（轨道头部）</li>
 *   <li>Track 控制按钮（锁 / 眼睛）</li>
 *   <li>Clip</li>
 *   <li>Keyframe</li>
 *   <li>空区域</li>
 * </ul>
 */
public final class TimelineHitTest {


    /**
     * 统一命中检测入口。
     *
     * @param renderer      渲染器（提供坐标转换、布局常量）
     * @param model         时间线模型
     * @param mouseX        鼠标屏幕 X 坐标
     * @param mouseY        鼠标屏幕 Y 坐标
     * @param contentX      轨道内容区域的屏幕 X 起点（== winX + leftLabelWidth）
     * @param trackAreaTop  轨道区域顶部屏幕 Y（即 renderTrackArea 的 winY）
     * @param contentW      轨道内容区域宽度
     * @param viewportHeight 轨道可视区域高度
     * @param scrollY       垂直滚动偏移（与 TimelineRenderer.renderTrackArea 使用的一致）
     * @param trackControlHit 轨道控件命中结果（用于排除控件按钮区域）
     */
    public static TimelineHitResult compute(
            TimelineRenderer renderer,
            TimelineModel model,
            float mouseX,
            float mouseY,
            float contentX,
            float trackAreaTop,
            float contentW,
            float viewportHeight,
            float scrollY,
            TrackControlHitResult trackControlHit
    ) {
        // 不在轨道可视区域内
        // ★ 关键修复：对于第一行轨道，即使mouseY刚好等于trackAreaTop，也应该能通过检查
        // 使用 < 和 > 而不是 <= 和 >=，确保边界点（mouseY == trackAreaTop）也能通过检查
        // 这样可以确保第一行轨道在trackAreaTop位置也能被检测到
        if (mouseY < trackAreaTop || mouseY > trackAreaTop + viewportHeight) {
            return TimelineHitResult.none();
        }

        // 转成轨道局部坐标（先确定轨道，再判断区域）
        // ★ 关键修复：对于第一行轨道，即使localY很小（接近0），也应该能正确计算trackIndex
        float localY = mouseY - trackAreaTop;
        
        // ★ 修复：如果localY非常接近0（可能是浮点精度问题），确保能检测到第一行轨道
        // 添加一个小容差来处理边界情况
        if (localY < 0 && localY > -0.5f) {
            localY = 0f; // 将接近0的负值修正为0，确保第一行轨道能被检测到
        }
        
        int trackIndex = renderer.yToTrackIndex(localY, scrollY);
        if (trackIndex < 0 || trackIndex >= model.getTrackCount()) {
            float time = renderer.xToTime(mouseX, contentX);
            return TimelineHitResult.empty(time);
        }

        TimelineTrack track = model.getTrackAt(trackIndex);
        if (track == null || !track.isVisible()) {
            float time = renderer.xToTime(mouseX, contentX);
            return TimelineHitResult.empty(time);
        }

        float rowHeight = TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        // ★ 关键修复：轨道Y坐标计算应该与getTrackHeaderBounds保持一致
        // 公式：trackY = trackAreaTop + (trackIndex * rowHeight - scrollY)
        // 这样可以确保第一行轨道（trackIndex=0）当scrollY=0时，trackY = trackAreaTop
        float trackY = trackAreaTop + (trackIndex * rowHeight - scrollY);
        float trackBottom = trackY + TimelineRenderer.getTrackHeight();

        // 确保鼠标确实在该轨道垂直范围内
        // ★ 修复：使用 <= 和 >= 而不是 < 和 >，确保边界点也能通过检查
        // 这样可以确保第一行轨道在trackAreaTop位置也能被检测到
        if (mouseY < trackY || mouseY > trackBottom) {
            float time = renderer.xToTime(mouseX, contentX);
            return TimelineHitResult.empty(time);
        }

        // 2) 判定是否命中 TrackHeader（必须在 clip/keyframe 前，不然会被内容误判）
        // 使用 getTrackHeaderBounds 进行精确检测
        TimelineRenderer.Rect headerRect = renderer.getTrackHeaderBounds(trackIndex, contentX, trackAreaTop, scrollY);
        if (headerRect.contains(mouseX, mouseY)) {
            // 但要排除 TrackControl 按钮（锁 / 眼睛）已经被检测过
            // 若 trackControlHit 不为空且命中，则表示点击了控件按钮，不返回 TRACK_HEADER
            if (trackControlHit != null && trackControlHit.isHit()) {
                return TimelineHitResult.none();
            }
            // 否则返回 TRACK_HEADER（点击头部空白区域）
            return TimelineHitResult.track(track, trackIndex);
        }

        // 在内容区右侧之外
        if (mouseX > contentX + contentW) {
            float time = renderer.xToTime(mouseX, contentX);
            return TimelineHitResult.empty(time);
        }

        // 时间坐标
        float timeAtMouse = renderer.xToTime(mouseX, contentX);

        // 命中 Keyframe（优先）
        TimelineHitResult keyHit = hitTestKeyframes(renderer, model, track, trackIndex,
                mouseX, mouseY, contentX, trackY, trackBottom);
        if (keyHit.type() == TimelineHitResult.HitType.KEYFRAME) {
            return keyHit;
        }

        // ★ 命中自动化关键帧（轨道级别的关键帧，如摄像机关键帧）
        TimelineHitResult automationKeyHit = hitTestAutomationKeyframes(renderer, model, track, trackIndex,
                mouseX, mouseY, contentX, trackY, trackBottom);
        if (automationKeyHit.type() == TimelineHitResult.HitType.AUTOMATION_KEYFRAME) {
            return automationKeyHit;
        }

        // 命中 Clip
        TimelineHitResult clipHit = hitTestClip(renderer, model, track, trackIndex,
                mouseX, mouseY, contentX, contentW, trackY, trackBottom, timeAtMouse);
        if (clipHit.type() == TimelineHitResult.HitType.CLIP) {
            return clipHit;
        }

        // 空白
        return TimelineHitResult.empty(timeAtMouse);
    }

    private static TimelineHitResult hitTestClip(
            TimelineRenderer renderer,
            TimelineModel model,
            TimelineTrack track,
            int trackIndex,
            float mouseX,
            float mouseY,
            float contentX,
            float contentW,
            float trackTop,
            float trackBottom,
            float timeAtMouse
    ) {
        List<TimelineClip> clips = track.getClips();
        if (clips == null || clips.isEmpty()) {
            return TimelineHitResult.empty(timeAtMouse);
        }

        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();

        for (TimelineClip clip : clips) {
            float x1 = contentX + (clip.getStartTime() * pps) - scrollX;
            float x2 = contentX + (clip.getEndTime() * pps) - scrollX;
            if (x2 < contentX || x1 > contentX + contentW) {
                continue;
            }

            float y1 = trackTop + 3f;
            float y2 = trackBottom - 3f;

            if (mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2) {
                return TimelineHitResult.clip(track, clip, timeAtMouse, trackIndex);
            }
        }

        return TimelineHitResult.empty(timeAtMouse);
    }

    private static TimelineHitResult hitTestKeyframes(
            TimelineRenderer renderer,
            TimelineModel model,
            TimelineTrack track,
            int trackIndex,
            float mouseX,
            float mouseY,
            float contentX,
            float trackTop,
            float trackBottom
    ) {
        List<TimelineClip> clips = track.getClips();
        if (clips == null || clips.isEmpty()) {
            return TimelineHitResult.none();
        }

        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();

        float bestDistSq = Float.MAX_VALUE;
        TimelineClip bestClip = null;
        com.chronoblocks.ui.timeline.model.clip.ClipKeyframe bestClipKeyframe = null;
        float bestTime = -1f;

        for (TimelineClip clip : clips) {
            float centerY = trackTop + (TimelineRenderer.getTrackHeight() / 2.0f);

            List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> clipKeyframes = clip.getClipKeyframes();
            if (clipKeyframes != null && !clipKeyframes.isEmpty()) {
                for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : clipKeyframes) {
                    if (kf == null) {
                        continue;
                    }
                    float kfTime = kf.getTime();
                    float kx = contentX + (kfTime * pps) - scrollX;
                    float dx = mouseX - kx;
                    float dy = mouseY - centerY;
                    float distSq = dx * dx + dy * dy;

                    float r = TimelineRenderer.getKeyframeHitRadius();
                    if (distSq <= r * r && distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestClip = clip;
                        bestTime = kfTime;
                        bestClipKeyframe = kf;
                    }
                }
                continue;
            }

            if (clip.getKeyframes().isEmpty()) {
                continue;
            }

            for (Float kf : clip.getKeyframes()) {
                if (kf == null) {
                        continue;
                }

                float kx = contentX + (kf * pps) - scrollX;
                float dx = mouseX - kx;
                float dy = mouseY - centerY;
                float distSq = dx * dx + dy * dy;

                float r = TimelineRenderer.getKeyframeHitRadius();
                if (distSq <= r * r && distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestClip = clip;
                    bestTime = kf;
                    bestClipKeyframe = null;
                }
            }
        }

        if (bestClip != null) {
            if (bestClipKeyframe != null) {
                return TimelineHitResult.keyframe(track, bestClip, bestClipKeyframe, trackIndex);
            }
            if (bestTime >= 0f) {
                final float EPS = 0.001f;
                com.chronoblocks.ui.timeline.model.clip.ClipKeyframe clipKeyframe =
                        bestClip.findKeyframeAt(bestTime, EPS);
                if (clipKeyframe != null) {
                    return TimelineHitResult.keyframe(track, bestClip, clipKeyframe, trackIndex);
                }
                clipKeyframe = new com.chronoblocks.ui.timeline.model.clip.ClipKeyframe(bestTime);
                return TimelineHitResult.keyframe(track, bestClip, clipKeyframe, trackIndex);
            }
        }

        return TimelineHitResult.none();
    }

    private static TimelineHitResult hitTestAutomationKeyframes(
            TimelineRenderer renderer,
            TimelineModel model,
            TimelineTrack track,
            int trackIndex,
            float mouseX,
            float mouseY,
            float contentX,
            float trackTop,
            float trackBottom
    ) {
        if (track == null || track.getType() == TrackType.AUDIO || !track.hasAutomationKeyframes()) {
            return TimelineHitResult.none();
        }

        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float centerY = trackTop + (TimelineRenderer.getTrackHeight() / 2.0f);

        float bestDistSq = Float.MAX_VALUE;
        TimelineSelectable bestKeyframe = null;

        for (TimelineSelectable keyframe : track.getAutomationKeyframes()) {
            if (keyframe == null) continue;

            float keyTime = keyframe.getStartTime();
            float kx = contentX + (keyTime * pps) - scrollX;
            float dx = mouseX - kx;
            float dy = mouseY - centerY;
            float distSq = dx * dx + dy * dy;

            float r = TimelineRenderer.getKeyframeHitRadius();
            if (distSq <= r * r && distSq < bestDistSq) {
                bestDistSq = distSq;
                bestKeyframe = keyframe;
            }
        }

        if (bestKeyframe != null) {
            return TimelineHitResult.automationKeyframe(track, bestKeyframe, trackIndex);
        }

        return TimelineHitResult.none();
    }
}
