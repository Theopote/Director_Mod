package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.ui.timeline.interaction.TimelineTrackHitTest.TrackControlHitResult;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
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

    private TimelineHitTest() {}

    /**
     * 统一命中检测入口（简化版本，使用渲染器内部状态）
     *
     * @param renderer 渲染器（提供坐标转换、布局常量）
     * @param model    时间线模型
     * @param mouseX   鼠标屏幕 X 坐标
     * @param mouseY   鼠标屏幕 Y 坐标
     * @return 命中结果
     */
    public static TimelineHitResult compute(
            TimelineRenderer renderer,
            TimelineModel model,
            float mouseX,
            float mouseY
    ) {
        if (model == null || renderer == null) {
            return TimelineHitResult.none();
        }

        float time = renderer.xToTime(mouseX);

        // ---------------------------
        // 1) Track Header 命中检测
        // ---------------------------
        for (int i = 0; i < model.getTrackCount(); i++) {
            TimelineTrack track = model.getTrackAt(i);
            if (track == null || !track.isVisible()) {
                continue;
            }

            TimelineRenderer.Rect bounds = renderer.getTrackHeaderBounds(i);

            if (bounds.contains(mouseX, mouseY)) {
                // 检查控制按钮（锁/眼睛）
                TimelineHitResult controlHit = TimelineTrackHitTest.checkTrackControlHit(
                        renderer, track, i, mouseX, mouseY
                );

                if (controlHit != null && controlHit.type() != TimelineHitResult.HitType.NONE) {
                    return controlHit; // 若命中按钮，优先返回按钮命中
                }

                // 非按钮命中 → Track Header
                return TimelineHitResult.track(track, i);
            }
        }

        // ---------------------------
        // 2) Clip 命中检测
        // ---------------------------
        for (int i = 0; i < model.getTrackCount(); i++) {
            TimelineTrack track = model.getTrackAt(i);
            if (track == null || !track.isVisible()) {
                continue;
            }

            List<TimelineClip> clips = track.getClips();
            if (clips == null || clips.isEmpty()) {
                continue;
            }

            for (TimelineClip clip : clips) {
                TimelineRenderer.Rect rect = renderer.getClipRect(track, clip, i);

                if (rect.contains(mouseX, mouseY)) {
                    // 是否命中关键帧（优先于 clip）
                    int keyIndex = hitKeyframe(renderer, track, clip, mouseX, mouseY);
                    if (keyIndex >= 0) {
                        var keyframeTimes = clip.getKeyframes();
                        if (keyframeTimes != null && !keyframeTimes.isEmpty()) {
                            int idx = 0;
                            for (Float kfTime : keyframeTimes) {
                                if (idx == keyIndex && kfTime != null) {
                                    return TimelineHitResult.keyframe(track, clip, kfTime, i, keyIndex);
                                }
                                idx++;
                            }
                        }
                    }

                    return TimelineHitResult.clip(track, clip, time, i);
                }
            }
        }

        // ---------------------------
        // 3) Keyframe (未命中 clip 背景的孤立关键帧)
        // ---------------------------
        for (int i = 0; i < model.getTrackCount(); i++) {
            TimelineTrack track = model.getTrackAt(i);
            if (track == null || !track.isVisible()) {
                continue;
            }

            List<TimelineClip> clips = track.getClips();
            if (clips == null || clips.isEmpty()) {
                continue;
            }

            for (TimelineClip clip : clips) {
                int kfIndex = hitKeyframe(renderer, track, clip, mouseX, mouseY);
                if (kfIndex >= 0) {
                    var keyframeTimes = clip.getKeyframes();
                    if (keyframeTimes != null && !keyframeTimes.isEmpty()) {
                        int idx = 0;
                        for (Float kfTime : keyframeTimes) {
                            if (idx == kfIndex && kfTime != null) {
                                return TimelineHitResult.keyframe(track, clip, kfTime, i, kfIndex);
                            }
                            idx++;
                        }
                    }
                }
            }
        }

        // ---------------------------
        // 4) 空白区域
        // ---------------------------
        return TimelineHitResult.empty(time);
    }

    private static int hitKeyframe(
            TimelineRenderer renderer,
            TimelineTrack track,
            TimelineClip clip,
            float mouseX,
            float mouseY
    ) {
        var kfs = clip.getKeyframes();
        if (kfs == null || kfs.isEmpty()) {
            return -1;
        }

        int index = 0;
        for (Float t : kfs) {
            if (t == null || t < 0) {
                index++;
                continue;
            }

            TimelineRenderer.Circle c = renderer.getKeyframeCircle(track, clip, t);

            if (c.contains(mouseX, mouseY)) {
                return index;
            }
            index++;
        }
        return -1;
    }

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
        float bestTime = -1f;
        int bestIndex = -1;

        for (TimelineClip clip : clips) {
            if (clip.getKeyframes().isEmpty()) continue;

            float centerY = trackTop + (TimelineRenderer.getTrackHeight() / 2.0f);

            int idx = 0;
            for (Float kf : clip.getKeyframes()) {
                if (kf == null) {
                    idx++;
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
                    bestIndex = idx;
                }
                idx++;
            }
        }

        if (bestClip != null) {
            return TimelineHitResult.keyframe(track, bestClip, bestTime, trackIndex, bestIndex);
        }

        return TimelineHitResult.none();
    }

    /**
     * 检测轨道级自动化关键帧的命中（摄像机关键帧、天气关键帧等）
     */
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
        if (track == null || !track.hasAutomationKeyframes()) {
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
