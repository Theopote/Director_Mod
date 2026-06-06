package com.director.ui.timeline.render;

import com.director.actor.action.ActorActionType;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.entity.ActorActionClip;
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.entity.TimelineClipVisualType;
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
 * @author Director Team
 * @version 9.0
 */
public final class TimelineClipRenderer {

    private static final Logger logger = LoggerFactory.getLogger(TimelineClipRenderer.class);

    // ========== 布局常量 ==========
    
    /**
     * 片段高度（像素）
     * 
     * <p>片段在轨道上的实际显示高度。
     * 轨道高度为 22px，片段上下各留 1px 内边距，实际渲染高度约 18px。
     */
    public static final float CLIP_HEIGHT = 20.0f;
    
    /**
     * 片段垂直内边距（像素）
     * 
     * <p>片段上下两侧的内边距。
     * 轨道高度 22px 时，与 clipTop/clipBottom 公式中的 ±1 配合，使片段视觉高度约 18px。
     */
    private static final float CLIP_VERT_PADDING = 1.0f;
    
    /**
     * 片段文本 X 偏移（像素）
     * 
     * <p>片段标签文本距离片段左边缘的偏移量。
     */
    private static final float CLIP_TEXT_OFFSET_X = 6.0f;
    
    /**
     * 调整手柄宽度（像素）
     * 
     * <p>片段左右两侧用于调整宽度的拖拽手柄宽度。
     * 注意：虽然不再渲染视觉手柄，但此常量仍用于命中检测逻辑。
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
    private com.director.ui.timeline.render.TimelineRenderer.DragStateQuery dragStateQuery = null;

    public TimelineClipRenderer(TimelineModel model, TimelineRenderMath math) {
        this.model = model;
        this.math = math;
    }
    
    /**
     * 设置拖动状态查询器（用于实时显示拖动中的关键帧位置）
     * 
     * @param dragStateQuery 拖动状态查询器，可以为 null
     */
    public void setDragStateQuery(com.director.ui.timeline.render.TimelineRenderer.DragStateQuery dragStateQuery) {
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

        // ✅ 扁平化样式：与摄像机路径片段保持一致，无渐变效果
        float clipTop = y + CLIP_VERT_PADDING + 1.0f;
        float clipBottom = y + trackH - CLIP_VERT_PADDING - 1.0f;
        
        // 检查是否被选中（用于边框颜色）
        boolean isSelected = clip == model.getSelectedClip();
        int borderColor = isSelected 
            ? TimelineColors.COLOR_KEYFRAME_SELECTED 
            : TimelineColors.rgba(0, 0, 0, 200);
        
        // 绘制片段块（扁平化风格，无圆角，无渐变）
        drawList.addRectFilled(
            clampedX1, clipTop, 
            clampedX2, clipBottom,
            col, 0.0f, 0  // 无圆角
        );
        
        // 边框（选中时高亮，扁平化）
        float borderWidth = isSelected ? 2.0f : 1.0f;
        drawList.addRect(
            clampedX1, clipTop,
            clampedX2, clipBottom,
            borderColor, 0.0f, 0, borderWidth  // 无圆角
        );

        // ✅ 文字渲染：默认白色，选中时黑色，垂直居中对齐
        float textWidth = ImGui.calcTextSize(clip.getLabel()).x;
        float clipWidth = clampedX2 - clampedX1;
        if (clipWidth > textWidth + 12.0f) { // 只有足够宽时才显示文本
            float textX = clampedX1 + Math.max(CLIP_TEXT_OFFSET_X, (clipWidth - textWidth) * 0.5f);
            if (textX + textWidth <= maxX && textX >= contentX) {
                // 文本颜色：默认白色，选中时黑色（与摄像机路径片段一致）
                int textColor = isSelected 
                    ? TimelineColors.COLOR_TEXT_DARK 
                    : TimelineColors.COLOR_TEXT_LIGHT;
                
                // 垂直居中对齐：使用片段中心位置
                float clipCenterY = (clipTop + clipBottom) * 0.5f;
                // 文本基准线在底部，需要向上偏移约7像素以居中对齐
                float textY = clipCenterY - 7.0f;
                
                drawList.addText(textX, textY, textColor, clip.getLabel());
            }
        }

        // ✅ 移除调整手柄：调整功能通过边缘检测实现（8像素范围），不需要视觉手柄
        // 扁平化设计下，这些手柄显得多余且不美观

        // ========= 关键帧渲染（曲线类型显示关键帧）=========
        float centerY = y + trackH / 2.0f;
        
        float[] visibleTimeRange = math.getVisibleTimeRange();
        float visibleStart = visibleTimeRange[0];
        float visibleEnd = visibleTimeRange[1];
        
        List<com.director.ui.timeline.model.clip.ClipKeyframe> clipKeyframes = clip.getClipKeyframes();
        if (clipKeyframes != null && !clipKeyframes.isEmpty()) {
            for (com.director.ui.timeline.model.clip.ClipKeyframe keyframe : clipKeyframes) {
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

        // ✅ 扁平化样式：与摄像机路径片段保持一致，无渐变效果
        float clipTop = y + CLIP_VERT_PADDING + 1.0f;
        float clipBottom = y + trackH - CLIP_VERT_PADDING - 1.0f;
        
        // 检查是否被选中（用于边框颜色）
        boolean isSelected = clip == model.getSelectedClip();
        int borderColor = isSelected 
            ? TimelineColors.COLOR_KEYFRAME_SELECTED 
            : TimelineColors.rgba(0, 0, 0, 200);
        
        // 绘制片段块（扁平化风格，无圆角，无渐变）
        drawList.addRectFilled(
            clampedX1, clipTop, 
            clampedX2, clipBottom,
            col, 0.0f, 0  // 无圆角
        );
        
        // 边框（选中时高亮，扁平化）
        float borderWidth = isSelected ? 2.0f : 1.0f;
        drawList.addRect(
            clampedX1, clipTop,
            clampedX2, clipBottom,
            borderColor, 0.0f, 0, borderWidth  // 无圆角
        );

        // ✅ 文字渲染：默认白色，选中时黑色，垂直居中对齐
        float textWidth = ImGui.calcTextSize(actionLabel).x;
        float clipWidth = clampedX2 - clampedX1;
        if (clipWidth > textWidth + 12.0f) { // 只有足够宽时才显示文本
            float textX = clampedX1 + (clipWidth - textWidth) * 0.5f;
            if (textX + textWidth <= maxX && textX >= contentX) {
                // 文本颜色：默认白色，选中时黑色（与摄像机路径片段一致）
                int textColor = isSelected 
                    ? TimelineColors.COLOR_TEXT_DARK 
                    : TimelineColors.COLOR_TEXT_LIGHT;
                
                // 垂直居中对齐：使用片段中心位置
                float clipCenterY = (clipTop + clipBottom) * 0.5f;
                // 文本基准线在底部，需要向上偏移约7像素以居中对齐
                float textY = clipCenterY - 7.0f;
                
                drawList.addText(textX, textY, textColor, actionLabel);
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
        // 事件在开始时间位置
        float maxX = contentX + contentW;

        if (x1 < contentX || x1 > maxX) return;

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
                x1, centerY - eventRadius,
            x1 + eventRadius, centerY,
                x1, centerY + eventRadius,
            col
        );
        drawList.addTriangleFilled(
                x1, centerY - eventRadius,
            x1 - eventRadius, centerY,
                x1, centerY + eventRadius,
            col
        );

        // 边框
        drawList.addLine(x1, centerY - eventRadius, x1 + eventRadius, centerY, TimelineColors.rgba(0, 0, 0, 200), 1.0f);
        drawList.addLine(x1 + eventRadius, centerY, x1, centerY + eventRadius, TimelineColors.rgba(0, 0, 0, 200), 1.0f);
        drawList.addLine(x1, centerY + eventRadius, x1 - eventRadius, centerY, TimelineColors.rgba(0, 0, 0, 200), 1.0f);
        drawList.addLine(x1 - eventRadius, centerY, x1, centerY - eventRadius, TimelineColors.rgba(0, 0, 0, 200), 1.0f);

        // 选中时显示外圈
        if (clip == model.getSelectedClip() && !isTrackLocked) {
            drawList.addCircle(x1, centerY, eventRadius + 2.0f, TimelineColors.COLOR_SELECTED_BORDER, 0, 2.0f);
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

