package com.chronoblocks.ui.timeline.render;

import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TimelineViewportState;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathType;
import com.chronoblocks.ui.timeline.model.time.TimeKeyframe;
import com.chronoblocks.ui.timeline.model.season.SeasonKeyframe;
// ⚠️ TimelineMarker 已移至时间标尺渲染，不再在轨道上渲染
// import com.chronoblocks.ui.timeline.model.TimelineMarker;

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
    private TimelineClipRenderer clipRenderer;
    private final TimelinePlayheadRenderer playheadRenderer;
    private final TimelineTrackControlHit trackControlHit;
    private final TimelineRenderHitTest renderHitTest;
    private final TimelineAudioRenderer audioRenderer;

    // ⭐ 拖动状态查询器（用于实时显示拖动中的关键帧位置）
    private DragStateQuery dragStateQuery = null;

    /**
     * 拖动状态查询接口（用于渲染时获取拖动中的临时时间）
     */
    public interface DragStateQuery {
        /**
         * 获取正在拖动中的自动化关键帧的临时时间
         * @param keyframe 关键帧
         * @return 临时时间，如果没有拖动则返回 -1
         */
        float getDraggedAutomationKeyframeTime(TimelineSelectable keyframe);
        
        /**
         * 获取正在拖动中的 Clip 关键帧的临时时间
         * @param clip 片段
         * @param keyframeTime 关键帧的原始时间
         * @return 临时时间，如果没有拖动则返回 -1
         */
        float getDraggedClipKeyframeTime(TimelineClip clip, float keyframeTime);
        
        /**
         * ⭐ 获取正在拖动中的标记的临时时间
         * @param marker 标记
         * @return 临时时间，如果没有拖动则返回 -1
         */
        float getDraggedMarkerTime(com.chronoblocks.ui.timeline.model.TimelineMarker marker);
        
        /**
         * ⭐ 获取正在拖动中的 Path Clip 的临时开始时间
         * @param clip Path Clip
         * @return 临时开始时间，如果没有拖动则返回 -1
         */
        float getDraggedPathClipStartTime(CameraPathClip clip);
        
        /**
         * ⭐ 获取正在拖动中的 Path Clip 的临时结束时间
         * @param clip Path Clip
         * @return 临时结束时间，如果没有拖动则返回 -1
         */
        float getDraggedPathClipEndTime(CameraPathClip clip);
    }

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
        this.rulerRenderer.setModel(model);  // ⭐ 设置 model 用于渲染标记
        this.trackRenderer = new TimelineTrackRenderer(viewport);
        this.clipRenderer = new TimelineClipRenderer(model, math);
        this.playheadRenderer = new TimelinePlayheadRenderer(model, math);
        this.trackControlHit = new TimelineTrackControlHit();
        this.audioRenderer = new TimelineAudioRenderer(viewport);
        
        // 初始化命中检测组件
        this.renderHitTest = new TimelineRenderHitTest(math, viewport, model);
    }

    /**
     * 设置拖动状态查询器（用于实时显示拖动中的关键帧位置）
     * 
     * @param dragStateQuery 拖动状态查询器，可以为 null
     */
    public void setDragStateQuery(DragStateQuery dragStateQuery) {
        this.dragStateQuery = dragStateQuery;
        // 同时传递给 clipRenderer
        if (clipRenderer != null) {
            clipRenderer.setDragStateQuery(dragStateQuery);
        }
        // 同时传递给 audioRenderer（用于音频点实时预览）
        if (audioRenderer != null) {
            audioRenderer.setDragStateQuery(dragStateQuery);
        }
        // ⭐ 同时传递给 rulerRenderer（用于标记拖动实时预览）
        if (rulerRenderer != null) {
            rulerRenderer.setDragStateQuery(dragStateQuery);
        }
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
        dividerHandler.updateLeftSectionWidth(winX, winY, totalHeight, false);
        // 同步静态变量（向后兼容）
        leftLabelWidth = dividerHandler.getLeftLabelWidth();
        viewport.setLeftLabelWidth(leftLabelWidth);
    }

    /**
     * 更新左侧轨道标签区域宽度（带跳过标志）
     */
    public void updateLeftSectionWidth(float winX, float winY, float totalHeight, boolean shouldSkipDividerDrag) {
        dividerHandler.updateLeftSectionWidth(winX, winY, totalHeight, shouldSkipDividerDrag);
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
                // ⚠️ EntityTrack 已隐藏，等待下一版本
                if (track.getType() == TrackType.ENTITY) {
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
                
                // ⭐ 渲染摄像机路径片段（Path Clips）- 在关键帧之前渲染，使其在关键帧下方
                if (track.getType() == TrackType.CAMERA && track.hasCameraPathClips()) {
                    renderCameraPathClips(drawList, track, contentX, trackY, contentW);
                }
                
                // ★ 渲染轨道级自动化关键帧（摄像机关键帧、天气关键帧等）
                renderAutomationKeyframes(drawList, track, contentX, trackY, contentW);
                
                // ★ 渲染音频轨道的事件点（如果是音频轨道）
                if (track.getType() == TrackType.AUDIO) {
                    // 音频轨道使用点事件渲染（AudioEvent），而不是复用普通自动化关键帧渲染
                    audioRenderer.renderAudioEvents(drawList, track, contentX, trackY, contentW);
                }
            }
        }

    }

    /**
     * ⭐ 渲染摄像机路径片段（Path Clips）
     * 
     * <p>Path Clip 作为"块"显示在轨道上，位于关键帧下方。
     * 不同路径类型使用不同颜色区分。
     */
    private static final float PATH_CLIP_MIN_WIDTH_PX = 10f; // 太窄也要可点中
    
    private void renderCameraPathClips(ImDrawList drawList, TimelineTrack track,
                                      float contentX, float y, float contentW) {
        if (track == null || !track.hasCameraPathClips()) {
            return;
        }
        
        // ⭐ 性能优化：移除重复的 syncViewportFromState() 调用
        // renderTrackArea 已经调用了 syncViewportFromState()，这里不需要重复调用
        
        // ⭐ 布局：与容器片段保持一致的高度和位置
        float pathClipHeight = TimelineClipRenderer.CLIP_HEIGHT;  // 20px（与容器片段一致）
        // 与容器片段相同的垂直对齐：CLIP_VERT_PADDING = 2.0f（轨道高度24px - 片段高度20px）/ 2
        float pathClipY = y + 2.0f;  // 与容器片段相同的垂直对齐
        
        // ⭐ 性能优化：直接计算可见时间范围，避免创建数组
        float contentXForTime = viewport.getContentX();
        float contentWForTime = viewport.getContentWidth();
        float visibleStart = math.xToTime(contentXForTime);
        float visibleEnd = math.xToTime(contentXForTime + contentWForTime);
        
        // ⭐ 性能优化：获取鼠标位置用于 hover 检测（只获取一次，避免重复调用）
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        
        // 遍历轨道上的所有 Path Clip
        for (CameraPathClip clip : track.getCameraPathClips()) {
            if (clip == null || !clip.isEnabled()) {
                continue;
            }
            
            // ⭐ 检查是否正在拖动中，如果是则使用临时时间
            float startTime = clip.getStartTime();
            float endTime = clip.getEndTime();
            if (dragStateQuery != null) {
                float tempStart = dragStateQuery.getDraggedPathClipStartTime(clip);
                float tempEnd = dragStateQuery.getDraggedPathClipEndTime(clip);
                if (tempStart >= 0) {
                    startTime = tempStart;
                }
                if (tempEnd >= 0) {
                    endTime = tempEnd;
                }
            }
            
            // ⭐ 性能优化：早期跳过不可见的 Path Clip（在计算屏幕坐标之前）
            if (endTime < visibleStart || startTime > visibleEnd) {
                continue;
            }
            
            // 计算屏幕坐标
            float x1 = math.timeToX(startTime);
            float x2 = math.timeToX(endTime);
            
            // ⭐ 性能优化：早期跳过不在内容区域内的 Path Clip
            if (x2 < contentX || x1 > contentX + contentW) {
                continue;
            }
            
            // 裁剪到内容区域
            float clipX1 = Math.max(contentX, x1);
            float clipX2 = Math.min(contentX + contentW, x2);
            float clipW = clipX2 - clipX1;
            
            // ⭐ 最小宽度保证（确保即使很窄的 Path Clip 也能被点击）
            if (clipW < PATH_CLIP_MIN_WIDTH_PX) {
                float centerX = (clipX1 + clipX2) * 0.5f;
                clipX1 = centerX - PATH_CLIP_MIN_WIDTH_PX * 0.5f;
                clipX2 = centerX + PATH_CLIP_MIN_WIDTH_PX * 0.5f;
                clipW = clipX2 - clipX1;
            }
            
            if (clipW <= 0) {
                continue;
            }
            
            // Hover 检测
            boolean hovering = mouseX >= clipX1 && mouseX <= clipX2 &&
                              mouseY >= pathClipY && mouseY <= pathClipY + pathClipHeight;
            
            // 检查是否被选中（isSelected 使用 HashSet.contains，已经是 O(1)）
            boolean isSelected = model != null && model.isSelected(clip);
            
            // ⭐ 性能优化：只调用一次 getPathType()，避免重复调用
            CameraPathType pathType = clip.getPathType();
            int clipColor = getPathClipColor(pathType, isSelected, hovering);
            int borderColor = isSelected 
                ? TimelineColors.COLOR_KEYFRAME_SELECTED 
                : TimelineColors.rgba(0, 0, 0, 200);
            
            // 绘制 Path Clip 块（扁平化风格，无圆角）
            // ⭐ 统一风格：与容器片段、音频片段保持一致，使用扁平化设计
            drawList.addRectFilled(
                clipX1, pathClipY, 
                clipX2, pathClipY + pathClipHeight,
                clipColor, 0.0f, 0  // 无圆角
            );
            
            // 边框（选中时高亮，扁平化）
            float borderWidth = isSelected ? 2.0f : 1.0f;
            drawList.addRect(
                clipX1, pathClipY,
                clipX2, pathClipY + pathClipHeight,
                borderColor, 0.0f, 0, borderWidth  // 无圆角
            );
            
            // ⭐ 移除白色竖条把手，统一扁平化风格（与容器片段、音频片段保持一致）
            
            // ⭐ 性能优化：只在需要时才计算和绘制文本（块足够宽时才绘制）
            // ⭐ 性能优化：避免字符串格式化，使用简单的字符串拼接
            if (clipW > 40) {
                String label = getPathClipLabelFast(clip, pathType);
                float textX = clipX1 + 6;
                float textY = pathClipY + pathClipHeight * 0.5f;
                
                // 文本颜色（根据背景色自动调整）
                int textColor = TimelineColors.COLOR_TEXT_LIGHT;
                if (isSelected) {
                    textColor = TimelineColors.COLOR_TEXT_DARK;
                }
                
                // 绘制文本
                drawList.addText(textX, textY - 7, textColor, label);
            }
        }
    }
    
    /**
     * 获取 Path Clip 的颜色（根据类型、选中状态、hover 状态）
     * ⭐ 性能优化：内联颜色计算，减少方法调用
     */
    private int getPathClipColor(CameraPathType pathType, boolean isSelected, boolean hovering) {
        // ⭐ 性能优化：根据状态直接返回最终颜色，避免多次调用 lighten/adjustAlpha
        if (isSelected) {
            // 选中时：使用预计算的亮色
            return switch (pathType) {
                case ORBIT -> TimelineColors.rgba(120, 210, 255, 240);  // 蓝色/青色（选中）
                case DOLLY -> TimelineColors.rgba(120, 255, 195, 240);  // 绿色（选中）
                case CRANE -> TimelineColors.rgba(255, 210, 120, 240);  // 橙色（选中）
                case RAIL -> TimelineColors.rgba(230, 180, 255, 240);   // 紫色（选中）
                default -> TimelineColors.rgba(180, 180, 180, 240);     // 灰色（选中）
            };
        } else if (hovering) {
            // Hover 时：使用预计算的稍亮色
            return switch (pathType) {
                case ORBIT -> TimelineColors.rgba(90, 190, 255, 200);   // 蓝色/青色（hover）
                case DOLLY -> TimelineColors.rgba(90, 255, 180, 200);   // 绿色（hover）
                case CRANE -> TimelineColors.rgba(255, 200, 120, 200);  // 橙色（hover）
                case RAIL -> TimelineColors.rgba(220, 170, 255, 200);  // 紫色（hover）
                default -> TimelineColors.rgba(170, 170, 170, 200);     // 灰色（hover）
            };
        } else {
            // 默认：基础颜色
            return switch (pathType) {
                case ORBIT -> TimelineColors.rgba(100, 180, 255, 180);  // 蓝色/青色
                case DOLLY -> TimelineColors.rgba(100, 255, 150, 180);  // 绿色
                case CRANE -> TimelineColors.rgba(255, 180, 100, 180);  // 橙色
                case RAIL -> TimelineColors.rgba(200, 150, 255, 180);   // 紫色
                default -> TimelineColors.rgba(150, 150, 150, 180);      // 灰色（FREE 或其他）
            };
        }
    }
    
    /**
     * 获取 Path Clip 的显示标签（性能优化版本）
     * ⭐ 优化：接受 pathType 参数，避免重复调用 getPathType()
     */
    private String getPathClipLabelFast(CameraPathClip clip, CameraPathType pathType) {
        String typeName = switch (pathType) {
            case ORBIT -> "ORBIT";
            case DOLLY -> "DOLLY";
            case CRANE -> "CRANE";
            case RAIL -> "RAIL";
            default -> "PATH";
        };
        
        // ⭐ 性能优化：如果是 Orbit，显示角度范围（避免使用 String.format，改用简单拼接）
        if (pathType == CameraPathType.ORBIT && clip instanceof com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip orbit) {
            float startAngle = orbit.getStartAngle();
            float endAngle = orbit.getEndAngle();
            float angleRange = endAngle - startAngle;
            if (angleRange < 0) {
                angleRange += 360f;
            }
            // ⭐ 性能优化：使用 StringBuilder 或简单字符串拼接，避免 String.format
            // String.format 会创建 Formatter 对象，相对昂贵
            int angleInt = Math.round(angleRange);
            return typeName + " · " + angleInt + "°";
        }
        
        return typeName;
    }
    
    /**
     * 获取 Path Clip 的显示标签（旧版本，保留用于向后兼容）
     * @deprecated 使用 getPathClipLabelFast 代替，避免重复调用 getPathType()
     */
    @Deprecated
    private String getPathClipLabel(CameraPathClip clip) {
        return getPathClipLabelFast(clip, clip.getPathType());
    }
    
    /**
     * 渲染轨道级自动化关键帧（摄像机关键帧、天气关键帧等）
     * 
     * <p>参考 TimelineClipRenderer 中渲染关键帧的逻辑，但用于轨道级别的自动化关键帧。
     */
    private void renderAutomationKeyframes(ImDrawList drawList, TimelineTrack track,
                                           float contentX, float y, float contentW) {
        if (track == null || track.getType() == TrackType.AUDIO || !track.hasAutomationKeyframes()) {
            return;
        }

        syncViewportFromState();
        float centerY = y + TimelineRenderer.TRACK_HEIGHT / 2.0f;
        
        // 获取可见时间范围（性能优化：只渲染可见的关键帧）
        float[] visibleTimeRange = math.getVisibleTimeRange();
        float visibleStart = visibleTimeRange[0];
        float visibleEnd = visibleTimeRange[1];
        
        // 获取鼠标位置用于 hover 检测
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        
        // 遍历轨道上的所有自动化关键帧
        for (TimelineSelectable keyframe : track.getAutomationKeyframes()) {
            if (keyframe == null) continue;
            
            // ⭐⭐ 修复：检查是否正在拖动中，如果是则使用临时时间
            float keyTime = keyframe.getStartTime();
            if (dragStateQuery != null) {
                float draggedTime = dragStateQuery.getDraggedAutomationKeyframeTime(keyframe);
                if (draggedTime >= 0) {
                    keyTime = draggedTime; // 使用拖动中的临时时间
                }
            }
            
            // ★ 优化：早期跳过不可见的关键帧
            if (keyTime < visibleStart || keyTime > visibleEnd) {
                continue;
            }
            
            float kx = math.timeToX(keyTime);
            if (kx < contentX || kx > contentX + contentW) continue;
            // ✅ 像素对齐：让“竖线”和“形状”使用同一个像素中心，避免看起来左右不对齐
            float kxAligned = snapToPixelCenter(kx);

            // Hover 检测用圆形区域（与 KEYFRAME_HIT_RADIUS 一致）
            float dx = mouseX - kx;
            float dy = mouseY - centerY;
            float distSq = dx * dx + dy * dy;
            boolean hovering = distSq <= KEYFRAME_HIT_RADIUS * KEYFRAME_HIT_RADIUS;
            
            // 检查是否被选中
            boolean isSelected = model != null && model.isSelected(keyframe);
            
            // 确定关键帧类型（如果是 CameraKeyframe）
            CameraKeyframe.KeyType keyType = null;
            if (keyframe instanceof CameraKeyframe) {
                keyType = ((CameraKeyframe) keyframe).getKeyType();
            }
            
            // ⚠️ 标记已移至时间标尺渲染，不再在轨道上渲染
            // boolean isMarker = keyframe instanceof TimelineMarker;
            
            // ⭐ 时间关键帧使用特殊颜色（橙色系，与时间轨道颜色一致）
            boolean isTimeKeyframe = keyframe instanceof TimeKeyframe;
            
            // ⭐ v1.0：季节关键帧使用特殊颜色（绿色系，与季节轨道颜色一致）
            boolean isSeasonKeyframe = keyframe instanceof SeasonKeyframe;
            
            // 确定颜色：选中 > hover > 默认
            int kfCol;
            if (isSelected) {
                kfCol = TimelineColors.COLOR_KEYFRAME_SELECTED;
            } else if (hovering) {
                if (isTimeKeyframe) {
                    kfCol = TimelineColors.rgba(255, 200, 100, 255);  // 时间关键帧 hover：亮橙色
                } else if (isSeasonKeyframe) {
                    kfCol = TimelineColors.rgba(140, 220, 140, 255);  // 季节关键帧 hover：亮绿色
                } else {
                    kfCol = TimelineColors.COLOR_KEYFRAME_HOVER;
                }
            } else {
                if (isTimeKeyframe) {
                    kfCol = TimelineColors.rgba(240, 160, 60, 255);  // 时间关键帧默认：橙色（与 TrackType.TIME 颜色一致）
                } else if (isSeasonKeyframe) {
                    kfCol = TimelineColors.rgba(100, 200, 120, 255);  // 季节关键帧默认：绿色（与 TrackType.SEASON 颜色一致）
                } else {
                    kfCol = TimelineColors.COLOR_KEYFRAME;
                }
            }

            // ⚠️ 标记已移至时间标尺渲染，不再在轨道上渲染
            {
                // ✅ 像素级对齐：不要用粗线 addLine（容易出现视觉偏移）
                // 改为明确宽度的填充矩形：1px 主线 + 3px 阴影，中心严格对齐 kxAligned。
                float lineBottom = y + TimelineRenderer.TRACK_HEIGHT;
                drawList.addRectFilled(kxAligned - 1.5f, y, kxAligned + 1.5f, lineBottom, TimelineColors.rgba(0, 0, 0, 80));
                drawList.addRectFilled(kxAligned - 0.5f, y, kxAligned + 0.5f, lineBottom, kfCol);
            }

            // 绘制关键帧形状（根据 KeyType）
            // 增大关键帧尺寸
            float keyframeSize = TimelineClipRenderer.KEYFRAME_RADIUS * 1.5f;
            if (isSelected) {
                // 选中时稍微放大
                keyframeSize *= 1.2f;
            }
            
            // 绘制选中外圈
            if (isSelected) {
                drawList.addCircle(kxAligned, centerY, keyframeSize + 3.0f, TimelineColors.COLOR_KEYFRAME_SELECTED_RING, 0, 2.5f);
            }
            
            // 根据关键帧类型绘制不同形状
            if (keyType != null) {
                // CameraKeyframe：根据 KeyType 绘制不同形状
                drawKeyframeShape(drawList, kxAligned, centerY, keyframeSize, kfCol, keyType, isSelected);
            } else if (isTimeKeyframe) {
                // ⭐ 时间关键帧：使用填充圆形（橙色，与时间轨道颜色一致）
                if (isSelected) {
                    // 选中时绘制空心圆 + 外圈
                    drawList.addCircle(kxAligned, centerY, keyframeSize + 1.5f, TimelineColors.rgba(0, 0, 0, 180), 0, 1.5f); // 描边
                    drawList.addCircle(kxAligned, centerY, keyframeSize, kfCol, 0, 2.0f); // 空心
                } else {
                    // 默认：填充圆形（橙色）
                    drawList.addCircleFilled(kxAligned, centerY, keyframeSize + 1.5f, TimelineColors.rgba(0, 0, 0, 180)); // 描边
                    drawList.addCircleFilled(kxAligned, centerY, keyframeSize, kfCol); // 填充
                }
            } else if (isSeasonKeyframe) {
                // ⭐ v1.0：季节关键帧：使用填充圆形（绿色，与季节轨道颜色一致）
                if (isSelected) {
                    // 选中时绘制空心圆 + 外圈
                    drawList.addCircle(kxAligned, centerY, keyframeSize + 1.5f, TimelineColors.rgba(0, 0, 0, 180), 0, 1.5f); // 描边
                    drawList.addCircle(kxAligned, centerY, keyframeSize, kfCol, 0, 2.0f); // 空心
                } else {
                    // 默认：填充圆形（绿色）
                    drawList.addCircleFilled(kxAligned, centerY, keyframeSize + 1.5f, TimelineColors.rgba(0, 0, 0, 180)); // 描边
                    drawList.addCircleFilled(kxAligned, centerY, keyframeSize, kfCol); // 填充
                }
            } else {
                // 默认圆形（其他类型的自动化关键帧，如 WeatherKeyframe）
                if (isSelected) {
                    // 选中时绘制空心
                    drawList.addCircle(kxAligned, centerY, keyframeSize + 1.5f, TimelineColors.rgba(0, 0, 0, 180), 0, 1.5f); // 描边
                    drawList.addCircle(kxAligned, centerY, keyframeSize, kfCol, 0, 2.0f); // 空心
                } else {
                    drawList.addCircleFilled(kxAligned, centerY, keyframeSize + 1.5f, TimelineColors.rgba(0, 0, 0, 180)); // 描边
                    drawList.addCircleFilled(kxAligned, centerY, keyframeSize, kfCol); // 填充
                }
            }
        }
    }

    /**
     * 将 X 坐标对齐到像素中心（+0.5），避免线条/形状在亚像素时出现“看起来偏左/偏右”的错觉。
     */
    private static float snapToPixelCenter(float x) {
        return (float) Math.floor(x) + 0.5f;
    }

    /**
     * 根据 KeyType 绘制关键帧形状
     * 
     * @param drawList ImDrawList 实例
     * @param x 中心 X 坐标
     * @param y 中心 Y 坐标
     * @param size 形状大小（半径或边长的一半）
     * @param color 填充颜色
     * @param keyType 关键帧类型
     * @param isSelected 是否被选中（选中时绘制空心）
     */
    private void drawKeyframeShape(ImDrawList drawList, float x, float y, float size, int color, CameraKeyframe.KeyType keyType, boolean isSelected) {
        int outlineColor = TimelineColors.rgba(0, 0, 0, 180);
        float outlineWidth = isSelected ? 2.0f : 1.5f;
        float shapeWidth = isSelected ? 2.0f : 1.0f; // 选中时线条更粗
        
        switch (keyType) {
            case STANDARD -> {
                // 菱形（Diamond）：4个顶点
                float[] diamondX = {x, x + size, x, x - size};
                float[] diamondY = {y - size, y, y + size, y};
                // 绘制描边
                for (int i = 0; i < 4; i++) {
                    int next = (i + 1) % 4;
                    drawList.addLine(diamondX[i], diamondY[i], diamondX[next], diamondY[next], outlineColor, outlineWidth);
                }
                if (isSelected) {
                    // 选中时绘制空心（只绘制轮廓）
                    for (int i = 0; i < 4; i++) {
                        int next = (i + 1) % 4;
                        drawList.addLine(diamondX[i], diamondY[i], diamondX[next], diamondY[next], color, shapeWidth);
                    }
                } else {
                    // 绘制填充（使用两个三角形）
                    drawList.addTriangleFilled(x, y - size, x + size, y, x, y + size, color);
                    drawList.addTriangleFilled(x, y - size, x - size, y, x, y + size, color);
                }
            }
            case CORNER -> {
                // 三角形（Triangle）：向上指向
                if (isSelected) {
                    // 选中时绘制空心
                    drawList.addTriangle(x, y - size, x - size, y + size, x + size, y + size, outlineColor, outlineWidth);
                    drawList.addTriangle(x, y - size, x - size, y + size, x + size, y + size, color, shapeWidth);
                } else {
                    drawList.addTriangleFilled(x, y - size, x - size, y + size, x + size, y + size, color);
                    // 绘制描边
                    drawList.addTriangle(x, y - size, x - size, y + size, x + size, y + size, outlineColor, outlineWidth);
                }
            }
            case HOLD -> {
                // 方形（Square）
                float halfSize = size * 0.8f; // 稍微小一点，看起来更协调
                if (isSelected) {
                    // 选中时绘制空心
                    drawList.addRect(x - halfSize, y - halfSize, x + halfSize, y + halfSize, outlineColor, 0.0f, 0, outlineWidth);
                    drawList.addRect(x - halfSize, y - halfSize, x + halfSize, y + halfSize, color, 0.0f, 0, shapeWidth);
                } else {
                    drawList.addRectFilled(x - halfSize, y - halfSize, x + halfSize, y + halfSize, color);
                    drawList.addRect(x - halfSize, y - halfSize, x + halfSize, y + halfSize, outlineColor, 0.0f, 0, outlineWidth);
                }
            }
            case SMOOTH -> {
                // 圆形（Circle）
                if (isSelected) {
                    // 选中时绘制空心
                    drawList.addCircle(x, y, size + outlineWidth, outlineColor, 0, outlineWidth);
                    drawList.addCircle(x, y, size, color, 0, shapeWidth);
                } else {
                    drawList.addCircleFilled(x, y, size + outlineWidth, outlineColor); // 描边
                    drawList.addCircleFilled(x, y, size, color); // 填充
                }
            }
            case MANUAL_BEZIER -> {
                // 六边形（Hexagon）：6个顶点
                int numPoints = 6;
                float[] hexX = new float[numPoints];
                float[] hexY = new float[numPoints];
                for (int i = 0; i < numPoints; i++) {
                    float angle = (float) (Math.PI / 3.0 * i - Math.PI / 2.0); // 从顶部开始
                    hexX[i] = x + size * (float) Math.cos(angle);
                    hexY[i] = y + size * (float) Math.sin(angle);
                }
                // 绘制描边
                for (int i = 0; i < numPoints; i++) {
                    int next = (i + 1) % numPoints;
                    drawList.addLine(hexX[i], hexY[i], hexX[next], hexY[next], outlineColor, outlineWidth);
                }
                if (isSelected) {
                    // 选中时绘制空心（只绘制轮廓）
                    for (int i = 0; i < numPoints; i++) {
                        int next = (i + 1) % numPoints;
                        drawList.addLine(hexX[i], hexY[i], hexX[next], hexY[next], color, shapeWidth);
                    }
                } else {
                    // 绘制填充（使用多个三角形，从中心点）
                    for (int i = 0; i < numPoints; i++) {
                        int next = (i + 1) % numPoints;
                        drawList.addTriangleFilled(x, y, hexX[i], hexY[i], hexX[next], hexY[next], color);
                    }
                }
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
