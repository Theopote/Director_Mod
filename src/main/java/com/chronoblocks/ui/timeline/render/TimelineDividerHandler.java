package com.chronoblocks.ui.timeline.render;

import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线分隔线拖动处理器（v9.0）
 * 
 * <p>职责：
 * <ul>
 *   <li>处理左侧轨道标签区域与内容区域之间的分隔线拖动</li>
 *   <li>检测鼠标悬停和拖动状态</li>
 *   <li>更新左侧标签宽度</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 9.0
 */
public final class TimelineDividerHandler {

    private static final Logger logger = LoggerFactory.getLogger(TimelineDividerHandler.class);

    // 分隔线拖动状态
    private boolean resizingLeftSection = false;

    // 左侧标签宽度限制
    private static final float DEFAULT_LEFT_LABEL_WIDTH = 180f;
    private static final float LEFT_LABEL_MIN = 140f;
    private static final float LEFT_LABEL_MAX = 320f;
    
    // ★ 优化：提取 hover 检测范围常量
    private static final float DIVIDER_HIT_WIDTH = 4f;

    // 当前左侧标签宽度
    private float leftLabelWidth;

    // 视口引用（用于同步状态）
    private final TimelineRenderViewport viewport;

    public TimelineDividerHandler(TimelineRenderViewport viewport) {
        this.viewport = viewport;
        this.leftLabelWidth = DEFAULT_LEFT_LABEL_WIDTH;
        // ★ 修复：构造函数中同步 viewport，确保初始状态一致
        if (viewport != null) {
            viewport.setLeftLabelWidth(DEFAULT_LEFT_LABEL_WIDTH);
        }
    }

    /**
     * 更新左侧轨道标签区域宽度（重载方法，向后兼容）
     */
    public void updateLeftSectionWidth(float winX, float winY, float totalHeight) {
        updateLeftSectionWidth(winX, winY, totalHeight, false);
    }

    /**
     * 更新左侧轨道标签区域宽度
     * 
     * <p>检测鼠标是否在分隔线上，如果是则：
     * <ul>
     *   <li>显示双箭头光标</li>
     *   <li>支持拖动调整宽度</li>
     *   <li>更新 viewport 状态</li>
     * </ul>
     * 
     * @param winX 窗口 X 坐标
     * @param winY 窗口 Y 坐标
     * @param totalHeight 总高度（用于检测范围）
     * @param shouldSkipDividerDrag 如果为 true，则跳过分割线拖动处理（例如鼠标在关键帧上时）
     */
    public void updateLeftSectionWidth(float winX, float winY, float totalHeight, boolean shouldSkipDividerDrag) {
        // ★ 优化：使用统一的 clamp 方法
        leftLabelWidth = clampWidth(leftLabelWidth);

        // ★ v9.0 修复：分隔线拖动检测，支持在整个时间线区域（包括 header 和轨道区域）拖动
        float dividerX = winX + leftLabelWidth;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        // ★ 优化：使用常量替换硬编码的 hover 检测范围
        boolean hoveringDivider = mouseX >= dividerX - DIVIDER_HIT_WIDTH && mouseX <= dividerX + DIVIDER_HIT_WIDTH
                && mouseY >= winY && mouseY <= winY + totalHeight;
        
        // ⭐ 修复：如果鼠标在关键帧上，不处理分割线拖动（避免冲突）
        if (shouldSkipDividerDrag && hoveringDivider) {
            // 如果正在拖动中，继续拖动（避免拖动中断）
            if (!resizingLeftSection) {
                return; // 跳过分割线拖动处理
            }
        }
        
        if (hoveringDivider || resizingLeftSection) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        
        if (!resizingLeftSection && hoveringDivider && ImGui.isMouseClicked(0)) {
            resizingLeftSection = true;
        }
        
        if (resizingLeftSection) {
            if (ImGui.isMouseDown(0)) {
                float target = mouseX - winX;
                leftLabelWidth = clampWidth(target);
                
                // ============================================================
                // ★ v9.0 修复 3：立即同步 viewport 状态
                // ============================================================
                // 确保当前帧后续的坐标转换（timeToX 等）使用新的宽度
                if (viewport != null) {
                    viewport.setLeftLabelWidth(leftLabelWidth);
                }
                
                // ★ 优化：添加日志记录拖动事件
                if (logger.isDebugEnabled()) {
                    logger.debug("Resizing left section to {}px", leftLabelWidth);
                }
            } else {
                resizingLeftSection = false;
                if (logger.isDebugEnabled()) {
                    logger.debug("Finished resizing left section, final width: {}px", leftLabelWidth);
                }
            }
        }
    }
    
    /**
     * 限制宽度在合法范围内（私有工具方法）
     * 
     * @param width 原始宽度
     * @return 限制后的宽度
     */
    private float clampWidth(float width) {
        return Math.max(LEFT_LABEL_MIN, Math.min(LEFT_LABEL_MAX, width));
    }

    /**
     * 获取当前左侧标签宽度
     * 
     * @return 左侧标签宽度（像素）
     */
    public float getLeftLabelWidth() {
        return leftLabelWidth;
    }

    /**
     * 设置左侧标签宽度（用于外部同步）
     * 
     * @param width 新的宽度
     */
    public void setLeftLabelWidth(float width) {
        this.leftLabelWidth = clampWidth(width);
        if (viewport != null) {
            viewport.setLeftLabelWidth(this.leftLabelWidth);
        }
    }

    /**
     * 获取默认左侧标签宽度
     * 
     * @return 默认宽度
     */
    public static float getDefaultLeftLabelWidth() {
        return DEFAULT_LEFT_LABEL_WIDTH;
    }

    /**
     * 获取左侧标签宽度最小值
     * 
     * @return 最小宽度
     */
    public static float getLeftLabelMin() {
        return LEFT_LABEL_MIN;
    }

    /**
     * 获取左侧标签宽度最大值
     * 
     * @return 最大宽度
     */
    public static float getLeftLabelMax() {
        return LEFT_LABEL_MAX;
    }
}

