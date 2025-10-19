package com.chronoblocks.ui.timeline;

import com.chronoblocks.ui.theme.Icons;
import imgui.ImDrawList;
import imgui.ImGui;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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
     * 渲染时间刻度标尺（专业级设计）
     * 参考 Blender, After Effects 等专业软件，使用主次刻度系统
     */
    public void renderRuler(ImDrawList drawList, float contentX, float topY, float contentW, float rulerHeight) {
        // 背景渐变（更深的颜色，更专业的质感）
        drawList.addRectFilled(contentX, topY, contentX + contentW, topY + rulerHeight, rgba(32, 32, 32, 255));
        
        // 顶部高光线（模拟3D效果）
        drawList.addRectFilledMultiColor(
            contentX, topY, contentX + contentW, topY + 1,
            rgba(65,65,65,200), rgba(65,65,65,200),
            rgba(45,45,45,100), rgba(45,45,45,100)
        );
        
        // 底部阴影线
        drawList.addLine(contentX, topY + rulerHeight, contentX + contentW, topY + rulerHeight, 
                        rgba(20,20,20,180), 1.0f);
        
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float timeStart = scrollX / pps;
        float timeEnd = (scrollX + contentW) / pps;
        
        // 获取主次刻度步长
        TickLevels tickLevels = calculateTickLevels(pps);
        float majorStep = tickLevels.majorStep;
        float minorStep = tickLevels.minorStep;
        
        // 优化：使用 Math.ceil 避免绘制不可见的刻度
        float firstMinorTick = (float)Math.ceil(timeStart / minorStep) * minorStep;
        
        // 先绘制次刻度（细线）
        for (float t = firstMinorTick; t <= timeEnd + minorStep; t += minorStep) {
            float x = contentX + (t * pps) - scrollX;
            if (x < contentX || x > contentX + contentW) continue;
            
            // 检查是否为主刻度
            boolean isMajor = Math.abs(t % majorStep) < 0.001f;
            if (!isMajor) {
                // 次刻度：短细线
                drawList.addLine(x, topY + rulerHeight - 6, x, topY + rulerHeight, 
                               rgba(100,100,100,150), 1.0f);
            }
        }
        
        // 再绘制主刻度（粗线 + 文字）
        float firstMajorTick = (float)Math.floor(timeStart / majorStep) * majorStep;
        for (float t = firstMajorTick; t <= timeEnd + majorStep; t += majorStep) {
            float x = contentX + (t * pps) - scrollX;
            if (x < contentX || x > contentX + contentW) continue;
            
            // 主刻度：长粗线
            drawList.addLine(x, topY + 2, x, topY + rulerHeight, 
                           rgba(160,160,160,220), 1.5f);
            
            // 时间文字（秒数 + 帧数）
            String timeText = formatTimeLabel(t);
            String frameText = formatFrameLabel(t);
            
            // 秒数（大字）
            drawList.addText(x + 3, topY + 3, COLOR_TEXT_LIGHT, timeText);
            
            // 帧数（小字，灰色）
            float frameTextY = topY + 3 + 10;  // 向下偏移
            if (frameTextY + 8 < topY + rulerHeight - 2) {  // 确保不超出边界
                drawList.addText(x + 3, frameTextY, rgba(140,140,140,200), frameText);
            }
        }
        
        // 当前时间指示器背景（半透明亮色）
        float currentTime = model.getCurrentTime();
        float px = contentX + (currentTime * pps) - scrollX;
        if (px >= contentX && px <= contentX + contentW) {
            drawList.addRectFilled(px - 25, topY, px + 25, topY + 1, rgba(255,100,100,180));
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
     * 渲染单个轨道（支持不同类型的视觉效果）
     */
    private void renderTrack(ImDrawList drawList, TimelineTrack track, float winX, 
                            float contentX, float y, float contentW, float contentH) {
        // 根据轨道类型获取背景色
        TrackType type = track.getType();
        int bgColor = type != null ? type.getBackgroundColor() : COLOR_BG_MEDIUM;
        
        // 轨道背景（使用类型专属颜色，增加透明度）
        int trackBgColor = adjustAlpha(bgColor, 100);  // 半透明背景
        drawList.addRectFilled(contentX, y, contentX + contentW, y + TRACK_HEIGHT, trackBgColor);
        
        // 左侧标签区域背景（深色）
        drawList.addRectFilled(winX, y, contentX, y + TRACK_HEIGHT, COLOR_TRACK_LABEL_BG);
        
        // 左侧标签区域的类型标识条（左边缘的彩色竖条）
        int typeIndicatorColor = type != null ? type.getClipColor() : rgba(100, 100, 100, 255);
        drawList.addRectFilled(winX, y, winX + 3, y + TRACK_HEIGHT, typeIndicatorColor);
        
        // 轨道图标（如果有类型）
        if (type != null) {
            String icon = type.getIcon();
            drawList.addText(winX + 8, y + 6, rgba(200, 200, 200, 255), icon);
        }
        
        // 轨道名称（图标后面）
        float nameX = type != null ? winX + 28 : winX + 8;
        drawList.addText(nameX, y + 6, COLOR_TEXT_LIGHT, track.getName());
        
        // 锁定图标
        if (track.isLocked()) {
            drawList.addText(winX + LEFT_LABEL_WIDTH - 45, y + 6, 
                           rgba(200, 180, 100, 255), Icons.LOCK);
        }
        
        // 可见性图标（不可见时显示 INVISIBLE 图标）
        if (!track.isVisible()) {
            drawList.addText(winX + LEFT_LABEL_WIDTH - 25, y + 6, 
                           rgba(150, 150, 150, 255), Icons.INVISIBLE);
        }
        
        // 渲染轨道上的所有片段
        for (TimelineClip clip : track.getClips()) {
            renderClip(drawList, clip, track, contentX, y, TRACK_HEIGHT, contentW);
        }
        
        // 轨道分隔线（底部）
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
        
        // 音频波形可视化（如果是音频轨道）
        if (track != null && track.getType() == TrackType.AUDIO) {
            renderAudioWaveform(drawList, clip, x1, x2, y, trackH);
        }
        
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
     * 渲染音频波形可视化（简化版）
     * 使用混合正弦波生成逼真的伪波形效果
     */
    private void renderAudioWaveform(ImDrawList drawList, TimelineClip clip,
                                    float x1, float x2, float y, float trackH) {
        // 简单的伪波形：使用正弦波模拟
        float centerY = y + trackH / 2;
        float amplitude = (trackH - 10) / 4;  // 波形高度
        int waveColor = rgba(50, 50, 50, 150);  // 半透明深色
        
        float waveWidth = x2 - x1;
        int samples = Math.max(20, (int)(waveWidth / 3));  // 每3像素一个采样点
        float step = waveWidth / samples;
        
        // 使用时间作为随机种子，生成稳定的"伪随机"波形
        float timeOffset = clip.getStartTime() * 100;
        
        for (int i = 0; i < samples - 1; i++) {
            float x = x1 + i * step;
            float nextX = x1 + (i + 1) * step;
            
            // 生成伪随机波形（混合多个频率）
            float phase1 = (x - x1) / waveWidth * 8 + timeOffset;
            float phase2 = (x - x1) / waveWidth * 20 + timeOffset * 0.5f;
            float wave = (float)(Math.sin(phase1) * 0.6 + Math.sin(phase2) * 0.4);
            
            float nextPhase1 = (nextX - x1) / waveWidth * 8 + timeOffset;
            float nextPhase2 = (nextX - x1) / waveWidth * 20 + timeOffset * 0.5f;
            float nextWave = (float)(Math.sin(nextPhase1) * 0.6 + Math.sin(nextPhase2) * 0.4);
            
            float y1 = centerY + wave * amplitude;
            float y2 = centerY + nextWave * amplitude;
            
            // 绘制波形线
            drawList.addLine(x, y1, nextX, y2, waveColor, 1.5f);
        }
    }
    
    /**
     * 渲染关键帧（优化版：批量 hover 检查）
     * 性能优化：先收集可见关键帧，再批量检查 hover 状态
     */
    public void renderKeyframes(ImDrawList drawList, TimelineClip clip, float contentX, 
                               float y, float trackH, float contentW) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float mx = ImGui.getMousePosX();
        float maxX = contentX + contentW;
        
        // 性能优化：先收集可见的关键帧
        List<Float> visibleKeyframes = new ArrayList<>();
        for (Float kf : clip.getKeyframes()) {
            float kx = contentX + (kf * pps) - scrollX;
            if (kx >= contentX && kx <= maxX) {
                visibleKeyframes.add(kf);
            }
        }
        
        // 批量检查 hover（只对可见关键帧检查）
        Float hoveredKeyframe = null;
        if (isMouseHoveringRect(contentX, y, maxX, y + trackH)) {
            for (Float kf : visibleKeyframes) {
                float kx = contentX + (kf * pps) - scrollX;
                if (Math.abs(mx - kx) < 5) {
                    hoveredKeyframe = kf;
                    break;
                }
            }
        }
        
        // 渲染可见的关键帧
        for (Float kf : visibleKeyframes) {
            float kx = contentX + (kf * pps) - scrollX;
            boolean isHovered = (kf.equals(hoveredKeyframe));
            int kfCol = isHovered ? COLOR_KEYFRAME_HOVER : COLOR_KEYFRAME;
            
            // 关键帧竖线
            drawList.addLine(kx, y + 4, kx, y + trackH - 4, kfCol, 2.0f);
            
            // 关键帧圆点
            drawList.addCircleFilled(kx, y + trackH / 2, 3.0f, kfCol);
        }
    }
    
    /**
     * 渲染播放头（专业级设计，参考 Blender）
     */
    public void renderPlayhead(ImDrawList drawList, float contentX, float contentY, 
                              float contentW, float contentH) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float currentTime = model.getCurrentTime();
        
        float px = contentX + (currentTime * pps) - scrollX;
        
        // 只在可见范围内绘制
        if (px < contentX - 20 || px > contentX + contentW + 20) return;
        
        // 标尺顶部区域的三角形手柄（更大更明显）
        float rulerTop = contentY - RULER_HEIGHT;
        float handleSize = 8.0f;
        
        // 手柄阴影（向下和向右偏移2px）
        drawList.addTriangleFilled(
            px - handleSize + 2, rulerTop + 2, 
            px + handleSize + 2, rulerTop + 2, 
            px + 2, rulerTop + handleSize * 1.5f + 2, 
            rgba(0, 0, 0, 100)
        );
        
        // 手柄主体（亮红色）
        drawList.addTriangleFilled(
            px - handleSize, rulerTop, 
            px + handleSize, rulerTop, 
            px, rulerTop + handleSize * 1.5f, 
            rgba(255, 100, 100, 255)
        );
        
        // 手柄边框（深色，增强立体感）
        drawList.addTriangle(
            px - handleSize, rulerTop, 
            px + handleSize, rulerTop, 
            px, rulerTop + handleSize * 1.5f, 
            rgba(180, 60, 60, 255), 1.5f
        );
        
        // 当前时间文本背景框
        String timeText = formatTimeLabel(currentTime);
        float textWidth = ImGui.calcTextSize(timeText).x;
        float boxPadding = 4.0f;
        float boxWidth = textWidth + boxPadding * 2;
        float boxHeight = 16.0f;
        float boxX = px - boxWidth / 2;
        float boxY = rulerTop - boxHeight - 2;
        
        // 确保文本框不超出屏幕
        if (boxX < contentX) boxX = contentX;
        if (boxX + boxWidth > contentX + contentW) boxX = contentX + contentW - boxWidth;
        
        // 时间框阴影
        drawList.addRectFilled(
            boxX + 2, boxY + 2, 
            boxX + boxWidth + 2, boxY + boxHeight + 2, 
            rgba(0, 0, 0, 150), 2.0f
        );
        
        // 时间框背景
        drawList.addRectFilled(
            boxX, boxY, 
            boxX + boxWidth, boxY + boxHeight, 
            rgba(255, 100, 100, 230), 2.0f
        );
        
        // 时间框边框
        drawList.addRect(
            boxX, boxY, 
            boxX + boxWidth, boxY + boxHeight, 
            rgba(255, 180, 180, 255), 2.0f, 0, 1.0f
        );
        
        // 时间文字（白色）
        drawList.addText(boxX + boxPadding, boxY + 2, rgba(255, 255, 255, 255), timeText);
        
        // 播放头垂直线（从标尺底部延伸到轨道底部）
        float lineEndY = contentY + contentH;
        
        // 线条阴影
        drawList.addLine(px + 1.5f, contentY, px + 1.5f, lineEndY,
                        rgba(0, 0, 0, 80), 2.5f);
        
        // 主线（较粗的亮红色）
        drawList.addLine(px, contentY, px, lineEndY,
                        rgba(255, 100, 100, 200), 2.0f);
        
        // 高光线（细的亮色，模拟光泽）
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
    
    // ========== 工具方法 ==========
    
    /**
     * 主次刻度级别
     */
    private static class TickLevels {
        float majorStep;  // 主刻度步长（显示文字）
        float minorStep;  // 次刻度步长（只显示短线）
        
        TickLevels(float major, float minor) {
            this.majorStep = major;
            this.minorStep = minor;
        }
    }
    
    /**
     * 根据缩放级别计算主次刻度步长（参考专业软件）
     */
    private TickLevels calculateTickLevels(float pps) {
        // 目标：主刻度间距约 80-150 像素
        // 次刻度：主刻度的 1/4 或 1/5
        
        if (pps >= 400) {
            // 超高缩放：0.1s 主刻度，0.02s 次刻度（每秒20帧的情况）
            return new TickLevels(0.1f, 0.02f);
        } else if (pps >= 200) {
            // 高缩放：0.25s 主刻度，0.05s 次刻度
            return new TickLevels(0.25f, 0.05f);
        } else if (pps >= 100) {
            // 中高缩放：0.5s 主刻度，0.1s 次刻度
            return new TickLevels(0.5f, 0.1f);
        } else if (pps >= 50) {
            // 中缩放：1s 主刻度，0.25s 次刻度
            return new TickLevels(1.0f, 0.25f);
        } else if (pps >= 25) {
            // 中低缩放：2s 主刻度，0.5s 次刻度
            return new TickLevels(2.0f, 0.5f);
        } else if (pps >= 15) {
            // 低缩放：5s 主刻度，1s 次刻度
            return new TickLevels(5.0f, 1.0f);
        } else {
            // 超低缩放：10s 主刻度，2s 次刻度
            return new TickLevels(10.0f, 2.0f);
        }
    }

    /**
     * 格式化时间标签（秒）
     */
    private String formatTimeLabel(float t) {
        if (t < 0) t = 0;
        
        // 根据时间长度选择格式
        if (t < 60) {
            // 小于1分钟：显示秒
            return String.format("%.2fs", t);
        } else if (t < 3600) {
            // 小于1小时：显示分:秒
            int minutes = (int)(t / 60);
            float seconds = t % 60;
            return String.format("%d:%05.2f", minutes, seconds);
        } else {
            // 超过1小时：显示时:分:秒
            int hours = (int)(t / 3600);
            int minutes = (int)((t % 3600) / 60);
            float seconds = t % 60;
            return String.format("%d:%02d:%05.2f", hours, minutes, seconds);
        }
    }
    
    /**
     * 格式化帧数标签（假设 20 FPS）
     */
    private String formatFrameLabel(float t) {
        int fps = 20;  // Minecraft 的标准 TPS
        int frame = Math.round(t * fps);
        return String.format("f%d", frame);
    }
    
    /**
     * 颜色增亮（优化版：使用 Color 类简化代码）
     */
    private int lighten(int col, float factor) {
        // 提取原始 RGBA 通道
        int a = (col >> 24) & 0xFF;
        int b = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int r = (col) & 0xFF;
        
        // 使用 Color 类简化计算
        Color color = new Color(r, g, b, a);
        int newR = clampInt((int)(color.getRed() * factor), 0, 255);
        int newG = clampInt((int)(color.getGreen() * factor), 0, 255);
        int newB = clampInt((int)(color.getBlue() * factor), 0, 255);
        
        // 重新打包为 ABGR 格式（ImGui 使用的格式）
        return ((a & 0xFF) << 24) | ((newB & 0xFF) << 16) | ((newG & 0xFF) << 8) | ((newR & 0xFF));
    }
    
    /**
     * 调整颜色的透明度
     */
    private int adjustAlpha(int col, int newAlpha) {
        int b = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int r = (col) & 0xFF;
        return ((newAlpha & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
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

