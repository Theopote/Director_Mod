package com.director.ui.timeline.render;

import imgui.ImDrawList;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 时间线标尺渲染器（v9.0）
 * 
 * <p>职责：
 * <ul>
 *   <li>渲染时间刻度标尺（主刻度和次刻度）</li>
 *   <li>计算刻度步长（根据缩放级别）</li>
 *   <li>格式化时间标签显示</li>
 * </ul>
 * 
 * <p>参考 Blender, After Effects 等专业软件，使用主次刻度系统
 * 
 * @author Director Team
 * @version 9.0
 */
public final class TimelineRulerRenderer {

    private static final Logger logger = LoggerFactory.getLogger(TimelineRulerRenderer.class);

    private final TimelineRenderMath math;
    private final TimelineRenderViewport viewport;
    private final TimelineTimeFormatter timeFormatter;
    private com.director.ui.timeline.model.TimelineModel model;
    private TimelineRenderer.DragStateQuery dragStateQuery;  // ⭐ 拖动状态查询器（用于显示拖动中的标记位置）
    
    // ★ 性能优化：使用 Map 缓存步长计算，避免重复计算
    // 由于 pps 值可能很多，使用缓存可以显著提升性能
    private final Map<Float, Float> majorStepCache = new HashMap<>();
    private final Map<Float, Float> minorStepCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 50; // 限制缓存大小，避免内存泄漏

    public TimelineRulerRenderer(TimelineRenderMath math, TimelineRenderViewport viewport) {
        this.math = math;
        this.viewport = viewport;
        this.timeFormatter = new TimelineTimeFormatter();
        // Logger 保留用于将来的调试和错误记录
        if (logger.isTraceEnabled()) {
            logger.trace("TimelineRulerRenderer initialized");
        }
    }
    
    /**
     * 设置 TimelineModel（用于渲染标记）
     */
    public void setModel(com.director.ui.timeline.model.TimelineModel model) {
        this.model = model;
    }
    
    /**
     * ⭐ 设置拖动状态查询器（用于显示拖动中的标记位置）
     */
    public void setDragStateQuery(TimelineRenderer.DragStateQuery dragStateQuery) {
        this.dragStateQuery = dragStateQuery;
    }

    /**
     * 渲染时间刻度标尺（专业级设计）
     * 参考 Blender, After Effects 等专业软件，使用主次刻度系统
     */
    public void renderRuler(ImDrawList drawList, float contentX, float topY, float contentW, float rulerHeight) {
        drawList.addRectFilled(contentX, topY, contentX + contentW, topY + rulerHeight, TimelineColors.COLOR_RULER_BG);

        drawList.addRectFilledMultiColor(
            contentX, topY, contentX + contentW, topY + 1,
            TimelineColors.rgba(65,65,65,200), TimelineColors.rgba(65,65,65,200),
            TimelineColors.rgba(45,45,45,100), TimelineColors.rgba(45,45,45,100)
        );

        drawList.addLine(contentX, topY + rulerHeight, contentX + contentW, topY + rulerHeight,
                        TimelineColors.rgba(20,20,20,180), 1.0f);

        float pps = viewport.getPixelsPerSecond();
        float[] visibleRange = math.getVisibleTimeRange();
        float timeStart = visibleRange[0];
        float timeEnd = visibleRange[1];

        // ★ 性能优化：直接获取步长，不创建 TickLevels 对象
        // 避免每帧渲染标尺时创建临时对象
        float majorStep = getMajorStep(pps);
        float minorStep = getMinorStep(pps);

        // 渲染次刻度
        float firstMinorTick = (float)Math.ceil(timeStart / minorStep) * minorStep;
        for (float t = firstMinorTick; t <= timeEnd + minorStep; t += minorStep) {
            float x = math.timeToX(t);
            if (x < contentX || x > contentX + contentW) continue;

            // 避免浮点取模在大步长/小步长时抖动，用“接近整数倍”判断主刻度
            float ratio = t / majorStep;
            boolean isMajor = Math.abs(ratio - Math.round(ratio)) < 0.001f;
            if (!isMajor) {
                drawList.addLine(x, topY + rulerHeight - 6, x, topY + rulerHeight,
                        TimelineColors.rgba(100,100,100,150), 1.0f);
            }
        }

        // 渲染主刻度和标签
        float firstMajorTick = (float)Math.floor(timeStart / majorStep) * majorStep;
        for (float t = firstMajorTick; t <= timeEnd + majorStep; t += majorStep) {
            float x = math.timeToX(t);
            if (x < contentX || x > contentX + contentW) continue;

            drawList.addLine(x, topY + 2, x, topY + rulerHeight,
                    TimelineColors.rgba(160,160,160,220), 1.5f);

            String timeText = timeFormatter.formatTimeLabel(t);
            String frameText = timeFormatter.formatFrameLabel(t);
            drawList.addText(x + 3, topY + 3, TimelineColors.COLOR_TEXT_LIGHT, timeText);

            // ★ 优化：提取常量替换硬编码的 frameTextY
            final float FRAME_TEXT_OFFSET_Y = 14f;
            final float FRAME_TEXT_MIN_SPACE = 8f;
            final float FRAME_TEXT_MARGIN = 2f;
            float frameTextY = topY + FRAME_TEXT_OFFSET_Y;
            if (frameTextY + FRAME_TEXT_MIN_SPACE < topY + rulerHeight - FRAME_TEXT_MARGIN) {
                drawList.addText(x + 3, frameTextY, TimelineColors.rgba(140,140,140,200), frameText);
            }
        }

        // ★ v9.0 优化：移除播放头顶部的水平横短线
        // 播放头的手柄现在放在刻度尺上方，不再需要这条横短线
        
        // ⭐ 渲染标记（在时间标尺上）
        if (model != null) {
            renderMarkers(drawList, contentX, topY, contentW, rulerHeight, timeStart, timeEnd);
        }
    }
    
    /**
     * 渲染标记（轻量化的标签样式）
     */
    private void renderMarkers(ImDrawList drawList, float contentX, float topY, float contentW, 
                               float rulerHeight, float timeStart, float timeEnd) {
        if (model == null) {
            return;
        }
        
        java.util.List<com.director.ui.timeline.model.TimelineMarker> markers = model.getMarkers();
        if (markers == null || markers.isEmpty()) {
            return;
        }
        
        // ⭐ 标记贴着时间刻度上边缘
        float markerY = topY + 2.0f;  // 距离上边缘 2px
        float centerY = topY + rulerHeight * 0.5f;  // 保留用于 hover 检测
        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();
        
        for (com.director.ui.timeline.model.TimelineMarker marker : markers) {
            if (marker == null) continue;
            
            // ⭐ 检查是否正在拖动此标记（使用拖动中的临时时间）
            float markerTime = marker.getTime();
            if (dragStateQuery != null) {
                float draggedTime = dragStateQuery.getDraggedMarkerTime(marker);
                if (draggedTime >= 0f) {
                    markerTime = draggedTime;  // 使用拖动中的临时时间
                }
            }
            
            // 跳过不可见的标记
            if (markerTime < timeStart || markerTime > timeEnd) {
                continue;
            }
            
            float mx = math.timeToX(markerTime);
            if (mx < contentX || mx > contentX + contentW) continue;
            
            float mxAligned = (float) Math.floor(mx) + 0.5f;
            
            // Hover 检测
            float dx = mouseX - mxAligned;
            float dy = mouseY - centerY;
            float distSq = dx * dx + dy * dy;
            boolean hovering = distSq <= 6.0f * 6.0f; // 6px 半径
            
            // 检查是否被选中
            boolean isSelected = model.isSelected(marker);
            
            // 获取标记颜色
            // ⭐ 修复：marker.getColor() 是 ARGB 格式，需要转换为 ImGui 使用的 ABGR 格式
            int markerColor = marker.getColor();
            int a = (markerColor >> 24) & 0xFF;
            int r = (markerColor >> 16) & 0xFF;
            int g = (markerColor >> 8) & 0xFF;
            int b = markerColor & 0xFF;
            
            int kfCol;
            if (isSelected) {
                // 选中时使用高亮颜色，并稍微变大
                kfCol = com.director.ui.timeline.render.TimelineColors.COLOR_KEYFRAME_SELECTED;
            } else if (hovering) {
                // 标记 hover 时稍微变亮
                kfCol = com.director.ui.timeline.render.TimelineColors.rgba(
                    Math.min(255, r + 40),
                    Math.min(255, g + 40),
                    Math.min(255, b + 40),
                    255
                );
            } else {
                // 正常状态：将 ARGB 转换为 ABGR（ImGui 格式）
                kfCol = com.director.ui.timeline.render.TimelineColors.rgba(r, g, b, a);
            }
            
            // 绘制小三角形（向下指向，看起来像标签）
            float markerSize = 4.0f;  // 标记更小
            if (isSelected) {
                markerSize = 5.0f;  // 选中时变大
                // ⭐ 移除高亮圈，只保留标记变大和高亮
            }
            
            // 绘制小三角形（向下指向，看起来像标签）
            // ⭐ 标记贴着上边缘，三角形从上边缘开始
            float triTop = markerY;
            float triBottom = markerY + markerSize * 1.4f;
            float triWidth = markerSize * 0.8f;
            
            // 绘制填充三角形（向下指向）
            drawList.addTriangleFilled(
                mxAligned, triTop,  // 顶点
                mxAligned - triWidth, triBottom,  // 左下
                mxAligned + triWidth, triBottom,  // 右下
                kfCol
            );
            
            // 绘制三角形边框（选中时更明显）
            if (isSelected) {
                drawList.addTriangle(
                    mxAligned, triTop,
                    mxAligned - triWidth, triBottom,
                    mxAligned + triWidth, triBottom,
                    com.director.ui.timeline.render.TimelineColors.rgba(0, 0, 0, 200), 1.0f
                );
            }
        }
    }

    /**
     * 获取主刻度步长（带缓存优化）
     * 
     * <p>性能优化：使用 Map 缓存步长计算结果，避免重复计算。
     * 在每一帧渲染标尺时调用，缓存可以显著减少计算开销。
     * 
     * @param pps 每秒像素数
     * @return 主刻度步长（秒）
     */
    private float getMajorStep(float pps) {
        // 使用整数键避免浮点精度问题
        float key = Math.round(pps * 10f) / 10f;
        
        Float cached = majorStepCache.get(key);
        if (cached != null) {
            return cached;
        }
        
        // 计算步长（自适应：避免缩放很小时标签重叠）
        // 目标：主刻度之间至少有足够像素间距容纳 “12.34s” 这类文本
        final float MIN_MAJOR_LABEL_SPACING_PX = 72f;
        float desiredSeconds = MIN_MAJOR_LABEL_SPACING_PX / Math.max(0.001f, pps);
        float step = pickNiceStepSeconds(desiredSeconds);
        
        // 缓存结果（限制缓存大小）
        if (majorStepCache.size() >= MAX_CACHE_SIZE) {
            majorStepCache.clear(); // 简单策略：清空缓存
            if (logger.isTraceEnabled()) {
                logger.trace("Major step cache cleared due to size limit");
            }
        }
        majorStepCache.put(key, step);
        
        return step;
    }

    /**
     * 获取次刻度步长（带缓存优化）
     * 
     * <p>性能优化：使用 Map 缓存步长计算结果，避免重复计算。
     * 
     * @param pps 每秒像素数
     * @return 次刻度步长（秒）
     */
    private float getMinorStep(float pps) {
        // 使用整数键避免浮点精度问题
        float key = Math.round(pps * 10f) / 10f;
        
        Float cached = minorStepCache.get(key);
        if (cached != null) {
            return cached;
        }
        
        // 计算步长（次刻度跟随主刻度，避免过密）
        float major = getMajorStep(pps);
        float step = major / 5f;

        // 如果次刻度在屏幕上过密（比如 < 6px），就减少次刻度数量
        final float MIN_MINOR_TICK_SPACING_PX = 6f;
        if (step * pps < MIN_MINOR_TICK_SPACING_PX) {
            // 尝试 major/4 或 major/2，直到不那么密
            float s4 = major / 4f;
            float s2 = major / 2f;
            if (s4 * pps >= MIN_MINOR_TICK_SPACING_PX) step = s4;
            else step = s2;
        }
        
        // 缓存结果（限制缓存大小）
        if (minorStepCache.size() >= MAX_CACHE_SIZE) {
            minorStepCache.clear(); // 简单策略：清空缓存
            if (logger.isTraceEnabled()) {
                logger.trace("Minor step cache cleared due to size limit");
            }
        }
        minorStepCache.put(key, step);
        
        return step;
    }

    /**
     * 从“漂亮步长”序列中选取 >= desiredSeconds 的值。
     * 使用 1/2/5 * 10^n 的经典刻度体系，并额外补充 15/30/60 等更符合时间语义的步长。
     */
    private static float pickNiceStepSeconds(float desiredSeconds) {
        if (desiredSeconds <= 0) return 0.1f;

        // 常用的“时间感”步长（秒）
        final float[] steps = new float[] {
                0.02f, 0.05f, 0.1f, 0.2f, 0.25f, 0.5f,
                1f, 2f, 5f, 10f, 15f, 20f, 30f,
                60f, 120f, 300f, 600f, 900f,
                1200f, 1800f, 3600f
        };

        for (float s : steps) {
            if (s >= desiredSeconds) return s;
        }

        // 再大就按 1/2/5 * 10^n 继续扩展（以秒为单位）
        double exp = Math.floor(Math.log10(desiredSeconds));
        double base = Math.pow(10.0, exp);
        double[] muls = new double[] {1, 2, 5, 10};
        for (double m : muls) {
            double candidate = m * base;
            if (candidate >= desiredSeconds) return (float) candidate;
        }
        return (float) (10.0 * base);
    }
}

