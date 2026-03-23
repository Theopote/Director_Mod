package com.director.ui.timeline.render;

import com.director.ui.timeline.model.TimelineModel;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线播放头渲染器（v9.0）
 * 
 * <p>职责：
 * <ul>
 *   <li>渲染播放头（三角形手柄 + 垂直红线）</li>
 *   <li>处理播放头的视觉效果（阴影、高光等）</li>
 * </ul>
 * 
 * @author Director Team
 * @version 9.0
 */
public final class TimelinePlayheadRenderer {

    private static final Logger logger = LoggerFactory.getLogger(TimelinePlayheadRenderer.class);

    // 布局常量
    private static final float HANDLE_SIZE = 6.0f;
    private static final float HANDLE_HEIGHT_MULTIPLIER = 1.5f;
    private static final float PLAYHEAD_VISIBILITY_MARGIN = 20f;
    private static final int PLAYHEAD_COLOR_R = 255;
    private static final int PLAYHEAD_COLOR_G = 100;
    private static final int PLAYHEAD_COLOR_B = 100;
    private static final int PLAYHEAD_COLOR_A = 255;
    private static final int PLAYHEAD_BORDER_R = 180;
    private static final int PLAYHEAD_BORDER_G = 60;
    private static final int PLAYHEAD_BORDER_B = 60;
    private static final float PLAYHEAD_BORDER_WIDTH = 1.5f;
    private static final int PLAYHEAD_SHADOW_ALPHA = 100;

    private final TimelineModel model;
    private final TimelineRenderMath math;

    public TimelinePlayheadRenderer(TimelineModel model, TimelineRenderMath math) {
        this.model = model;
        this.math = math;
    }

    /**
     * 渲染播放头（带阴影与时间标签）
     */
    public void renderPlayhead(ImDrawList drawList, float contentX, float contentY,
                               float contentW, float contentH) {
        // ★ 优化：检查 pps，防止除零错误
        float pps = math.getPixelsPerSecond();
        if (pps <= 0.0001f) {
            if (logger.isWarnEnabled()) {
                logger.warn("Invalid pixels per second: {}, skipping playhead rendering", pps);
            }
            return;
        }
        
        float currentTime = model.getCurrentTime();
        float px = math.timeToX(currentTime);

        // ★ 优化：使用常量替换硬编码的可见性范围
        if (px < contentX - PLAYHEAD_VISIBILITY_MARGIN || px > contentX + contentW + PLAYHEAD_VISIBILITY_MARGIN) {
            return;
        }

        // ★ v9.0 修复：rulerTop 应该是刻度尺的顶部，即 contentY（winY）
        // 刻度尺从 winY 开始渲染，高度是 RULER_HEIGHT
        // ★ v9.0 优化：缩小手柄尺寸，三角形顶部与刻度尺上边缘对齐
        float handleHeight = HANDLE_SIZE * HANDLE_HEIGHT_MULTIPLIER;
        // 手柄顶部位置：与刻度尺顶部对齐（三角形的顶部点在刻度尺上边缘）
        float handleBottom = contentY + handleHeight;

        // 绘制手柄投影（深灰色）
        drawList.addTriangleFilled(
            px - HANDLE_SIZE + 1, contentY + 1,
            px + HANDLE_SIZE + 1, contentY + 1,
            px + 1, handleBottom + 1,
            TimelineColors.rgba(0, 0, 0, PLAYHEAD_SHADOW_ALPHA)
        );

        // 绘制手柄主体（红色）
        drawList.addTriangleFilled(
            px - HANDLE_SIZE, contentY,
            px + HANDLE_SIZE, contentY,
            px, handleBottom,
            TimelineColors.rgba(PLAYHEAD_COLOR_R, PLAYHEAD_COLOR_G, PLAYHEAD_COLOR_B, PLAYHEAD_COLOR_A)
        );

        // 绘制手柄边框（深红色）
        drawList.addTriangle(
            px - HANDLE_SIZE, contentY,
            px + HANDLE_SIZE, contentY,
            px, handleBottom,
            TimelineColors.rgba(PLAYHEAD_BORDER_R, PLAYHEAD_BORDER_G, PLAYHEAD_BORDER_B, PLAYHEAD_COLOR_A),
            PLAYHEAD_BORDER_WIDTH
        );
        
        // ★ v9.0 优化：垂直线条从手柄底部开始（三角形底部），延伸到轨道区域底部
        float lineEndY = contentY + contentH;

        // 绘制垂直线条投影（深灰色，在手柄下方）
        drawList.addLine(px + 1.5f, handleBottom, px + 1.5f, lineEndY,
                        TimelineColors.COLOR_PLAYHEAD_SHADOW, 2.5f);

        // 绘制垂直线条主体（红色）
        drawList.addLine(px, handleBottom, px, lineEndY,
                        TimelineColors.COLOR_PLAYHEAD, 2.0f);

        // 绘制垂直线条高光（淡红色）
        drawList.addLine(px - 0.5f, handleBottom, px - 0.5f, lineEndY,
                        TimelineColors.rgba(255, 180, 180, 120), 1.0f);
    }
}

