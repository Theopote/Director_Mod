package com.chronoblocks.ui.timeline;

import imgui.ImDrawList;
import imgui.ImGui;

/**
 * Timeline 渲染器
 * 负责所有 ImGui 绘制逻辑，与业务数据完全解耦
 * 
 * @author ChronoBlocks Team
 * @version 6.0
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
    private static final float RULER_HEIGHT = 22f;
    private static final float LEFT_LABEL_WIDTH = 140f;
    private static final float RESIZE_HANDLE_WIDTH = 6f;
    
    private final TimelineModel model;
    
    public TimelineRenderer(TimelineModel model) {
        this.model = model;
    }
    
    // ========== 主渲染方法 ==========
    
    /**
     * 渲染整个时间线画布
     */
    public void renderCanvas(ImDrawList drawList, float winX, float winY, float winW, float canvasHeight) {
        // 绘制背景
        drawList.addRectFilled(winX, winY, winX + winW, winY + canvasHeight, COLOR_BG_DARK);
        
        float contentX = winX + LEFT_LABEL_WIDTH;
        float contentW = winW - LEFT_LABEL_WIDTH;
        float contentY = winY + RULER_HEIGHT;
        float contentH = canvasHeight - RULER_HEIGHT;
        
        // 绘制左侧标签背景
        drawList.addRectFilled(winX, winY + RULER_HEIGHT, contentX, winY + canvasHeight, COLOR_TRACK_LABEL_BG);
        drawList.addText(winX + 8, winY + 6, COLOR_TEXT_MEDIUM, "轨道");
        
        // 渲染标尺
        renderRuler(drawList, contentX, winY, contentW, RULER_HEIGHT);
        
        // 渲染轨道
        renderTracks(drawList, winX, contentX, contentY, contentW, contentH);
        
        // 渲染播放头（最后绘制，在最上层）
        renderPlayhead(drawList, contentX, contentY, contentW, contentH);
    }
    
    /**
     * 渲染时间刻度标尺
     */
    public void renderRuler(ImDrawList drawList, float contentX, float topY, float contentW, float rulerHeight) {
        // 背景渐变
        drawList.addRectFilled(contentX, topY, contentX + contentW, topY + rulerHeight, COLOR_RULER_BG);
        drawList.addRectFilledMultiColor(
            contentX, topY, contentX + contentW, topY + 2,
            rgba(60,60,60,100), rgba(60,60,60,100),
            rgba(40,40,40,0), rgba(40,40,40,0)
        );
        
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float timeStart = scrollX / pps;
        float timeEnd = (scrollX + contentW) / pps;
        
        float step = chooseTickStep(pps);
        float firstTick = (float)Math.floor(timeStart / step) * step;
        
        // 绘制刻度
        for (float t = firstTick; t <= timeEnd + step; t += step) {
            float x = contentX + (t * pps) - scrollX;
            if (x < contentX || x > contentX + contentW) continue;
            
            drawList.addLine(x, topY + 2, x, topY + rulerHeight - 2, rgba(160,160,160,200));
            drawList.addText(x + 2, topY + 2, COLOR_TEXT_MEDIUM, formatTimeLabel(t));
        }
    }
    
    /**
     * 渲染所有轨道
     */
    private void renderTracks(ImDrawList drawList, float winX, float contentX, 
                             float contentY, float contentW, float contentH) {
        float y = contentY;
        
        for (int trackIdx = 0; trackIdx < model.getTracks().size(); trackIdx++) {
            TimelineTrack track = model.getTracks().get(trackIdx);
            
            // 性能优化：跳过不可见的轨道
            if (y + TRACK_HEIGHT < contentY || y > contentY + contentH) {
                y += TRACK_HEIGHT + TRACK_GAP;
                continue;
            }
            
            // 渲染轨道
            renderTrack(drawList, track, winX, contentX, y, contentW, contentH);
            
            y += TRACK_HEIGHT + TRACK_GAP;
        }
    }
    
    /**
     * 渲染单个轨道
     */
    private void renderTrack(ImDrawList drawList, TimelineTrack track, float winX, 
                            float contentX, float y, float contentW, float contentH) {
        // 轨道背景
        drawList.addRectFilled(contentX, y, contentX + contentW, y + TRACK_HEIGHT, COLOR_BG_MEDIUM);
        
        // 轨道名称
        drawList.addText(winX + 8, y + 6, COLOR_TEXT_LIGHT, track.getName());
        
        // 渲染轨道上的所有片段
        for (TimelineClip clip : track.getClips()) {
            renderClip(drawList, clip, contentX, y, TRACK_HEIGHT, contentW);
        }
    }
    
    /**
     * 渲染单个 Clip
     */
    public void renderClip(ImDrawList drawList, TimelineClip clip, float contentX, 
                          float y, float trackH, float contentW) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        
        float x1 = contentX + (clip.getStartTime() * pps) - scrollX;
        float x2 = contentX + (clip.getEndTime() * pps) - scrollX;
        float maxX = contentX + contentW;
        
        // 性能优化：裁剪不可见片段
        if (x2 < contentX || x1 > maxX) return;
        
        int col = clip.getColor();
        
        // 选中边框
        if (clip == model.getSelectedClip()) {
            drawList.addRect(x1 - 2, y + 1, x2 + 2, y + trackH - 1, COLOR_SELECTED_BORDER, 0.0f, 0, 2.0f);
        }
        
        // 片段主体（渐变效果）
        drawList.addRectFilled(x1, y + 3, x2, y + trackH - 3, col);
        int highlightCol = lighten(col, 1.3f);
        drawList.addRectFilledMultiColor(
            x1, y + 3, x2, y + 8,
            highlightCol, highlightCol,
            col, col
        );
        
        // 边框
        drawList.addRect(x1, y + 3, x2, y + trackH - 3, rgba(0,0,0,200));
        
        // 标签
        drawList.addText(x1 + 6, y + 7, COLOR_TEXT_DARK, clip.getLabel());
        
        // 渲染关键帧
        renderKeyframes(drawList, clip, contentX, y, trackH, contentW);
    }
    
    /**
     * 渲染 Clip 的调整把手
     */
    public void renderResizeHandles(ImDrawList drawList, TimelineClip clip, float contentX, float y, float trackH) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        
        float x1 = contentX + (clip.getStartTime() * pps) - scrollX;
        float x2 = contentX + (clip.getEndTime() * pps) - scrollX;
        
        float mx = ImGui.getMousePosX();
        
        boolean hoverLeft = Math.abs(mx - x1) < RESIZE_HANDLE_WIDTH && 
                           isMouseHoveringRect(x1 - 3, y, x1 + 3, y + trackH);
        boolean hoverRight = Math.abs(mx - x2) < RESIZE_HANDLE_WIDTH && 
                            isMouseHoveringRect(x2 - 3, y, x2 + 3, y + trackH);
        
        if (hoverLeft) {
            drawList.addRectFilled(x1 - 1, y + 5, x1 + 1, y + trackH - 5, COLOR_RESIZE_HANDLE);
        }
        
        if (hoverRight) {
            drawList.addRectFilled(x2 - 1, y + 5, x2 + 1, y + trackH - 5, COLOR_RESIZE_HANDLE);
        }
    }
    
    /**
     * 渲染关键帧
     */
    public void renderKeyframes(ImDrawList drawList, TimelineClip clip, float contentX, 
                               float y, float trackH, float contentW) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float mx = ImGui.getMousePosX();
        float maxX = contentX + contentW;
        
        for (Float kf : clip.getKeyframes()) {
            float kx = contentX + (kf * pps) - scrollX;
            
            // 性能优化：只绘制可见的关键帧
            if (kx < contentX || kx > maxX) continue;
            
            boolean hoverKeyframe = Math.abs(mx - kx) < 5 && 
                                   isMouseHoveringRect(kx - 5, y, kx + 5, y + trackH);
            
            int kfCol = hoverKeyframe ? COLOR_KEYFRAME_HOVER : COLOR_KEYFRAME;
            
            // 关键帧竖线
            drawList.addLine(kx, y + 4, kx, y + trackH - 4, kfCol, 2.0f);
            
            // 关键帧圆点
            drawList.addCircleFilled(kx, y + trackH / 2, 3.0f, kfCol);
        }
    }
    
    /**
     * 渲染播放头
     */
    public void renderPlayhead(ImDrawList drawList, float contentX, float contentY, 
                              float contentW, float contentH) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float currentTime = model.getCurrentTime();
        
        float px = contentX + (currentTime * pps) - scrollX;
        
        // 阴影
        drawList.addLine(px + 2, contentY, px + 2, contentY + contentH, 
                        COLOR_PLAYHEAD_SHADOW, 3.0f);
        
        // 主线
        drawList.addLine(px, contentY, px, contentY + contentH, COLOR_PLAYHEAD, 2.0f);
        
        // 三角形手柄
        drawList.addTriangleFilled(px - 6, contentY, px + 6, contentY, px, contentY - 10, 
                                  COLOR_PLAYHEAD);
    }
    
    /**
     * 渲染拖放目标高亮
     */
    public void renderDropHighlight(ImDrawList drawList, int trackIndex, float contentX, 
                                   float y, float contentW) {
        drawList.addRectFilled(contentX, y, contentX + contentW, y + TRACK_HEIGHT, 
                              COLOR_DROP_HIGHLIGHT);
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 选择合适的时间刻度步长
     */
    private float chooseTickStep(float pps) {
        if (pps >= 200) return 0.25f;
        if (pps >= 100) return 0.5f;
        if (pps >= 50) return 1.0f;
        if (pps >= 20) return 5.0f;
        return 10.0f;
    }
    
    /**
     * 格式化时间标签
     */
    private String formatTimeLabel(float t) {
        if (t < 0) t = 0;
        return String.format("%.2fs", t);
    }
    
    /**
     * 颜色增亮
     */
    private int lighten(int col, float factor) {
        int a = (col >> 24) & 0xFF;
        int b = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int r = (col) & 0xFF;
        r = clampInt((int)(r * factor), 0, 255);
        g = clampInt((int)(g * factor), 0, 255);
        b = clampInt((int)(b * factor), 0, 255);
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }
    
    /**
     * 鼠标悬浮检测
     */
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
    
    // ========== Getters ==========
    
    public static float getTrackHeight() { return TRACK_HEIGHT; }
    public static float getTrackGap() { return TRACK_GAP; }
    public static float getRulerHeight() { return RULER_HEIGHT; }
    public static float getLeftLabelWidth() { return LEFT_LABEL_WIDTH; }
    public static float getResizeHandleWidth() { return RESIZE_HANDLE_WIDTH; }
}

