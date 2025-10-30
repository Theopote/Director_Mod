package com.chronoblocks.ui.timeline;

import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Timeline 渲染器
 * 负责所有 ImGui 绘制逻辑，与业务数据完全解耦
 *
 * <p>该版本支持将标尺与轨道区域拆分渲染，以便外部面板实现独立滚动。</p>
 */
public class TimelineRenderer {

    // ========== 颜色常量缓存 ==========
    private static final int COLOR_KEYFRAME = rgba(255, 220, 64, 255);
    private static final int COLOR_KEYFRAME_HOVER = rgba(255, 240, 100, 255);
    private static final int COLOR_PLAYHEAD = rgba(255, 80, 80, 220);
    private static final int COLOR_PLAYHEAD_SHADOW = rgba(255, 80, 80, 100);
    private static final int COLOR_SELECTED_BORDER = rgba(255, 255, 80, 255);
    private static final int COLOR_BG_DARK = rgba(30, 30, 30, 220);
    private static final int COLOR_BG_MEDIUM = rgba(45, 45, 45, 200);
    private static final int COLOR_RULER_BG = rgba(40, 40, 40, 220);
    private static final int COLOR_TRACK_LABEL_BG = rgba(24, 24, 24, 220);
    private static final int COLOR_RESIZE_HANDLE = rgba(180, 180, 180, 150);
    private static final int COLOR_DROP_HIGHLIGHT = rgba(100, 200, 255, 80);
    private static final int COLOR_TEXT_LIGHT = rgba(220, 220, 220, 255);
    private static final int COLOR_TEXT_MEDIUM = rgba(200, 200, 200, 255);
    private static final int COLOR_TEXT_DARK = rgba(10, 10, 10, 255);

    // ========== 布局常量 ==========
    private static final float TRACK_HEIGHT = 28f;
    private static final float TRACK_GAP = 6f;
    private static final float RULER_HEIGHT = UITheme.Spacing.BUTTON_HEIGHT;
    private static final float RULER_BOTTOM_PADDING = UITheme.Spacing.SMALL;
    private static final float DEFAULT_LEFT_LABEL_WIDTH = 180f;
    private static final float LEFT_LABEL_MIN = 140f;
    private static final float LEFT_LABEL_MAX = 320f;
    private static float leftLabelWidth = DEFAULT_LEFT_LABEL_WIDTH;
    private static final float RESIZE_HANDLE_WIDTH = 6f;
    private static final float TRACK_LABEL_ICON_SPACING = 6f;
    private static final float TRACK_LABEL_TEXT_OFFSET = 8f;
    private static final float CONTROL_ICON_SIZE = 16f;

    private boolean resizingLeftSection = false;

    private final List<TrackControlHit> trackControlHits = new ArrayList<>();

    private final TimelineModel model;

    public TimelineRenderer(TimelineModel model) {
        this.model = model;
    }

    // ========== 主渲染方法 ==========

    /**
     * 渲染整个时间线画布（保留向后兼容）
     */
    public void renderCanvas(ImDrawList drawList, float winX, float winY, float winW, float canvasHeight) {
        float headerHeight = getHeaderHeight();
        float trackViewportHeight = Math.max(0f, canvasHeight - headerHeight);

        updateLeftSectionWidth(winX, winY, canvasHeight);
        renderTimelineHeader(drawList, winX, winY, winW);
        if (trackViewportHeight > 0f) {
            renderTrackArea(drawList, winX, winY + headerHeight, winW, trackViewportHeight, 0f);
        }
    }

    /**
     * 更新左侧轨道标签区域宽度（简单 clamp，保留接口供交互使用）
     */
    public void updateLeftSectionWidth(float winX, float winY, float totalHeight) {
        // clamp 以确保宽度合法，实际拖动逻辑由面板层驱动
        leftLabelWidth = Math.max(LEFT_LABEL_MIN, Math.min(LEFT_LABEL_MAX, leftLabelWidth));

        // 如果未来需要在渲染层直接处理拖动，可在此拓展。目前仅在鼠标悬停时显示指针（供调试）。
        float dividerX = winX + leftLabelWidth;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        boolean hoveringDivider = mouseX >= dividerX - 2f && mouseX <= dividerX + 2f
                && mouseY >= winY && mouseY <= winY + totalHeight;
        if (hoveringDivider || resizingLeftSection) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        if (!resizingLeftSection && hoveringDivider && ImGui.isMouseClicked(0)) {
            resizingLeftSection = true;
        }
        if (resizingLeftSection) {
            if (ImGui.isMouseDown(0)) {
                float target = mouseX - winX;
                leftLabelWidth = Math.max(LEFT_LABEL_MIN, Math.min(LEFT_LABEL_MAX, target));
            } else {
                resizingLeftSection = false;
            }
        }
    }

    /**
     * 渲染时间线头部（轨道标签背景 + 时间标尺）
     */
    public void renderTimelineHeader(ImDrawList drawList, float winX, float winY, float winW) {
        float labelWidth = leftLabelWidth;
        float contentX = winX + labelWidth;
        float contentW = Math.max(0f, winW - labelWidth);
        float rulerBottom = winY + RULER_HEIGHT;
        float headerBottom = winY + getHeaderHeight();

        // 背景块
        drawList.addRectFilled(winX, winY, winX + winW, headerBottom, COLOR_BG_DARK);
        drawList.addRectFilled(winX, rulerBottom, winX + labelWidth, headerBottom, COLOR_TRACK_LABEL_BG);
        drawList.addRectFilled(contentX, rulerBottom, contentX + contentW, headerBottom, COLOR_BG_MEDIUM);
        drawList.addLine(contentX, winY, contentX, headerBottom, rgba(60, 60, 60, 200));

        drawList.addText(winX + TRACK_LABEL_TEXT_OFFSET, winY + 6, COLOR_TEXT_MEDIUM, "轨道");

        renderRuler(drawList, contentX, winY, contentW, RULER_HEIGHT);
    }

    /**
     * 渲染轨道区域（支持垂直滚动）
     * @param scrollY 子窗口的垂直滚动偏移
     */
    public void renderTrackArea(ImDrawList drawList, float winX, float winY, float winW, float viewportHeight, float scrollY) {
        trackControlHits.clear();

        float labelWidth = leftLabelWidth;
        float contentX = winX + labelWidth;
        float contentW = Math.max(0f, winW - labelWidth);

        drawList.addRectFilled(winX, winY, winX + winW, winY + viewportHeight, COLOR_BG_DARK);
        drawList.addRectFilled(winX, winY, winX + labelWidth, winY + viewportHeight, COLOR_TRACK_LABEL_BG);
        drawList.addLine(contentX, winY, contentX, winY + viewportHeight, rgba(60, 60, 60, 200));

        float startY = winY - scrollY;
        float rowSpacing = TRACK_HEIGHT + TRACK_GAP;
        float currentY = startY;

        for (TimelineTrack track : model.getTracks()) {
            float trackBottom = currentY + TRACK_HEIGHT;
            if (trackBottom >= winY && currentY <= winY + viewportHeight) {
                renderTrack(drawList, track, winX, contentX, currentY, contentW, viewportHeight);
                registerTrackControlAreas(track, winX, currentY);
            }
            currentY += rowSpacing;
        }

        renderPlayhead(drawList, contentX, winY, contentW, viewportHeight);
    }

    // ========== 标尺与轨道渲染 ==========

    /**
     * 渲染时间刻度标尺（专业级设计）
     * 参考 Blender, After Effects 等专业软件，使用主次刻度系统
     */
    public void renderRuler(ImDrawList drawList, float contentX, float topY, float contentW, float rulerHeight) {
        drawList.addRectFilled(contentX, topY, contentX + contentW, topY + rulerHeight, COLOR_RULER_BG);

        drawList.addRectFilledMultiColor(
            contentX, topY, contentX + contentW, topY + 1,
            rgba(65,65,65,200), rgba(65,65,65,200),
            rgba(45,45,45,100), rgba(45,45,45,100)
        );

        drawList.addLine(contentX, topY + rulerHeight, contentX + contentW, topY + rulerHeight,
                        rgba(20,20,20,180), 1.0f);

        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float timeStart = scrollX / pps;
        float timeEnd = (scrollX + contentW) / pps;

        TickLevels tickLevels = calculateTickLevels(pps);
        float majorStep = tickLevels.majorStep;
        float minorStep = tickLevels.minorStep;

        float firstMinorTick = (float)Math.ceil(timeStart / minorStep) * minorStep;
        for (float t = firstMinorTick; t <= timeEnd + minorStep; t += minorStep) {
            float x = contentX + (t * pps) - scrollX;
            if (x < contentX || x > contentX + contentW) continue;

            boolean isMajor = Math.abs(t % majorStep) < 0.001f;
            if (!isMajor) {
                drawList.addLine(x, topY + rulerHeight - 6, x, topY + rulerHeight,
                        rgba(100,100,100,150), 1.0f);
            }
        }

        float firstMajorTick = (float)Math.floor(timeStart / majorStep) * majorStep;
        for (float t = firstMajorTick; t <= timeEnd + majorStep; t += majorStep) {
            float x = contentX + (t * pps) - scrollX;
            if (x < contentX || x > contentX + contentW) continue;

            drawList.addLine(x, topY + 2, x, topY + rulerHeight,
                    rgba(160,160,160,220), 1.5f);

            String timeText = formatTimeLabel(t);
            String frameText = formatFrameLabel(t);
            drawList.addText(x + 3, topY + 3, COLOR_TEXT_LIGHT, timeText);

            float frameTextY = topY + 14;
            if (frameTextY + 8 < topY + rulerHeight - 2) {
                drawList.addText(x + 3, frameTextY, rgba(140,140,140,200), frameText);
            }
        }

        float currentTime = model.getCurrentTime();
        float px = contentX + (currentTime * pps) - scrollX;
        if (px >= contentX && px <= contentX + contentW) {
            drawList.addRectFilled(px - 25, topY, px + 25, topY + 1, rgba(255,100,100,180));
        }
    }

    /**
     * 渲染所有轨道（带类型色彩、锁定与可见性标记）
     */
    private void renderTrack(ImDrawList drawList, TimelineTrack track, float winX,
                             float contentX, float y, float contentW, float viewportHeight) {
        TrackType type = track.getType();
        int bgColor = type != null ? type.getBackgroundColor() : COLOR_BG_MEDIUM;
        int trackBgColor = adjustAlpha(bgColor, 100);
        drawList.addRectFilled(contentX, y, contentX + contentW, y + TRACK_HEIGHT, trackBgColor);

        drawList.addRectFilled(winX, y, contentX, y + TRACK_HEIGHT, COLOR_TRACK_LABEL_BG);
        int typeIndicatorColor = type != null ? type.getClipColor() : rgba(100, 100, 100, 255);
        drawList.addRectFilled(winX, y, winX + 3, y + TRACK_HEIGHT, typeIndicatorColor);

        float labelX = winX + TRACK_LABEL_TEXT_OFFSET;
        if (type != null) {
            drawList.addText(labelX, y + 6, rgba(200, 200, 200, 255), type.getIcon());
            labelX += ImGui.calcTextSize(type.getIcon()).x + TRACK_LABEL_ICON_SPACING;
        }
        drawList.addText(labelX, y + 6, COLOR_TEXT_LIGHT, track.getName());

        float lockX = winX + leftLabelWidth - 45f;
        float visibilityX = winX + leftLabelWidth - 25f;
        if (track.isLocked()) {
            drawList.addText(lockX, y + 6, rgba(200, 180, 100, 255), Icons.LOCK);
        } else {
            drawList.addText(lockX, y + 6, rgba(120, 120, 120, 180), Icons.UNLOCK);
        }
        if (!track.isVisible()) {
            drawList.addText(visibilityX, y + 6, rgba(150, 150, 150, 255), Icons.INVISIBLE);
        } else {
            drawList.addText(visibilityX, y + 6, rgba(180, 180, 180, 200), Icons.VISIBLE);
        }

        for (TimelineClip clip : track.getClips()) {
            renderClip(drawList, clip, track, contentX, y, TRACK_HEIGHT, contentW);
        }

        drawList.addLine(contentX, y + TRACK_HEIGHT, contentX + contentW, y + TRACK_HEIGHT,
                rgba(50, 50, 50, 200), 1.0f);
    }

    /**
     * 渲染单个 Clip
     */
    public void renderClip(ImDrawList drawList, TimelineClip clip, TimelineTrack track,
                           float contentX, float y, float trackH, float contentW) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();

        float x1 = contentX + (clip.getStartTime() * pps) - scrollX;
        float x2 = contentX + (clip.getEndTime() * pps) - scrollX;
        float maxX = contentX + contentW;

        if (x2 < contentX || x1 > maxX) return;

        int col = clip.getColor();

        if (clip == model.getSelectedClip()) {
            drawList.addRect(x1 - 2, y + 1, x2 + 2, y + trackH - 1, COLOR_SELECTED_BORDER, 0.0f, 0, 2.0f);
        }

        drawList.addRectFilled(x1, y + 3, x2, y + trackH - 3, col);
        int highlightCol = lighten(col, 1.3f);
        drawList.addRectFilledMultiColor(
            x1, y + 3, x2, y + 8,
            highlightCol, highlightCol,
            col, col
        );

        drawList.addRect(x1, y + 3, x2, y + trackH - 3, rgba(0,0,0,200));

        float textWidth = ImGui.calcTextSize(clip.getLabel()).x;
        float clipWidth = x2 - x1;
        float textX = x1 + Math.max(6f, (clipWidth - textWidth) * 0.5f);
        drawList.addText(textX, y + 6, COLOR_TEXT_DARK, clip.getLabel());

        float controlWidth = RESIZE_HANDLE_WIDTH;
        float handleLeftX = x1;
        float handleRightX = x2 - controlWidth;
        drawList.addRectFilled(handleLeftX, y + 3, handleLeftX + controlWidth, y + trackH - 3, COLOR_RESIZE_HANDLE);
        drawList.addRectFilled(handleRightX, y + 3, handleRightX + controlWidth, y + trackH - 3, COLOR_RESIZE_HANDLE);

        for (Float keyframe : clip.getKeyframes()) {
            float kx = contentX + (keyframe * pps) - scrollX;
            if (kx < contentX || kx > contentX + contentW) continue;

            boolean hovering = isMouseHoveringRect(kx - 4, y + 6, kx + 4, y + trackH - 6);
            int kfCol = hovering ? COLOR_KEYFRAME_HOVER : COLOR_KEYFRAME;

            drawList.addLine(kx, y + 4, kx, y + trackH - 4, kfCol, 2.0f);
            drawList.addCircleFilled(kx, y + trackH / 2, 3.0f, kfCol);
        }
    }

    /**
     * 兼容旧接口：调整把手已由 {@link #renderClip} 完成，因此这里保留空实现防止调用方报错。
     */
    public void renderResizeHandles(ImDrawList drawList, TimelineClip clip, float contentX, float y, float trackHeight) {
        // no-op
    }

    /**
     * 渲染播放头（带阴影与时间标签）
     */
    public void renderPlayhead(ImDrawList drawList, float contentX, float contentY,
                               float contentW, float contentH) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float currentTime = model.getCurrentTime();

        float px = contentX + (currentTime * pps) - scrollX;

        if (px < contentX - 20 || px > contentX + contentW + 20) return;

        float rulerTop = contentY - RULER_BOTTOM_PADDING - RULER_HEIGHT;
        float handleSize = 8.0f;

        drawList.addTriangleFilled(
            px - handleSize + 2, rulerTop + 2,
            px + handleSize + 2, rulerTop + 2,
            px + 2, rulerTop + handleSize * 1.5f + 2,
            rgba(0, 0, 0, 100)
        );

        drawList.addTriangleFilled(
            px - handleSize, rulerTop,
            px + handleSize, rulerTop,
            px, rulerTop + handleSize * 1.5f,
            rgba(255, 100, 100, 255)
        );

        drawList.addTriangle(
            px - handleSize, rulerTop,
            px + handleSize, rulerTop,
            px, rulerTop + handleSize * 1.5f,
            rgba(180, 60, 60, 255), 1.5f
        );

        String timeText = formatTimeLabel(currentTime);
        float textWidth = ImGui.calcTextSize(timeText).x;
        float boxPadding = 6.0f;
        float boxWidth = textWidth + boxPadding * 2;
        float boxTop = rulerTop;
        float boxBottom = boxTop + RULER_HEIGHT;
        float boxX = px - boxWidth / 2;

        if (boxX < contentX) boxX = contentX;
        if (boxX + boxWidth > contentX + contentW) boxX = contentX + contentW - boxWidth;

        drawList.addRectFilled(boxX, boxTop, boxX + boxWidth, boxBottom,
                rgba(255, 100, 100, 230), 2.0f);
        drawList.addRect(boxX, boxTop, boxX + boxWidth, boxBottom,
                rgba(255, 180, 180, 255), 2.0f, 0, 1.0f);

        float textY = boxTop + (RULER_HEIGHT - ImGui.calcTextSize(timeText).y) * 0.5f;
        drawList.addText(boxX + boxPadding, textY, rgba(255, 255, 255, 255), timeText);

        float lineEndY = contentY + contentH;

        drawList.addLine(px + 1.5f, contentY, px + 1.5f, lineEndY,
                        COLOR_PLAYHEAD_SHADOW, 2.5f);

        drawList.addLine(px, contentY, px, lineEndY,
                        COLOR_PLAYHEAD, 2.0f);

        drawList.addLine(px - 0.5f, contentY, px - 0.5f, lineEndY,
                        rgba(255, 180, 180, 120), 1.0f);
    }

    /**
     * 渲染拖放目标高亮
     */
    public void renderDropHighlight(ImDrawList drawList, int trackIndex, float contentX,
                                    float y, float contentW) {
        drawList.addRectFilled(contentX, y, contentX + contentW, y + TRACK_HEIGHT,
                COLOR_DROP_HIGHLIGHT);
    }

    // ========== Track Control Hit 区域 ==========

    private void registerTrackControlAreas(TimelineTrack track, float winX, float y) {
        float lockX1 = winX + leftLabelWidth - 48f;
        float lockX2 = lockX1 + CONTROL_ICON_SIZE;
        float visibleX1 = winX + leftLabelWidth - 28f;
        float visibleX2 = visibleX1 + CONTROL_ICON_SIZE;
        float y2 = y + CONTROL_ICON_SIZE + 2f;
        trackControlHits.add(new TrackControlHit(track, TrackControlType.LOCK, lockX1, y + 2f, lockX2, y2));
        trackControlHits.add(new TrackControlHit(track, TrackControlType.VISIBILITY, visibleX1, y + 2f, visibleX2, y2));
    }

    public List<TrackControlHit> getTrackControlHitAreas() {
        return trackControlHits;
    }

    public float getTrackContentHeight() {
        int trackCount = model.getTracks().size();
        if (trackCount <= 0) {
            return 0f;
        }
        return trackCount * TRACK_HEIGHT + (trackCount - 1) * TRACK_GAP;
    }

    // ========== 工具方法 ==========

    private static class TickLevels {
        float majorStep;
        float minorStep;

        TickLevels(float major, float minor) {
            this.majorStep = major;
            this.minorStep = minor;
        }
    }

    private TickLevels calculateTickLevels(float pps) {
        if (pps >= 400) {
            return new TickLevels(0.1f, 0.02f);
        } else if (pps >= 200) {
            return new TickLevels(0.25f, 0.05f);
        } else if (pps >= 100) {
            return new TickLevels(0.5f, 0.1f);
        } else if (pps >= 50) {
            return new TickLevels(1.0f, 0.25f);
        } else if (pps >= 25) {
            return new TickLevels(2.0f, 0.5f);
        } else if (pps >= 15) {
            return new TickLevels(5.0f, 1.0f);
        } else {
            return new TickLevels(10.0f, 2.0f);
        }
    }

    private String formatTimeLabel(float t) {
        if (t < 0) t = 0;
        if (t < 60) {
            return String.format("%.2fs", t);
        } else if (t < 3600) {
            int minutes = (int)(t / 60);
            float seconds = t % 60;
            return String.format("%d:%05.2f", minutes, seconds);
        } else {
            int hours = (int)(t / 3600);
            int minutes = (int)((t % 3600) / 60);
            float seconds = t % 60;
            return String.format("%d:%02d:%05.2f", hours, minutes, seconds);
        }
    }

    private String formatFrameLabel(float t) {
        int fps = 20;
        int frame = Math.round(t * fps);
        return String.format("f%d", frame);
    }

    private int lighten(int col, float factor) {
        int a = (col >> 24) & 0xFF;
        int b = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int r = (col) & 0xFF;

        Color color = new Color(r, g, b, a);
        int newR = clampInt((int)(color.getRed() * factor), 0, 255);
        int newG = clampInt((int)(color.getGreen() * factor), 0, 255);
        int newB = clampInt((int)(color.getBlue() * factor), 0, 255);

        return ((a & 0xFF) << 24) | ((newB & 0xFF) << 16) | ((newG & 0xFF) << 8) | ((newR & 0xFF));
    }

    private int adjustAlpha(int col, int newAlpha) {
        int b = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int r = (col) & 0xFF;
        return ((newAlpha & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }

    private boolean isMouseHoveringRect(float x1, float y1, float x2, float y2) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    private int clampInt(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    private static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }

    // ========== Track Control Hit 定义 ==========

    public enum TrackControlType {
        LOCK,
        VISIBILITY
    }

    public static final class TrackControlHit {
        public final TimelineTrack track;
        public final TrackControlType type;
        public final float x1;
        public final float y1;
        public final float x2;
        public final float y2;

        public TrackControlHit(TimelineTrack track, TrackControlType type, float x1, float y1, float x2, float y2) {
            this.track = track;
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    // ========== Getters ==========

    public static float getTrackHeight() { return TRACK_HEIGHT; }
    public static float getTrackGap() { return TRACK_GAP; }
    public static float getRulerHeight() { return RULER_HEIGHT; }
    public static float getHeaderHeight() { return RULER_HEIGHT + RULER_BOTTOM_PADDING; }
    public static float getRulerBottomPadding() { return RULER_BOTTOM_PADDING; }
    public static float getLeftLabelWidth() { return leftLabelWidth; }
    public static float getResizeHandleWidth() { return RESIZE_HANDLE_WIDTH; }
}