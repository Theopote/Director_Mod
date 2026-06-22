package com.director.ui.timeline.render;

import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
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
 * @author Director Team
 * @version 9.0
 */
public final class TimelineTrackRenderer {

    private static final Logger logger = LoggerFactory.getLogger(TimelineTrackRenderer.class);

    // 布局常量
    private static final float TRACK_HEIGHT = UITheme.Spacing.TIMELINE_TRACK_HEIGHT;
    private static final float TRACK_LABEL_ICON_SPACING = 6f;
    private static final float TRACK_LABEL_TEXT_OFFSET = 8f;
    private static final float TRACK_ICON_OFFSET_Y = 6f;
    // ✅ 修复：按钮区域两侧边距设置为 6px，按钮之间的间距保持 20px 不变
    // 按钮布局：分割线 | 6px | 关键帧(16px) | 20px | 锁定(16px) | 20px | 可见性(16px) | 6px | 右边界
    private static final float BUTTON_AREA_MARGIN = 6f;  // 两侧边距
    private static final float BUTTON_SPACING = 4f;     // 按钮之间的间距（保持不变）
    private static final float CONTROL_ICON_SIZE = 16f;  // 按钮图标大小
    // 从右边界向左计算位置（负值表示从右边界向左）
    // 可见性按钮：右边界 - 6px - 16px = -22px
    private static final float TRACK_VISIBILITY_OFFSET_X = -(BUTTON_AREA_MARGIN + CONTROL_ICON_SIZE);
    // 锁定按钮：可见性位置 - 20px - 16px = -58px（保持20px间距）
    private static final float TRACK_LOCK_OFFSET_X = -(BUTTON_AREA_MARGIN + CONTROL_ICON_SIZE + BUTTON_SPACING + CONTROL_ICON_SIZE);
    // 关键帧按钮：锁定位置 - 20px - 16px = -94px（保持20px间距）
    private static final float TRACK_KEYFRAME_OFFSET_X = -(BUTTON_AREA_MARGIN + CONTROL_ICON_SIZE + BUTTON_SPACING + CONTROL_ICON_SIZE + BUTTON_SPACING + CONTROL_ICON_SIZE);
    // 名称和按钮之间的分割位置：关键帧位置 - 6px = -100px
    private static final float TRACK_NAME_BUTTONS_SEPARATOR_OFFSET = -(BUTTON_AREA_MARGIN + CONTROL_ICON_SIZE + BUTTON_SPACING + CONTROL_ICON_SIZE + BUTTON_SPACING + CONTROL_ICON_SIZE + BUTTON_AREA_MARGIN);

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

        // 6. 计算分割位置（名称和按钮之间的分割线）
        float separatorX = winX + viewport.getLeftLabelWidth() + TRACK_NAME_BUTTONS_SEPARATOR_OFFSET;
        
        // 7. 绘制文字和图标（名称会被裁剪到分割位置）
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
        
        // 计算名称的最大显示宽度（从 labelX 到分割线）
        float maxNameWidth = separatorX - labelX - 4f; // 留4像素间距
        if (maxNameWidth > 0) {
            // 使用 ImDrawList 的裁剪功能来限制文本显示
            String trackName = track.getName();
            
            // 设置裁剪区域：从 labelX 到 separatorX
            drawList.pushClipRect(
                labelX, y,
                separatorX, y + TRACK_HEIGHT,
                true  // 相交模式
            );
            
            // 绘制文本（会被裁剪到分割线位置）
            drawList.addText(labelX, y + TRACK_ICON_OFFSET_Y, TimelineColors.COLOR_TEXT_LIGHT, trackName);
            
            // 恢复裁剪区域
            drawList.popClipRect();
        } else {
            // 如果空间不足，不绘制名称
        }
        
        // 8. 绘制分割线（名称和按钮之间）
        drawList.addLine(separatorX, y, separatorX, y + TRACK_HEIGHT, 
                TimelineColors.COLOR_SEPARATOR_LINE, 1.0f);

        // 9. 绘制控制图标（关键帧 / 锁 / 激活摄像机）
        float keyframeX = winX + viewport.getLeftLabelWidth() + TRACK_KEYFRAME_OFFSET_X;
        float lockX = winX + viewport.getLeftLabelWidth() + TRACK_LOCK_OFFSET_X;
        float visibilityX = winX + viewport.getLeftLabelWidth() + TRACK_VISIBILITY_OFFSET_X;
        
        // ⭐ v5.0：激活摄像机按钮（仅 CAMERA 轨道，放在原来隐藏按钮的位置）
        if (type == TrackType.CAMERA) {
            boolean isActive = com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                    .getActiveCameraTrack() == track;
            // 实心圆 = 激活，空心圆 = 未激活（使用 DOT 和 CHECK_CIRCLE）
            String icon = isActive ? Icons.DOT : Icons.CHECK_CIRCLE;
            int iconColor = isActive 
                    ? TimelineColors.rgba(100, 200, 255, 255)  // 激活：亮蓝色
                    : TimelineColors.rgba(140, 140, 140, 180); // 未激活：灰色
            drawList.addText(visibilityX, y + TRACK_ICON_OFFSET_Y, iconColor, icon);
        }
        
        // ⭐ v3.5：激活时间按钮（仅 TIME 轨道，放在原来隐藏按钮的位置）
        if (type == TrackType.TIME) {
            boolean isActive = com.director.timeline.playback.time.TimelineWorldController.getInstance()
                    .getActiveTimeTrack() == track;
            // 实心圆 = 激活，空心圆 = 未激活（使用 DOT 和 CHECK_CIRCLE）
            String icon = isActive ? Icons.DOT : Icons.CHECK_CIRCLE;
            int iconColor = isActive 
                    ? TimelineColors.rgba(240, 160, 60, 255)  // 激活：橙色（与时间轨道颜色一致）
                    : TimelineColors.rgba(140, 140, 140, 180); // 未激活：灰色
            drawList.addText(visibilityX, y + TRACK_ICON_OFFSET_Y, iconColor, icon);
        }
        
        // ⭐ v1.1：激活季节按钮（仅 SEASON 轨道，放在原来隐藏按钮的位置）
        if (type == TrackType.SEASON) {
            boolean isActive = com.director.timeline.playback.season.TimelineSeasonController.getInstance()
                    .getActiveSeasonTrack() == track;
            // 实心圆 = 激活，空心圆 = 未激活（使用 DOT 和 CHECK_CIRCLE）
            String icon = isActive ? Icons.DOT : Icons.CHECK_CIRCLE;
            int iconColor = isActive 
                    ? TimelineColors.rgba(60, 200, 100, 255)  // 激活：绿色（与季节轨道颜色一致）
                    : TimelineColors.rgba(140, 140, 140, 180); // 未激活：灰色
            drawList.addText(visibilityX, y + TRACK_ICON_OFFSET_Y, iconColor, icon);
        }
        
        // ⭐ v1.0：激活天气按钮（仅 WEATHER 轨道，放在原来隐藏按钮的位置）
        if (type == TrackType.WEATHER) {
            boolean isActive = com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
                    .getActiveWeatherTrack() == track;
            // 实心圆 = 激活，空心圆 = 未激活（使用 DOT 和 CHECK_CIRCLE）
            String icon = isActive ? Icons.DOT : Icons.CHECK_CIRCLE;
            int iconColor = isActive 
                    ? TimelineColors.rgba(80, 140, 180, 255)  // 激活：蓝色（与天气轨道颜色一致）
                    : TimelineColors.rgba(140, 140, 140, 180); // 未激活：灰色
            drawList.addText(visibilityX, y + TRACK_ICON_OFFSET_Y, iconColor, icon);
        }

        // ⭐ v5.0：对于摄像机轨道，只有在激活状态下才显示可用的关键帧按钮
        // ⭐ v3.5：对于时间轨道，只有在激活状态下才显示可用的关键帧按钮
        // ⭐ v1.1：对于季节轨道，只有在激活状态下才显示可用的关键帧按钮
        // ⭐ v1.0：对于天气轨道，只有在激活状态下才显示可用的关键帧按钮
        // ✅ 修复：容器轨道和清除轨道不支持轨道级关键帧，不显示按钮
        boolean shouldShowKeyframeButton = type != null && type.supportsKeyframes();
        if (type == TrackType.CONTAINER) {
            shouldShowKeyframeButton = false;  // 容器轨道不显示关键帧按钮
        }

        boolean keyframeEnabled = isKeyframeEnabled(track, shouldShowKeyframeButton, type);

        // 只有应该显示且启用的轨道才渲染关键帧按钮
        if (shouldShowKeyframeButton) {
            int keyframeColor = keyframeEnabled 
                    ? TimelineColors.COLOR_KEYFRAME 
                    : TimelineColors.rgba(100, 100, 100, 100);  // 禁用状态：更暗的灰色
            drawList.addText(keyframeX, y + TRACK_ICON_OFFSET_Y, keyframeColor, Icons.KEYFRAME);
        }

        if (track.isLocked()) {
            drawList.addText(lockX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(200, 180, 100, 255), Icons.LOCK);
        } else {
            drawList.addText(lockX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(120, 120, 120, 180), Icons.UNLOCK);
        }
        
        // ⭐ v5.0：摄像机轨道不显示隐藏/显示按钮
        // ⭐ v3.5：时间轨道不显示隐藏/显示按钮
        // ⭐ v1.1：季节轨道不显示隐藏/显示按钮
        // ⭐ v1.0：天气轨道不显示隐藏/显示按钮
        if (type != TrackType.CAMERA && type != TrackType.TIME && type != TrackType.SEASON && type != TrackType.WEATHER) {
            if (!track.isVisible()) {
                drawList.addText(visibilityX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(150, 150, 150, 255), Icons.INVISIBLE);
            } else {
                drawList.addText(visibilityX, y + TRACK_ICON_OFFSET_Y, TimelineColors.rgba(180, 180, 180, 200), Icons.VISIBLE);
            }
        }

        // 注意：Clip 的渲染由 TimelineClipRenderer 负责，这里只需要设置裁剪区域
        // 在 TimelineRenderer.renderTrackArea 中会调用 TimelineClipRenderer.renderClip
    }

    private static boolean isKeyframeEnabled(TimelineTrack track, boolean shouldShowKeyframeButton, TrackType type) {
        boolean keyframeEnabled = false;
        if (shouldShowKeyframeButton) {
            if (type == TrackType.CAMERA) {
                com.director.timeline.playback.camera.TimelineCameraController controller =
                        com.director.timeline.playback.camera.TimelineCameraController.getInstance();
                TimelineTrack activeTrack = controller.getActiveCameraTrack();
                keyframeEnabled = (activeTrack == track);  // 只有激活轨道才可用
            } else if (type == TrackType.TIME) {
                com.director.timeline.playback.time.TimelineWorldController controller =
                        com.director.timeline.playback.time.TimelineWorldController.getInstance();
                TimelineTrack activeTrack = controller.getActiveTimeTrack();
                keyframeEnabled = (activeTrack == track);  // 只有激活轨道才可用
            } else if (type == TrackType.SEASON) {
                com.director.timeline.playback.season.TimelineSeasonController controller =
                        com.director.timeline.playback.season.TimelineSeasonController.getInstance();
                TimelineTrack activeTrack = controller.getActiveSeasonTrack();
                keyframeEnabled = (activeTrack == track);  // 只有激活轨道才可用
            } else if (type == TrackType.WEATHER) {
                com.director.timeline.playback.weather.TimelineWeatherController controller =
                        com.director.timeline.playback.weather.TimelineWeatherController.getInstance();
                TimelineTrack activeTrack = controller.getActiveWeatherTrack();
                keyframeEnabled = (activeTrack == track);  // 只有激活轨道才可用
            } else {
                // 其他支持关键帧的轨道（如演员轨道），默认启用
                keyframeEnabled = true;
            }
        }
        return keyframeEnabled;
    }
}

