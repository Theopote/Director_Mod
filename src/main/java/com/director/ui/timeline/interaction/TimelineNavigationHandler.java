package com.director.ui.timeline.interaction;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import com.director.ui.timeline.render.TimelineRenderer;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 专门处理时间轴视图的导航逻辑（缩放、滚动、平移）
 * 
 * <p>职责：
 * <ul>
 *   <li>处理鼠标滚轮缩放（Ctrl + 滚轮）</li>
 *   <li>处理水平滚动（Shift + 滚轮）</li>
 *   <li>实现围绕鼠标点的缩放</li>
 *   <li>适应视图功能（fit to view）</li>
 *   <li>确保播放头始终可见（自动跟随）</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.1 - 优化滚动限制，允许滚动超过时间线末尾
 */
public class TimelineNavigationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineNavigationHandler.class);
    
    private final TimelineModel model;
    private final TimelineViewportState viewportState;
    
    // ========== 交互常量 ==========
    /** 滚轮检测阈值，小于此值忽略滚轮事件 */
    private static final float WHEEL_THRESHOLD = 0.01f;
    /** 缩放因子，每次滚轮的缩放倍数 */
    private static final float ZOOM_FACTOR = 1.1f;
    /** 水平滚动速度（像素/滚轮单位） */
    private static final float HORIZONTAL_SCROLL_SPEED = 40.0f;
    /** 适应视图时的边距比例（留出一定空白） */
    private static final float FIT_TO_VIEW_MARGIN_RATIO = 0.9f;
    /** 允许用户滚动超过时间线末尾的额外空间（像素），方便拖放操作 */
    private static final float END_PADDING_PIXELS = 200.0f;

    public TimelineNavigationHandler(TimelineModel model, TimelineViewportState viewportState) {
        this.model = model;
        this.viewportState = viewportState;
    }

    /**
     * 处理鼠标滚轮缩放和水平滚动
     * 
     * @param winX 窗口 X 坐标
     * @param winY 窗口 Y 坐标
     * @param winW 窗口宽度
     * @param headerHeight 标尺区域高度
     * @param trackViewportHeight 轨道视口高度
     */
    public void handleMouseWheel(float winX, float winY, float winW, float headerHeight, float trackViewportHeight) {
        float wheel = ImGui.getIO().getMouseWheel();
        if (Math.abs(wheel) < WHEEL_THRESHOLD) {
            return;
        }

        // 检查鼠标是否在区域内
        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();
        float totalHeight = headerHeight + trackViewportHeight;
        boolean inside = mouseX >= winX && mouseX <= winX + winW &&
                         mouseY >= winY && mouseY <= winY + totalHeight;
        if (!inside) {
            return;
        }

        boolean ctrl = ImGui.getIO().getKeyCtrl();
        boolean shift = ImGui.getIO().getKeyShift();
        float contentX = winX + TimelineRenderer.getLeftLabelWidth();

        if (ctrl) {
            // Ctrl + 滚轮：缩放（以鼠标所在时间为中心）
            performZoom(wheel, mouseX, contentX);
        } else if (shift) {
            // Shift + 滚轮：水平滚动
            performHorizontalScroll(wheel);
        }
        // 普通滚轮交给子窗口做垂直滚动，不在这里处理
    }

    /**
     * 执行缩放操作（围绕鼠标指向的时间点）
     */
    private void performZoom(float wheel, float mouseX, float contentX) {
        float oldPps = viewportState.getPixelsPerSecond();
        float factor = wheel > 0 ? ZOOM_FACTOR : 1.0f / ZOOM_FACTOR;
        float newPps = clamp(oldPps * factor,
                viewportState.getMinPixelsPerSecond(),
                viewportState.getMaxPixelsPerSecond());

        if (Math.abs(newPps - oldPps) < 0.001f) {
            return;
        }

        // 保持鼠标指向的时间点不变
        // 鼠标当前对应的时间（基于旧的 zoom/scrollX）
        float pivotTime = (mouseX - contentX + viewportState.getScrollX()) / oldPps;

        viewportState.setPixelsPerSecond(newPps);

        // 调整 scrollX，使得 pivotTime 对应的屏幕 X 仍在鼠标处
        // timeToScreen(t) = contentX + t * pps - scrollX  =>  scrollX = contentX + t * pps - screenX
        float newScrollX = contentX + pivotTime * newPps - mouseX;
        setScrollXClamped(newScrollX);

        LOGGER.debug("[Navigation] Zoom: pps {} -> {}, pivotTime={}, scrollX={}",
                oldPps, newPps, pivotTime, newScrollX);
    }

    /**
     * 执行水平滚动
     */
    private void performHorizontalScroll(float wheel) {
        float scrollX = viewportState.getScrollX();
        float deltaPixels = -wheel * HORIZONTAL_SCROLL_SPEED;
        setScrollXClamped(scrollX + deltaPixels);
    }

    /**
     * 适应视图：自动缩放以查看整个时间线
     * 
     * @param availableWidth 可用宽度（窗口宽度 - 左侧标签宽度）
     */
    public void fitToView(float availableWidth) {
        float duration = model.getDuration();
        if (duration <= 0) {
            duration = 10.0f; // 默认时长
        }

        // 计算需要的缩放级别（留一定边距）
        float targetPps = (availableWidth * FIT_TO_VIEW_MARGIN_RATIO) / duration;

        // 限制在合理范围内
        targetPps = clamp(targetPps,
                viewportState.getMinPixelsPerSecond(),
                viewportState.getMaxPixelsPerSecond());

        viewportState.setPixelsPerSecond(targetPps);
        setScrollXClamped(0); // 重置到开始位置

        LOGGER.info("[Navigation] Fit to view：时长={:.1f}s, 缩放={:.1f}px/s", duration, targetPps);
    }

    /**
     * 确保播放头（时间游标）始终在可见区域内（类似 Premiere Pro 的行为）
     * 
     * <p>当播放时，如果播放头超出可见范围，自动调整 scrollX 使播放头保持在可见区域内。
     * 这确保播放时游标始终可见，用户可以随时看到当前播放位置。
     * 
     * @param contentX 内容区域的起始 X 坐标（屏幕坐标）
     * @param contentW 内容区域的宽度（像素）
     * @param margin 边距（像素），播放头距离可见区域边缘的最小距离
     */
    public void ensurePlayheadVisible(float contentX, float contentW, float margin) {
        if (contentW <= 0) {
            return; // 无效区域
        }
        
        float currentTime = model.getCurrentTime();
        float pps = viewportState.getPixelsPerSecond();
        float scrollX = viewportState.getScrollX();
        
        // 计算播放头在屏幕上的位置
        // timeToScreen: screenX = contentX + time * pps - scrollX
        float playheadScreenX = contentX + (currentTime * pps) - scrollX;
        
        // 可见区域的边界（考虑边距）
        float visibleLeft = contentX + margin;
        float visibleRight = contentX + contentW - margin;
        
        // 检查播放头是否在可见区域内
        if (playheadScreenX < visibleLeft) {
            // 播放头在左侧边界外，需要向右滚动（减少 scrollX）
            // 目标：使播放头位于 visibleLeft 位置
            float targetScrollX = contentX + (currentTime * pps) - visibleLeft;
            setScrollXClamped(targetScrollX);
        } else if (playheadScreenX > visibleRight) {
            // 播放头在右侧边界外，需要向左滚动（增加 scrollX）
            // 目标：使播放头位于 visibleRight 位置
            float targetScrollX = contentX + (currentTime * pps) - visibleRight;
            setScrollXClamped(targetScrollX);
        }
    }
    
    /**
     * 设置 scrollX 并限制在合理范围内（优化版）
     * 
     * <p>允许用户滚动超过时间线末尾一定的距离，提升用户体验。
     * 这样可以方便用户在时间线末尾进行拖放操作或查看。
     * 
     * <p>优化说明：
     * <ul>
     *   <li>最小滚动值固定为 0（不允许负值）</li>
     *   <li>最大滚动值允许超过时间线末尾，增加 {@link #END_PADDING_PIXELS} 的额外空间</li>
     *   <li>这样可以防止用户"迷路"或数值溢出，同时提供足够的操作空间</li>
     * </ul>
     */
    private void setScrollXClamped(float targetScrollX) {
        // 最小不能小于 0
        if (targetScrollX < 0) {
            targetScrollX = 0;
        }
        
        // 计算允许的最大滚动值
        // 允许滚动到：总时长 + 额外空白，而不是 strict 的 totalWidth - contentW
        // 这样可以方便用户在时间线末尾拖放素材
        float duration = model.getDuration();
        float pps = viewportState.getPixelsPerSecond();
        float totalWidth = duration * pps;
        
        // 允许滚动超过末尾，至少保留固定的额外空间
        float maxScrollX = totalWidth + END_PADDING_PIXELS;
        
        if (targetScrollX > maxScrollX) {
            targetScrollX = maxScrollX;
        }
        
        viewportState.setScrollX(targetScrollX);
    }

    /**
     * 工具方法：限制值在范围内
     */
    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
}