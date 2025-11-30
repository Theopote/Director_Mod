package com.chronoblocks.ui.timeline.render;

import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import imgui.ImDrawList;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线轨道渲染器（v9.0）
 * 
 * <p>职责：
 * <ul>
 *   <li>渲染单个轨道（背景、标签、分割线）</li>
 *   <li>斑马纹效果（奇偶行背景色）</li>
 *   <li>轨道控制图标（锁定、可见性）</li>
 *   <li>轨道类型指示条</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 9.0
 */
public final class TimelineTrackRenderer {

    private static final Logger logger = LoggerFactory.getLogger(TimelineTrackRenderer.class);

    // 布局常量
    private static final float TRACK_HEIGHT = UITheme.Spacing.TIMELINE_TRACK_HEIGHT;
    private static final float TRACK_LABEL_ICON_SPACING = 6f;
    private static final float TRACK_LABEL_TEXT_OFFSET = 8f;
    private static final float TRACK_ICON_OFFSET_Y = 6f;
    private static final float TRACK_KEYFRAME_OFFSET_X = -65f;
    private static final float TRACK_LOCK_OFFSET_X = -45f;
    private static final float TRACK_VISIBILITY_OFFSET_X = -25f;

    private final TimelineRenderViewport viewport;

    public TimelineTrackRenderer(TimelineRenderViewport viewport) {
        this.viewport = viewport;
    }

    /**
     * 渲染单个轨道（带类型色彩、锁定与可见性标记）
     * 
     * <p>v9.1 优化：
     * <ul>
     *   <li>斑马纹背景：区分奇偶行，提升可读性。</li>
     *   <li>全贯通分割线：增强行与行之间的界限。</li>
     *   <li>绘制顺序优化：确保分割线位于最上层，不被背景覆盖。</li>
     * </ul>
     */
    public void renderTrack(ImDrawList drawList, TimelineTrack track, int trackIndex, float winX,
                             float contentX, float y, float contentW) {
        // 1. 准备颜色和状态
        TrackType type = track.getType();
        boolean isEven = (trackIndex % 2 == 0);
        
        // 2. 绘制右侧内容区域背景
        // 专业软件通常内容区背景较暗且统一，以便突出 Clip 颜色。
        // 这里保持原逻辑：内容区使用统一的深色背景，不应用斑马纹，以免干扰 Clip 的色彩判断。
        int bgColor = type != null ? type.getBackgroundColor() : TimelineColors.COLOR_BG_MEDIUM;
        int trackBgColor = TimelineColors.adjustAlpha(bgColor);
        drawList.addRectFilled(contentX, y, contentX + contentW, y + TRACK_HEIGHT, trackBgColor);

        // 3. 绘制左侧标签区域背景（应用斑马纹）
        // 偶数行使用稍亮的背景色，奇数行使用默认背景色，形成经典的斑马纹效果
        int labelBgColor = isEven ? TimelineColors.COLOR_TRACK_LABEL_BG_ALT : TimelineColors.COLOR_TRACK_LABEL_BG;
        drawList.addRectFilled(winX, y, contentX, y + TRACK_HEIGHT, labelBgColor);

        // 4. 绘制左侧彩色指示条
        int typeIndicatorColor = type != null ? type.getClipColor() : TimelineColors.rgba(100, 100, 100, 255);
        drawList.addRectFilled(winX, y, winX + 3, y + TRACK_HEIGHT, typeIndicatorColor);

        // 5. 绘制全贯通分割线（v9.1 优化：调整绘制顺序）
        // 位置：轨道的底部边缘。
        // 优化：使用 addLine 时，Y 坐标通常建议偏移 0.5f 以实现像素对齐（在 1.0f 线宽下），
        // 但 ImGui 的 addLine 在默认抗锯齿下表现良好。
        // 关键点：这行代码放在这里（背景绘制之后，文字绘制之前）是安全的。
        // 如果 TRACK_GAP > 0，这条线会画在轨道实体的最下方边缘，视觉上是边框。
        // 如果 TRACK_GAP == 0，这条线会作为行与行之间的分界线。
        drawList.addLine(winX, y + TRACK_HEIGHT, contentX + contentW, y + TRACK_HEIGHT,
                TimelineColors.COLOR_SEPARATOR_LINE, 1.0f);

        // 6. 绘制文字和图标
        float labelX = winX + TRACK_LABEL_TEXT_OFFSET;
        if (type != null) {
            String icon = type.getIcon();
            if (icon != null) {
                drawList.addText(labelX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(200, 200, 200, 255), icon);
                labelX += ImGui.calcTextSize(icon).x + TRACK_LABEL_ICON_SPACING;
            } else if (logger.isWarnEnabled()) {
                logger.warn("Track type icon is null for track: {}", track.getName());
            }
        }
        drawList.addText(labelX, y + TRACK_ICON_OFFSET_Y, TimelineColors.COLOR_TEXT_LIGHT, track.getName());

        // 7. 绘制控制图标（关键帧 / 锁 / 可见性）
        float keyframeX = winX + viewport.getLeftLabelWidth() + TRACK_KEYFRAME_OFFSET_X;
        float lockX = winX + viewport.getLeftLabelWidth() + TRACK_LOCK_OFFSET_X;
        float visibilityX = winX + viewport.getLeftLabelWidth() + TRACK_VISIBILITY_OFFSET_X;

        boolean supportsKeyframes = type != null && type.supportsKeyframes();
        int keyframeColor = supportsKeyframes ? TimelineColors.COLOR_KEYFRAME : TimelineColors.rgba(140, 140, 140, 100);
        drawList.addText(keyframeX, y + TRACK_ICON_OFFSET_Y, keyframeColor, Icons.KEYFRAME);

        if (track.isLocked()) {
            drawList.addText(lockX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(200, 180, 100, 255), Icons.LOCK);
        } else {
            drawList.addText(lockX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(120, 120, 120, 180), Icons.UNLOCK);
        }
        if (!track.isVisible()) {
            drawList.addText(visibilityX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(150, 150, 150, 255), Icons.INVISIBLE);
        } else {
            drawList.addText(visibilityX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(180, 180, 180, 200), Icons.VISIBLE);
        }

        // 注意：Clip 的渲染由 TimelineClipRenderer 负责，这里只需要设置裁剪区域
        // 在 TimelineRenderer.renderTrackArea 中会调用 TimelineClipRenderer.renderClip
    }
}

