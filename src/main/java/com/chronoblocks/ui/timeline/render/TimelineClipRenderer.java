package com.chronoblocks.ui.timeline.render;

import com.chronoblocks.actor.action.ActorActionType;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.entity.ActorActionClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTimelineClip;
import com.chronoblocks.ui.timeline.model.entity.TimelineClipVisualType;
import imgui.ImDrawList;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * 时间线 Clip 渲染器（v9.0）
 * 
 * <p>职责：
 * <ul>
 *   <li>渲染 Clip 矩形（带高光效果）</li>
 *   <li>渲染关键帧（垂直指示线 + 关键帧点）</li>
 *   <li>渲染 Clip 文本标签</li>
 *   <li>渲染调整手柄（左右两侧）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 9.0
 */
public final class TimelineClipRenderer {

    private static final Logger logger = LoggerFactory.getLogger(TimelineClipRenderer.class);

    // ========== 布局常量 ==========
    
    /**
     * 片段高度（像素）
     * 
     * <p>片段在轨道上的实际显示高度。
     * 轨道高度为 24px，片段高度为 20px，上下各留 2px 内边距。
     */
    public static final float CLIP_HEIGHT = 20.0f;
    
    /**
     * 片段垂直内边距（像素）
     * 
     * <p>片段上下两侧的内边距。
     * 计算公式：CLIP_VERT_PADDING = (轨道高度 - 片段高度) / 2
     * 当前值：(24 - 20) / 2 = 2px
     */
    private static final float CLIP_VERT_PADDING = 2.0f;
    
    /**
     * 片段文本 X 偏移（像素）
     * 
     * <p>片段标签文本距离片段左边缘的偏移量。
     */
    private static final float CLIP_TEXT_OFFSET_X = 6.0f;
    
    /**
     * 片段文本 Y 偏移（像素）
     * 
     * <p>片段标签文本距离轨道顶部的偏移量。
     */
    private static final float CLIP_TEXT_OFFSET_Y = 6.0f;
    
    /**
     * 选中边框偏移（像素）
     * 
     * <p>选中片段时，边框向外扩展的偏移量。
     */
    private static final float CLIP_SELECTED_BORDER_OFFSET = 2.0f;
    
    /**
     * 调整手柄宽度（像素）
     * 
     * <p>片段左右两侧用于调整宽度的拖拽手柄宽度。
     */
    private static final float RESIZE_HANDLE_WIDTH = 6.0f;
    
    // ========== 关键帧常量 ==========
    
    /**
     * 关键帧半径（像素）
     * 
     * <p>关键帧点的显示半径。
     */
    public static final float KEYFRAME_RADIUS = 4.0f;
    
    /**
     * 关键帧命中检测半径（像素）
     * 
     * <p>关键帧的鼠标命中检测半径，略大于显示半径以便于选中。
     */
    public static final float KEYFRAME_HIT_RADIUS = 6.0f;

    private final TimelineModel model;
    private final TimelineRenderMath math;
    
    // ★ 性能优化：缓存鼠标位置，避免每关键帧调用 ImGui.getMousePosX/Y()
    private float cachedMouseX = -1f;
    private float cachedMouseY = -1f;
    
    // ⭐ 拖动状态查询器（用于实时显示拖动中的关键帧位置）
    private com.chronoblocks.ui.timeline.render.TimelineRenderer.DragStateQuery dragStateQuery = null;

    public TimelineClipRenderer(TimelineModel model, TimelineRenderMath math) {
        this.model = model;
        this.math = math;
    }
    
    /**
     * 设置拖动状态查询器（用于实时显示拖动中的关键帧位置）
     * 
     * @param dragStateQuery 拖动状态查询器，可以为 null
     */
    public void setDragStateQuery(com.chronoblocks.ui.timeline.render.TimelineRenderer.DragStateQuery dragStateQuery) {
        this.dragStateQuery = dragStateQuery;
    }
    
    /**
     * 更新鼠标位置缓存（应在 renderClip 前调用）
     */
    public void updateMousePosition() {
        cachedMouseX = imgui.ImGui.getMousePosX();
        cachedMouseY = imgui.ImGui.getMousePosY();
        // Logger 保留用于将来的调试和错误记录
        if (logger.isTraceEnabled()) {
            logger.trace("Mouse position updated: ({}, {})", cachedMouseX, cachedMouseY);
        }
    }

    /**
     * 渲染单个 Clip（v2.0：支持视觉类型分支）
     */
    public void renderClip(ImDrawList drawList, TimelineClip clip, TimelineTrack track, int trackIndex,
                           float contentX, float y, float trackH, float contentW) {
        // ★ 优化：添加 null 检查
        if (clip == null) {
            if (logger.isWarnEnabled()) {
                logger.warn("Attempted to render null clip, trackIndex={}", trackIndex);
            }
            return;
        }
        
        // ========== 视觉类型分支（v2.0 新增）==========
        TimelineClipVisualType visualType = TimelineClipVisualType.CURVE; // 默认
        if (clip instanceof EntityTimelineClip entityTimelineClip) {
            visualType = entityTimelineClip.getVisualType();
        }

        // 根据视觉类型分支绘制
        switch (visualType) {
            case BLOCK -> renderBlockClip(drawList, clip, track, trackIndex, contentX, y, trackH, contentW);
            case EVENT -> renderEventClip(drawList, clip, track, trackIndex, contentX, y, trackH, contentW);
            case CURVE -> renderCurveClip(drawList, clip, track, trackIndex, contentX, y, trackH, contentW);
        }
    }

    /**
     * 渲染曲线类型 Clip（TransformClip 等）
     * <p>显示关键帧和插值曲线
     */
    private void renderCurveClip(ImDrawList drawList, TimelineClip clip, TimelineTrack track, int trackIndex,
                                  float contentX, float y, float trackH, float contentW) {
        float x1 = math.timeToX(clip.getStartTime());
        float x2 = math.timeToX(clip.getEndTime());
        float maxX = contentX + contentW;

        if (x2 < contentX || x1 > maxX) return;
        
        float clampedX1 = Math.max(contentX, x1);
        float clampedX2 = Math.min(maxX, x2);

        // ★ 轨道锁定功能：如果轨道锁定，将片段颜色变灰
        int col = clip.getColor();
        boolean isTrackLocked = track != null && track.isLocked();
        if (isTrackLocked) {
            col = TimelineColors.grayscale(col);
        }

        if (clip == model.getSelectedClip()) {
            float borderX1 = Math.max(contentX, x1 - CLIP_SELECTED_BORDER_OFFSET);
            float borderX2 = Math.min(maxX, x2 + CLIP_SELECTED_BORDER_OFFSET);
            if (borderX2 > borderX1 && !isTrackLocked) {
                drawList.addRect(borderX1, y + 1, borderX2, y + trackH - 1, TimelineColors.COLOR_SELECTED_BORDER, 0.0f, 0, 2.0f);
            }
        }

        // 使用限制后的坐标绘制片段（曲线类型：线条更细）
        float clipTop = y + CLIP_VERT_PADDING + 1.0f; // 稍微细一点
        float clipBottom = y + trackH - CLIP_VERT_PADDING - 1.0f;
        drawList.addRectFilled(clampedX1, clipTop, clampedX2, clipBottom, col);
        int highlightCol = TimelineColors.lighten(col);
        drawList.addRectFilledMultiColor(
            clampedX1, clipTop, clampedX2, y + 8,
            highlightCol, highlightCol,
            col, col
        );

        drawList.addRect(clampedX1, clipTop, clampedX2, clipBottom, TimelineColors.rgba(0,0,0,200));

        float textWidth = ImGui.calcTextSize(clip.getLabel()).x;
        float clipWidth = clampedX2 - clampedX1;
        float textX = clampedX1 + Math.max(CLIP_TEXT_OFFSET_X, (clipWidth - textWidth) * 0.5f);
        if (textX + textWidth <= maxX && textX >= contentX) {
            drawList.addText(textX, y + CLIP_TEXT_OFFSET_Y, TimelineColors.COLOR_TEXT_DARK, clip.getLabel());
        }

        // ★ 轨道锁定功能：锁定轨道上的片段不显示调整手柄
        if (!isTrackLocked) {
            float controlWidth = RESIZE_HANDLE_WIDTH;
            float handleRightX = x2 - controlWidth;
            
            drawList.addRectFilled(x1, clipTop, x1 + controlWidth, clipBottom, TimelineColors.COLOR_RESIZE_HANDLE);
            drawList.addRectFilled(handleRightX, clipTop, handleRightX + controlWidth, clipBottom, TimelineColors.COLOR_RESIZE_HANDLE);
            
            float leftHandleCenterX = x1 + controlWidth * 0.5f;
            drawList.addLine(leftHandleCenterX, clipTop + 2, leftHandleCenterX, clipBottom - 2, 
                            TimelineColors.rgba(255, 255, 255, 200), 1.5f);
            float rightHandleCenterX = handleRightX + controlWidth * 0.5f;
            drawList.addLine(rightHandleCenterX, clipTop + 2, rightHandleCenterX, clipBottom - 2, 
                            TimelineColors.rgba(255, 255, 255, 200), 1.5f);
        }

        // ========= 关键帧渲染（曲线类型显示关键帧）=========
        float centerY = y + trackH / 2.0f;
        
        float[] visibleTimeRange = math.getVisibleTimeRange();
        float visibleStart = visibleTimeRange[0];
        float visibleEnd = visibleTimeRange[1];
        
        List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> clipKeyframes = clip.getClipKeyframes();
        if (clipKeyframes != null && !clipKeyframes.isEmpty()) {
            for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe keyframe : clipKeyframes) {
                if (keyframe == null) {
                    continue;
                }

                float keyTime = keyframe.getTime();
                if (dragStateQuery != null) {
                    float draggedTime = dragStateQuery.getDraggedClipKeyframeTime(clip, keyTime);
                    if (draggedTime >= 0) {
                        keyTime = draggedTime;
                    }
                }

                if (keyTime < visibleStart || keyTime > visibleEnd) {
                    continue;
                }

                float kx = math.timeToX(keyTime);
                if (kx < contentX || kx > contentX + contentW) continue;
                float kxAligned = (float) Math.floor(kx) + 0.5f;

                boolean hovering = isMouseHoveringCircle(kxAligned, centerY);
                int kfCol = hovering ? TimelineColors.COLOR_KEYFRAME_HOVER : TimelineColors.COLOR_KEYFRAME;

                // ✅ 像素级对齐：不要用粗线 addLine（会出现“看起来偏左/偏右”的抗锯齿错觉）
                // 改为明确宽度的填充矩形：1px 主线 + 3px 阴影，中心都严格对齐 kxAligned。
                float lineTop = y + 4;
                float lineBottom = y + trackH - 4;
                drawList.addRectFilled(kxAligned - 1.5f, lineTop, kxAligned + 1.5f, lineBottom, TimelineColors.rgba(0, 0, 0, 80));
                drawList.addRectFilled(kxAligned - 0.5f, lineTop, kxAligned + 0.5f, lineBottom, kfCol);
                drawList.addCircleFilled(kxAligned, centerY, KEYFRAME_RADIUS + 1.0f, TimelineColors.rgba(0, 0, 0, 180));
                drawList.addCircleFilled(kxAligned, centerY, KEYFRAME_RADIUS, kfCol);
            }
        } else {
            for (Float keyframe : clip.getKeyframes()) {
                if (keyframe == null) continue;

                float keyTime = keyframe;
                if (dragStateQuery != null) {
                    float draggedTime = dragStateQuery.getDraggedClipKeyframeTime(clip, keyTime);
                    if (draggedTime >= 0) {
                        keyTime = draggedTime;
                    }
                }

                if (keyTime < visibleStart || keyTime > visibleEnd) {
                    continue;
                }

                float kx = math.timeToX(keyTime);
                if (kx < contentX || kx > contentX + contentW) continue;
                float kxAligned = (float) Math.floor(kx) + 0.5f;

                boolean hovering = isMouseHoveringCircle(kxAligned, centerY);
                int kfCol = hovering ? TimelineColors.COLOR_KEYFRAME_HOVER : TimelineColors.COLOR_KEYFRAME;

                // ✅ 同上：用矩形绘制竖线，保证与圆点严格同心
                float lineTop = y + 4;
                float lineBottom = y + trackH - 4;
                drawList.addRectFilled(kxAligned - 1.5f, lineTop, kxAligned + 1.5f, lineBottom, TimelineColors.rgba(0, 0, 0, 80));
                drawList.addRectFilled(kxAligned - 0.5f, lineTop, kxAligned + 0.5f, lineBottom, kfCol);
                drawList.addCircleFilled(kxAligned, centerY, KEYFRAME_RADIUS + 1.0f, TimelineColors.rgba(0, 0, 0, 180));
                drawList.addCircleFilled(kxAligned, centerY, KEYFRAME_RADIUS, kfCol);
            }
        }
    }

    /**
     * 渲染区块类型 Clip（ActorActionClip 等）
     * <p>实心区块，不显示关键帧，使用 ActionType 颜色
     */
    private void renderBlockClip(ImDrawList drawList, TimelineClip clip, TimelineTrack track, int trackIndex,
                                 float contentX, float y, float trackH, float contentW) {
        float x1 = math.timeToX(clip.getStartTime());
        float x2 = math.timeToX(clip.getEndTime());
        float maxX = contentX + contentW;

        if (x2 < contentX || x1 > maxX) return;
        
        float clampedX1 = Math.max(contentX, x1);
        float clampedX2 = Math.min(maxX, x2);

        // 获取 ActionType 颜色（如果是 ActorActionClip）
        int col = clip.getColor();
        String actionLabel = clip.getLabel();
        
        if (clip instanceof EntityTimelineClip entityTimelineClip) {
            var entityClip = entityTimelineClip.getEntityClip();
            if (entityClip instanceof ActorActionClip actionClip) {
                ActorActionType actionType = actionClip.getActionType();
                col = ActionTypeColors.getColorForActionType(actionType);
                actionLabel = actionType.name(); // 使用 ActionType 名称作为标签
            }
        }

        boolean isTrackLocked = track != null && track.isLocked();
        if (isTrackLocked) {
            col = TimelineColors.grayscale(col);
        }

        // 选中边框
        if (clip == model.getSelectedClip()) {
            float borderX1 = Math.max(contentX, x1 - CLIP_SELECTED_BORDER_OFFSET);
            float borderX2 = Math.min(maxX, x2 + CLIP_SELECTED_BORDER_OFFSET);
            if (borderX2 > borderX1 && !isTrackLocked) {
                drawList.addRect(borderX1, y + 1, borderX2, y + trackH - 1, TimelineColors.COLOR_SELECTED_BORDER, 0.0f, 0, 2.0f);
            }
        }

        // Block Clip：实心区块，高度略大于 Curve
        float blockPadding = 0.5f; // Block 比 Curve 稍微高一点
        float clipTop = y + CLIP_VERT_PADDING - blockPadding;
        float clipBottom = y + trackH - CLIP_VERT_PADDING + blockPadding;

        // 绘制实心区块（使用普通矩形，未来可以改进为圆角）
        drawList.addRectFilled(clampedX1, clipTop, clampedX2, clipBottom, col);
        
        // 边框（稍微内缩，营造"区块感"）
        drawList.addRect(clampedX1 + 1, clipTop + 1, clampedX2 - 1, clipBottom - 1, TimelineColors.rgba(0, 0, 0, 200));

        // 文本标签（中央显示 Action 名称）
        float textWidth = ImGui.calcTextSize(actionLabel).x;
        float clipWidth = clampedX2 - clampedX1;
        if (clipWidth > textWidth + 12.0f) { // 只有足够宽时才显示文本
            float textX = clampedX1 + (clipWidth - textWidth) * 0.5f;
            if (textX + textWidth <= maxX && textX >= contentX) {
                drawList.addText(textX, y + CLIP_TEXT_OFFSET_Y, TimelineColors.COLOR_TEXT_DARK, actionLabel);
            }
        }

        // Block Clip 不显示调整手柄（状态区间，通常不需要频繁调整）
        // 如果需要，可以保留，但建议禁用

        // ⚠️ Block Clip 不显示关键帧（这是底线）
        // 不渲染任何关键帧点或线条
    }

    /**
     * 渲染事件类型 Clip（ActionEvent / AnimationEvent 等）
     * <p>点 / 标记形态
     */
    private void renderEventClip(ImDrawList drawList, TimelineClip clip, TimelineTrack track, int trackIndex,
                                 float contentX, float y, float trackH, float contentW) {
        float x1 = math.timeToX(clip.getStartTime());
        float centerX = x1; // 事件在开始时间位置
        float maxX = contentX + contentW;

        if (centerX < contentX || centerX > maxX) return;

        int col = clip.getColor();
        boolean isTrackLocked = track != null && track.isLocked();
        if (isTrackLocked) {
            col = TimelineColors.grayscale(col);
        }

        float centerY = y + trackH / 2.0f;
        float eventRadius = 4.0f; // 事件点半径

        // 绘制事件点（菱形 ◆）
        // 使用两个三角形绘制菱形
        drawList.addTriangleFilled(
            centerX, centerY - eventRadius,
            centerX + eventRadius, centerY,
            centerX, centerY + eventRadius,
            col
        );
        drawList.addTriangleFilled(
            centerX, centerY - eventRadius,
            centerX - eventRadius, centerY,
            centerX, centerY + eventRadius,
            col
        );

        // 边框
        drawList.addLine(centerX, centerY - eventRadius, centerX + eventRadius, centerY, TimelineColors.rgba(0, 0, 0, 200), 1.0f);
        drawList.addLine(centerX + eventRadius, centerY, centerX, centerY + eventRadius, TimelineColors.rgba(0, 0, 0, 200), 1.0f);
        drawList.addLine(centerX, centerY + eventRadius, centerX - eventRadius, centerY, TimelineColors.rgba(0, 0, 0, 200), 1.0f);
        drawList.addLine(centerX - eventRadius, centerY, centerX, centerY - eventRadius, TimelineColors.rgba(0, 0, 0, 200), 1.0f);

        // 选中时显示外圈
        if (clip == model.getSelectedClip() && !isTrackLocked) {
            drawList.addCircle(centerX, centerY, eventRadius + 2.0f, TimelineColors.COLOR_SELECTED_BORDER, 0, 2.0f);
        }

        // 事件不显示调整手柄和关键帧
    }

    /**
     * 检查鼠标是否悬停在圆形区域内（用于关键帧 hover 检测）
     * 
     * <p>★ 性能优化：使用缓存的鼠标位置，避免每关键帧调用 ImGui.getMousePosX/Y()
     * 
     * @param cx 圆心 X
     * @param cy 圆心 Y
     * @return 是否悬停
     */
    private boolean isMouseHoveringCircle(float cx, float cy) {
        // 使用缓存的鼠标位置（在 renderClip 前通过 updateMousePosition 更新）
        if (cachedMouseX < 0 || cachedMouseY < 0) {
            // 如果缓存未初始化，回退到直接获取（向后兼容）
            cachedMouseX = ImGui.getMousePosX();
            cachedMouseY = ImGui.getMousePosY();
        }
        float dx = cachedMouseX - cx;
        float dy = cachedMouseY - cy;
        return dx * dx + dy * dy <= KEYFRAME_HIT_RADIUS * KEYFRAME_HIT_RADIUS;
    }

    /**
     * 获取调整手柄宽度
     * 
     * @return 手柄宽度（像素）
     */
    public static float getResizeHandleWidth() {
        return RESIZE_HANDLE_WIDTH;
    }
}

