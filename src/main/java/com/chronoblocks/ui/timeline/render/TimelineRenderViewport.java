package com.chronoblocks.ui.timeline.render;

/**
 * 时间线渲染视口（ChronoBlocks v9.0）
 * 
 * <p>负责管理视口的滚动、缩放、可见区域和鼠标状态。
 * 这是渲染器的核心状态容器，所有坐标转换都基于此视口。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>支持无限滚动（水平/垂直）</li>
 *   <li>支持无限缩放（0.001× 到 5000×）</li>
 *   <li>精确的可见区域裁剪</li>
 *   <li>鼠标位置跟踪</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 9.0
 */
public class TimelineRenderViewport {

    // ========== 滚动与缩放 ==========
    
    /** 水平滚动偏移（像素） */
    private float scrollX = 0f;
    
    /** 垂直滚动偏移（像素） */
    private float scrollY = 0f;
    
    /** 缩放级别（1.0 = 100%，2.0 = 200%） */
    private float zoom = 1.0f;
    
    /** 基础像素/秒（zoom=1.0 时的值） */
    private float basePixelsPerSecond = 100f;
    
    // ========== 可见区域 ==========
    
    /** 视口左上角 X 坐标（屏幕坐标） */
    private float viewX = 0f;
    
    /** 视口左上角 Y 坐标（屏幕坐标） */
    private float viewY = 0f;
    
    /** 视口宽度（像素） */
    private float viewWidth = 0f;
    
    /** 视口高度（像素） */
    private float viewHeight = 0f;
    
    // ========== 鼠标状态 ==========
    
    /** 鼠标 X 坐标（相对于视口） */
    private float mouseX = 0f;
    
    /** 鼠标 Y 坐标（相对于视口） */
    private float mouseY = 0f;
    
    /** 鼠标是否在视口内 */
    private boolean mouseInside = false;
    
    // ========== 布局参数 ==========
    
    /** 左侧标签区域宽度（轨道名称区域） */
    private float leftLabelWidth = 180f;
    
    /** 轨道高度（像素） */
    private float trackHeight = 24f;
    
    /** 轨道间距（像素） */
    private float trackGap = 4f;
    
    // ========== Getter / Setter ==========
    
    public float getScrollX() {
        return scrollX;
    }
    
    public void setScrollX(float scrollX) {
        this.scrollX = Math.max(0f, scrollX); // 暂时限制不能负滚动
    }
    
    public float getScrollY() {
        return scrollY;
    }
    
    public void setScrollY(float scrollY) {
        this.scrollY = Math.max(0f, scrollY);
    }
    
    public float getZoom() {
        return zoom;
    }
    
    public void setZoom(float zoom) {
        this.zoom = Math.max(0.001f, Math.min(5000f, zoom));
    }
    
    public float getBasePixelsPerSecond() {
        return basePixelsPerSecond;
    }
    
    public void setBasePixelsPerSecond(float pps) {
        this.basePixelsPerSecond = Math.max(1f, pps);
    }
    
    /**
     * 获取当前缩放下的像素/秒
     * 
     * @return 当前缩放下的像素/秒
     */
    public float getPixelsPerSecond() {
        return basePixelsPerSecond * zoom;
    }
    
    public float getViewX() {
        return viewX;
    }
    
    public float getViewY() {
        return viewY;
    }
    
    public float getViewWidth() {
        return viewWidth;
    }
    
    public float getViewHeight() {
        return viewHeight;
    }
    
    /**
     * 更新视口区域
     * 
     * @param viewX 视口左上角 X
     * @param viewY 视口左上角 Y
     * @param width 视口宽度
     * @param height 视口高度
     */
    public void update(float viewX, float viewY, float width, float height) {
        this.viewX = viewX;
        this.viewY = viewY;
        this.viewWidth = width;
        this.viewHeight = height;
    }
    
    public float getMouseX() {
        return mouseX;
    }
    
    public float getMouseY() {
        return mouseY;
    }
    
    public boolean isMouseInside() {
        return mouseInside;
    }
    
    /**
     * 更新鼠标位置
     * 
     * @param x 鼠标 X 坐标（屏幕坐标）
     * @param y 鼠标 Y 坐标（屏幕坐标）
     * @param inside 是否在视口内
     */
    public void setMouse(float x, float y, boolean inside) {
        this.mouseX = x;
        this.mouseY = y;
        this.mouseInside = inside;
    }
    
    public float getLeftLabelWidth() {
        return leftLabelWidth;
    }
    
    public void setLeftLabelWidth(float width) {
        this.leftLabelWidth = Math.max(140f, Math.min(320f, width));
    }
    
    public float getTrackHeight() {
        return trackHeight;
    }
    
    public void setTrackHeight(float height) {
        this.trackHeight = height;
    }
    
    public float getTrackGap() {
        return trackGap;
    }
    
    public void setTrackGap(float gap) {
        this.trackGap = gap;
    }
    
    /**
     * 获取内容区域起始 X（轨道头区域右侧）
     * 
     * @return 内容区域起始 X
     */
    public float getContentX() {
        return viewX + leftLabelWidth;
    }
    
    /**
     * 获取内容区域宽度（总宽度减去轨道头宽度）
     * 
     * @return 内容区域宽度
     */
    public float getContentWidth() {
        return Math.max(0f, viewWidth - leftLabelWidth);
    }

    /**
     * 获取轨道区域的起始 Y 坐标（视口顶部 + 头部高度）
     * 
     * <p>轨道区域实际上是从头部（标尺）下方开始的，而不是从整个窗口顶部开始。
     * 这个方法返回轨道区域的实际起始位置，用于正确的坐标转换。
     * 
     * <p>★ 优化：避免循环依赖，将 headerHeight 作为参数注入或使用常量
     * 
     * @return 轨道区域起始 Y 坐标
     */
    public float getTrackAreaY() {
        // 头部高度 = RULER_HEIGHT + RULER_BOTTOM_PADDING
        // ★ 优化：避免循环依赖，直接使用常量计算
        // 这些常量应该与 TimelineRenderer 中的定义保持一致
        float headerHeight = com.chronoblocks.ui.theme.UITheme.Spacing.BUTTON_HEIGHT + 
                            com.chronoblocks.ui.theme.UITheme.Spacing.SMALL;
        return viewY + headerHeight;
    }

    /**
     * 检查点是否在视口内
     * 
     * @param x X 坐标
     * @param y Y 坐标
     * @return 是否在视口内
     */
    public boolean contains(float x, float y) {
        return x >= viewX && x <= viewX + viewWidth &&
               y >= viewY && y <= viewY + viewHeight;
    }
}

