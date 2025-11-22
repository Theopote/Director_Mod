package com.chronoblocks.ui.timeline.render;

import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TimelineViewportState;

import imgui.ImDrawList;
import imgui.ImGui;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TimelineRenderer v9.0 - 专业级渲染器（ChronoBlocks 风格）
 * 
 * <p>架构升级：
 * <ul>
 *   <li>使用 TimelineRenderViewport 管理视口状态</li>
 *   <li>使用 TimelineRenderMath 进行坐标转换</li>
 *   <li>使用 TimelineRenderHitTest 进行命中检测</li>
 *   <li>支持无限缩放和无限滚动</li>
 *   <li>精确的像素级对齐</li>
 * </ul>
 * 
 * <p>职责（v9.0 重构后）：
 * <ul>
 *   <li>作为渲染协调器，组装和委托给各个专门的渲染组件</li>
 *   <li>管理视口状态和坐标转换</li>
 *   <li>提供向后兼容的公共 API</li>
 * </ul>
 * 
 * <p>拆分后的组件：
 * <ul>
 *   <li>{@link TimelineColors} - 颜色常量和管理</li>
 *   <li>{@link TimelineTimeFormatter} - 时间格式化</li>
 *   <li>{@link TimelineRulerRenderer} - 标尺渲染</li>
 *   <li>{@link TimelineTrackRenderer} - 轨道渲染</li>
 *   <li>{@link TimelineClipRenderer} - Clip 渲染</li>
 *   <li>{@link TimelinePlayheadRenderer} - 播放头渲染</li>
 *   <li>{@link TimelineDividerHandler} - 分隔线拖动处理</li>
 *   <li>{@link TimelineTrackControlHit} - TrackControl 命中检测</li>
 * </ul>
 * 
 * <p>真正的数据修改都交给 TimelineModel / CommandHistory 去做。
 * 
 * @author ChronoBlocks Team
 * @version 9.0 - 专业级架构（拆分优化）
 */
public class TimelineRenderer {

    private static final Logger logger = LoggerFactory.getLogger(TimelineRenderer.class);

    // ========== 颜色常量已迁移到 TimelineColors ==========
    // 所有颜色常量现在通过 TimelineColors 访问

    // ========== 布局常量 ==========
    // 时间线轨道尺寸从 UITheme 中获取，保持UI规范统一
    private static final float TRACK_HEIGHT = UITheme.Spacing.TIMELINE_TRACK_HEIGHT;
    private static final float TRACK_GAP = UITheme.Spacing.TIMELINE_TRACK_GAP;
    private static final float RULER_HEIGHT = UITheme.Spacing.BUTTON_HEIGHT;
    private static final float RULER_BOTTOM_PADDING = UITheme.Spacing.SMALL;
    private static final float DEFAULT_LEFT_LABEL_WIDTH = TimelineDividerHandler.getDefaultLeftLabelWidth();
    // LEFT_LABEL_MIN 和 LEFT_LABEL_MAX 现在由 TimelineDividerHandler 管理
    @SuppressWarnings("unused")
    private static final float LEFT_LABEL_MIN = TimelineDividerHandler.getLeftLabelMin();
    @SuppressWarnings("unused")
    private static final float LEFT_LABEL_MAX = TimelineDividerHandler.getLeftLabelMax();
    private static final float TRACK_LABEL_TEXT_OFFSET = 8f;
    
    // leftLabelWidth 现在由 dividerHandler 管理，保留静态变量作为缓存以兼容旧代码
    private static float leftLabelWidth = DEFAULT_LEFT_LABEL_WIDTH;
    
    // ========== Keyframe 视觉 & Hit 常量（统一）==========
    /** 命中检测半径（略大，方便选中） */
    public static final float KEYFRAME_HIT_RADIUS = TimelineClipRenderer.KEYFRAME_HIT_RADIUS;

    private final TimelineModel model;
    private final TimelineViewportState viewportState;
    
    // 当前渲染区域的顶部 Y 坐标（由 updateRenderArea 设置）
    private float trackAreaTop = 0f;

    // ======= 新架构：专业级渲染组件（v9.0） =======
    private final TimelineRenderViewport viewport;
    private final TimelineRenderMath math;

    // ======= 拆分后的渲染组件（v9.0 重构） =======
    private final TimelineDividerHandler dividerHandler;
    private final TimelineRulerRenderer rulerRenderer;
    private final TimelineTrackRenderer trackRenderer;
    private final TimelineClipRenderer clipRenderer;
    private final TimelinePlayheadRenderer playheadRenderer;
    private final TimelineTrackControlHit trackControlHit;
    private final TimelineRenderHitTest renderHitTest;

    public TimelineRenderer(TimelineModel model, TimelineViewportState viewportState) {
        this.model = model;
        this.viewportState = viewportState;
        
        // 初始化新架构组件
        this.viewport = new TimelineRenderViewport();
        this.viewport.setBasePixelsPerSecond(100f);
        this.viewport.setTrackHeight(TRACK_HEIGHT);
        this.viewport.setTrackGap(TRACK_GAP);
        this.viewport.setLeftLabelWidth(DEFAULT_LEFT_LABEL_WIDTH);
        
        this.math = new TimelineRenderMath(viewport);

        // 初始化拆分后的渲染组件
        this.dividerHandler = new TimelineDividerHandler(viewport);
        this.rulerRenderer = new TimelineRulerRenderer(math, viewport);
        this.trackRenderer = new TimelineTrackRenderer(viewport);
        this.clipRenderer = new TimelineClipRenderer(model, math);
        this.playheadRenderer = new TimelinePlayheadRenderer(model, math);
        this.trackControlHit = new TimelineTrackControlHit();
        
        // 初始化命中检测组件
        this.renderHitTest = new TimelineRenderHitTest(math, viewport, model);
    }

    private void syncViewportFromState() {
        if (viewportState == null) {
            throw new IllegalStateException("ViewportState not initialized");
        }
        viewport.setScrollX(viewportState.getScrollX());
        viewport.setScrollY(viewportState.getScrollY());
        viewport.setBasePixelsPerSecond(viewportState.getPixelsPerSecond());
    }

    // ===================== 每帧由 Panel 驱动的入口 =====================

    /**
     * 由 TimelinePanel 每帧调用，告知当前面板区域信息（v9.0 统一入口）
     */
    public void beginFrame(float x, float y, float width, float height) {
        // 更新新架构的视口
        viewport.update(x, y, width, height);
        
        // ★ v9.0 修复：确保 viewport 的 leftLabelWidth 与 dividerHandler 同步（双重保险）
        // 这样即使 updateLeftSectionWidth 在 beginFrame 之前调用，
        // viewport 也能获取到最新的宽度值
        float currentWidth = dividerHandler.getLeftLabelWidth();
        viewport.setLeftLabelWidth(currentWidth);
        
        syncViewportFromState();

        // 更新鼠标状态
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        boolean inside = viewport.contains(mx, my);
        viewport.setMouse(mx, my, inside);

        // beginFrame 仅负责同步视口，滚动范围由 Panel / ImGui 控制
    }

    /**
     * 更新左侧轨道标签区域宽度（委托给 TimelineDividerHandler）
     */
    public void updateLeftSectionWidth(float winX, float winY, float totalHeight) {
        dividerHandler.updateLeftSectionWidth(winX, winY, totalHeight);
        // 同步静态变量（向后兼容）
        leftLabelWidth = dividerHandler.getLeftLabelWidth();
        viewport.setLeftLabelWidth(leftLabelWidth);
    }

    /**
     * 渲染时间线头部（轨道标签背景 + 时间标尺）
     */
    public void renderTimelineHeader(ImDrawList drawList, float winX, float winY, float winW) {
        float labelWidth = dividerHandler.getLeftLabelWidth();
        float contentX = winX + labelWidth;
        float contentW = Math.max(0f, winW - labelWidth);
        float rulerBottom = winY + RULER_HEIGHT;
        float headerBottom = winY + getHeaderHeight();

        // 背景块
        drawList.addRectFilled(winX, winY, winX + winW, headerBottom, TimelineColors.COLOR_BG_DARK);
        drawList.addRectFilled(winX, rulerBottom, winX + labelWidth, headerBottom, TimelineColors.COLOR_TRACK_LABEL_BG);
        drawList.addRectFilled(contentX, rulerBottom, contentX + contentW, headerBottom, TimelineColors.COLOR_BG_MEDIUM);
        drawList.addLine(contentX, winY, contentX, headerBottom, TimelineColors.rgba(60, 60, 60, 200));

        drawList.addText(winX + TRACK_LABEL_TEXT_OFFSET, winY + 6, TimelineColors.COLOR_TEXT_MEDIUM, "轨道");

        rulerRenderer.renderRuler(drawList, contentX, winY, contentW, RULER_HEIGHT);
    }

    /**
     * 渲染轨道区域（支持垂直滚动）
     */
    public void renderTrackArea(ImDrawList drawList, float winX, float winY, float winW, float viewportHeight) {
        // 保存 trackAreaTop 供简化版本的 getTrackHeaderBounds 使用
        this.trackAreaTop = winY;

        syncViewportFromState();
        float scrollY = viewport.getScrollY();

        float labelWidth = viewport.getLeftLabelWidth();
        float contentX = winX + labelWidth;
        float contentW = Math.max(0f, winW - labelWidth);

        drawList.addRectFilled(winX, winY, winX + winW, winY + viewportHeight, TimelineColors.COLOR_BG_DARK);
        // ★ v9.0 优化：移除整体轨道名称背景填充，让每个轨道自己绘制斑马纹背景色
        // 这样用户可以看到清晰的相间效果，每行轨道更易区分
        // 之前的代码：drawList.addRectFilled(winX, winY, winX + labelWidth, winY + viewportHeight, TimelineColors.COLOR_TRACK_LABEL_BG);
        drawList.addLine(contentX, winY, contentX, winY + viewportHeight, TimelineColors.rgba(60, 60, 60, 200));

        // 重置 TrackControlHit 对象池（每帧开始）
        trackControlHit.reset();

        int trackCount = model != null ? model.getTrackCount() : 0;
        if (trackCount > 0) {
            float rowHeight = TRACK_HEIGHT + TRACK_GAP;
            // ★ 优化：使用 Math 工具类计算可见轨道范围，考虑部分可见轨道
            int[] visibleRange = math.getVisibleTrackRange(trackCount);
            int startIndex = visibleRange[0];
            int endIndex = visibleRange[1];

            for (int idx = startIndex; idx <= endIndex; idx++) {
                TimelineTrack track = model.getTrackAt(idx);
                if (track == null) {
                    continue;
                }
                float trackY = winY + (idx * rowHeight - scrollY);
                
                // 使用 TimelineTrackRenderer 渲染轨道
                trackRenderer.renderTrack(drawList, track, idx, winX, contentX, trackY, contentW);
                
                // 注册控制区域（使用 TimelineTrackControlHit）
                trackControlHit.registerTrackControlAreas(track, winX, trackY, labelWidth);
                
                // 渲染 Clip 内容（使用 TimelineClipRenderer）
                // ★ 性能优化：在渲染所有 clip 前更新一次鼠标位置
                clipRenderer.updateMousePosition();
                drawList.pushClipRect(contentX, trackY, contentX + contentW, trackY + TRACK_HEIGHT);
                for (TimelineClip clip : track.getClips()) {
                    clipRenderer.renderClip(drawList, clip, track, idx, contentX, trackY, TRACK_HEIGHT, contentW);
                }
                drawList.popClipRect();
            }
        }

    }


    /**
     * 渲染播放头（委托给 TimelinePlayheadRenderer）
     */
    public void renderPlayhead(ImDrawList drawList, float contentX, float contentY,
                               float contentW, float contentH) {
        playheadRenderer.renderPlayhead(drawList, contentX, contentY, contentW, contentH);
    }

    /**
     * 渲染拖放目标高亮
     */
    public void renderDropHighlight(ImDrawList drawList, float contentX,
                                    float y, float contentW) {
        drawList.addRectFilled(contentX, y, contentX + contentW, y + TRACK_HEIGHT,
                TimelineColors.COLOR_DROP_HIGHLIGHT);
    }

    public List<TimelineTrackControlHit.Hit> getTrackControlHitAreas() {
        return trackControlHit.getTrackControlHitAreas();
    }

    // ===================== 坐标转换：X ↔ 时间（v9.0 统一 API）=====================

    /**
     * 屏幕像素 X → 时间秒数（不含磁吸）
     * 
     * <p>v9.0：使用 TimelineRenderMath 进行统一坐标转换
     */
    public float xToTime(float mouseX) {
        return math.xToTime(mouseX);
    }

    /**
     * 屏幕像素 X → 时间秒数（带 contentX 参数）
     * 
     * @param screenX 屏幕 X 坐标
     * @param contentX 轨道内容区域的屏幕 X 起点（== winX + leftLabelWidth）
     */
    public float xToTime(float screenX, float contentX) {
        float scrollX = viewportState != null ? viewportState.getScrollX() : 0f;
        float pps = viewportState != null ? viewportState.getPixelsPerSecond() : 100f;
        if (pps <= 0.0001f) return 0f;
        return (screenX - contentX + scrollX) / pps;
    }

    /**
     * 时间 → 像素坐标（带 contentX 参数）
     * 
     * @param time 时间值（秒）
     * @param contentX 轨道内容区域的屏幕 X 起点
     */
    public float timeToX(float time, float contentX) {
        float scrollX = viewportState != null ? viewportState.getScrollX() : 0f;
        float pps = viewportState != null ? viewportState.getPixelsPerSecond() : 100f;
        return contentX + (time * pps) - scrollX;
    }

    /**
     * Y 像素坐标 → 轨道索引（带 scrollY 参数）
     * 
     * @param yLocal 相对于轨道区域顶部的局部 Y
     * @param scrollY 垂直滚动
     */
    public int yToTrackIndex(float yLocal, float scrollY) {
        // ★ 关键修复：对于第一行轨道，即使yLocal非常接近0（可能是浮点精度问题），也应该返回索引0
        // 添加一个小容差来处理边界情况，确保第一行轨道能被正确检测到
        if (yLocal < 0) {
            // 如果yLocal只是稍微小于0（可能是浮点精度问题），且scrollY也是0或很小，可能是第一行轨道
            if (yLocal > -0.5f && scrollY <= 0.5f) {
                yLocal = 0f; // 修正为0，确保第一行轨道能被检测到
            } else {
                return -1;
            }
        }
        float rowHeight = TRACK_HEIGHT + TRACK_GAP;
        float relativeY = yLocal + scrollY;
        if (relativeY < 0) {
            // 同样的容差处理
            if (relativeY > -0.5f) {
                relativeY = 0f;
            } else {
                return -1;
            }
        }
        int index = (int) (relativeY / rowHeight);
        int trackCount = model.getTrackCount();
        return (index >= 0 && index < trackCount) ? index : -1;
    }

    /**
     * 获取渲染数学工具（用于高级操作）
     * 
     * @return TimelineRenderMath 实例
     */
    public TimelineRenderMath getMath() {
        return math;
    }


    // ===================== Getter / Setter =====================
    
    public static float getTrackHeight() { return TRACK_HEIGHT; }
    public static float getTrackGap() { return TRACK_GAP; }
    public static float getRulerHeight() { return RULER_HEIGHT; }
    public static float getHeaderHeight() { return RULER_HEIGHT + RULER_BOTTOM_PADDING; }
    public static float getLeftLabelWidth() {
        return leftLabelWidth;
    }
    public static float getKeyframeHitRadius() { return KEYFRAME_HIT_RADIUS; }

    // ========== TrackHeader 边界查询 ==========
    // ★ v9.0 重构：Rect 和 Circle 已迁移到 TimelineRenderHitTest，这里保留作为类型别名以保持向后兼容
    // 注意：为了保持向后兼容，保留 TimelineRenderer.Rect 和 TimelineRenderer.Circle 作为类型别名
    // 实际实现已经委托给 TimelineRenderHitTest

    /**
     * 轨道头部边界矩形（用于命中检测）
     * 
     * <p>★ v9.0 重构：此类型已迁移到 TimelineRenderHitTest，保留此别名以保持向后兼容。
     * 使用 TimelineRenderHitTest.Rect 作为实际类型，这里只是类型别名。
     */
    public static class Rect {
        public final float x1, y1, x2, y2;
        
        public Rect(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
        
        public boolean contains(float x, float y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
        
        // 与 TimelineRenderHitTest.Rect 互转
        public static Rect from(TimelineRenderHitTest.Rect rect) {
            return new Rect(rect.x1(), rect.y1(), rect.x2(), rect.y2());
        }

    }

    /**
     * 关键帧圆形区域（用于命中检测）
     * 
     * <p>★ v9.0 重构：此类型已迁移到 TimelineRenderHitTest，保留此别名以保持向后兼容。
     * 使用 TimelineRenderHitTest.Circle 作为实际类型，这里只是类型别名。
     */
    public static class Circle {
        public final float x, y, radius;
        
        public Circle(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
        
        public boolean contains(float px, float py) {
            float dx = px - x;
            float dy = py - y;
            return dx * dx + dy * dy <= radius * radius;
        }
        
        // 与 TimelineRenderHitTest.Circle 互转
        public static Circle from(TimelineRenderHitTest.Circle circle) {
            return new Circle(circle.x(), circle.y(), circle.radius());
        }
    }

    /**
     * 获取指定轨道的头部边界矩形（简化版本，使用当前视口状态）
     * 
     * <p>⚠️ 警告：此方法使用了简化的坐标计算，可能不准确。
     * 建议使用完整版本 {@link #getTrackHeaderBounds(int, float, float, float)}。
     * 
     * <p>★ v9.0 重构：委托给 TimelineRenderHitTest
     * 
     * @param trackIndex 轨道索引
     * @return 轨道头部边界矩形（可能不准确，因为缺少 winX 参数）
     */
    public Rect getTrackHeaderBounds(int trackIndex) {
        if (trackIndex < 0 || trackIndex >= model.getTrackCount()) {
            return new Rect(0, 0, 0, 0);
        }

        syncViewportFromState();
        // ★ 修复：无法确定 winX 的准确值，返回一个边界为 0 的矩形
        // 调用方应该使用完整版本的 getTrackHeaderBounds
        float x1 = 0;
        float x2 = viewport.getLeftLabelWidth();
        float rowHeight = TRACK_HEIGHT + TRACK_GAP;
        float y1 = trackAreaTop + (trackIndex * rowHeight - viewport.getScrollY());
        float y2 = y1 + TRACK_HEIGHT;

        return new Rect(x1, y1, x2, y2);
    }

    /**
     * 获取指定轨道的头部边界矩形（完整版本，指定参数）
     * 
     * <p>★ v9.0 重构：委托给 TimelineRenderHitTest
     * 
     * @param trackIndex 轨道索引
     * @param contentX 轨道内容区域的屏幕 X 起点（== winX + leftLabelWidth）
     * @param trackAreaTop 轨道区域顶部 Y 坐标
     * @param scrollY 垂直滚动偏移
     * @return 轨道头部边界矩形
     */
    public Rect getTrackHeaderBounds(int trackIndex, float contentX, float trackAreaTop, float scrollY) {
        if (renderHitTest != null) {
            TimelineRenderHitTest.Rect rect = renderHitTest.getTrackHeaderBounds(
                trackIndex, contentX, trackAreaTop, scrollY, dividerHandler.getLeftLabelWidth());
            return Rect.from(rect);
        }
        
        // 回退到旧实现（向后兼容）
        if (trackIndex < 0 || trackIndex >= model.getTrackCount()) {
            return new Rect(0, 0, 0, 0);
        }

        float x1 = contentX - dividerHandler.getLeftLabelWidth();
        float rowHeight = TRACK_HEIGHT + TRACK_GAP;
        float y1 = trackAreaTop + (trackIndex * rowHeight - scrollY);
        float y2 = y1 + TRACK_HEIGHT;

        return new Rect(x1, y1, contentX, y2);
    }

    /**
     * 获取 Clip 的边界矩形
     * 
     * <p>★ v9.0 重构：委托给 TimelineRenderHitTest
     * 
     * @param track 轨道
     * @param clip 片段
     * @param trackIndex 轨道索引
     * @return Clip 边界矩形
     */
    public Rect getClipRect(TimelineTrack track, TimelineClip clip, int trackIndex) {
        if (renderHitTest != null) {
            syncViewportFromState();
            TimelineRenderHitTest.Rect rect = renderHitTest.getClipRect(
                track, clip, trackIndex, trackAreaTop, viewport.getScrollY());
            return Rect.from(rect);
        }
        
        // 回退到旧实现（向后兼容）
        if (track == null || clip == null || trackIndex < 0) {
            return new Rect(0, 0, 0, 0);
        }

        syncViewportFromState();
        float x1 = math.timeToX(clip.getStartTime());
        float x2 = math.timeToX(clip.getEndTime());
        float rowHeight = TRACK_HEIGHT + TRACK_GAP;
        float y1 = trackAreaTop + (trackIndex * rowHeight - viewport.getScrollY());
        float y2 = y1 + TRACK_HEIGHT;

        return new Rect(x1, y1, x2, y2);
    }

    /**
     * 获取关键帧的圆形区域
     * 
     * <p>★ v9.0 重构：委托给 TimelineRenderHitTest
     * 
     * @param track 轨道
     * @param clip 片段
     * @param keyTime 关键帧时间
     * @return 关键帧圆形区域
     */
    public Circle getKeyframeCircle(TimelineTrack track, TimelineClip clip, float keyTime) {
        if (track == null || clip == null) {
            return new Circle(0, 0, 0);
        }
        
        if (renderHitTest != null) {
            syncViewportFromState();
            TimelineRenderHitTest.Circle circle = renderHitTest.getKeyframeCircle(
                track, clip, keyTime, trackAreaTop, viewport.getScrollY());
            return Circle.from(circle);
        }
        
        // 回退到旧实现（向后兼容）
        // ★ 优化：添加日志警告，提示使用回退实现
        if (model == null) {
            if (logger.isWarnEnabled()) {
                logger.warn("HitTest not initialized and model is null, falling back to manual calculation");
            }
            return new Circle(0, 0, 0);
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("HitTest not initialized, falling back to manual calculation for keyframe circle");
        }

        syncViewportFromState();
        float x = math.timeToX(keyTime);
        
        // 查找轨道索引（性能优化：如果轨道多，这个循环可能较慢）
        int trackIndex = -1;
        int trackCount = model.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            if (model.getTrackAt(i) == track) {
                trackIndex = i;
                break;
            }
        }
        if (trackIndex < 0) {
            return new Circle(0, 0, 0);
        }
        
        float rowHeight = TRACK_HEIGHT + TRACK_GAP;
        float y = trackAreaTop + (trackIndex * rowHeight - viewport.getScrollY()) + TRACK_HEIGHT / 2.0f;

        return new Circle(x, y, KEYFRAME_HIT_RADIUS);
    }
}