package com.director.ui.timeline.ui;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.timeline.interaction.TimelineNavigationHandler;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import com.director.ui.timeline.render.TimelineRenderer; // 引入 Renderer 获取常量
import com.director.client.renderer.CameraPathDebugRenderer;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * 时间线控制栏 (v8.1 修复版)
 * <p>
 * 职责：专门负责渲染顶部工具栏的 UI
 * - 轨道管理按钮（新建、删除）
 * - 撤销/重做按钮
 * - 播放控制
 * - 时间显示和跳转
 * - 吸附控制
 * - 缩放控制
 * 
 * <p>优化点：
 * <ul>
 *   <li>Zero-GC: 移除了每帧分配的 float[] 数组，改为成员变量复用</li>
 *   <li>Layout: 播放控制区使用紧凑 FramePadding；配合全局 ButtonTextAlign 与 AlignTextToFramePadding 对齐</li>
 *   <li>UX: 限制了缩放滑块的最大宽度，防止在高分辨率下过长</li>
 * </ul>
 * 
 * <p>修复记录：
 * <ul>
 *   <li>[BugFix] ensurePlayheadVisible 坐标计算错误：改用 WindowPos 而非 CursorPos</li>
 *   <li>[Layout] 增加了对时间定位滑块的最小宽度检查，防止窗口过窄时布局崩溃</li>
 *   <li>[UX] 增加了总时长的上限（扩大到24小时）</li>
 * </ul>
 * 
 * @author Director Team
 * @version 8.1
 */
public class TimelineControlBar {

    private final TimelineModel model;
    private final TimelineCommandHistory commandHistory;
    private final TimelineViewportState viewportState;
    private final Callbacks callbacks;
    private final TimelineNavigationHandler navigationHandler;

    // ⭐ Facade 架构：命令门面（用于控制命令，如播放/暂停/跳转）
    private com.director.ui.commands.UICommandFacade commands;

    // 是否显示相机轨迹（调试用）
    private boolean showCameraPath = false;

    // ========== 性能优化：复用数据容器，避免每帧 GC ==========
    // 这些数组在构造函数中分配一次，在 render() 方法中复用
    // 避免每帧创建新数组导致的 GC 压力（60 FPS 下每秒创建 180 个数组对象）
    private final float[] tempDuration = new float[1];
    private final float[] tempCurrentTime = new float[1];
    private final float[] tempPps = new float[1];
    
    // ========== 布局和交互常量 ==========
    /** 总时长限制：最小时长（秒） */
    private static final float MIN_DURATION = 1.0f;
    /** 总时长限制：最大时长（秒），24小时 */
    private static final float MAX_DURATION = 86400.0f; // 24小时
    /** 总时长拖动步长（秒） */
    private static final float DURATION_DRAG_STEP = 0.5f;
    /** 跳转时间拖动步长（秒） */
    private static final float TIME_JUMP_DRAG_STEP = 0.01f;
    
    /** 缩放滑块最小宽度（像素） */
    private static final float ZOOM_SLIDER_MIN_WIDTH = 80f;
    /** 缩放滑块最大宽度（像素） */
    private static final float ZOOM_SLIDER_MAX_WIDTH = 150.0f;
    /** 缩放滑块占剩余空间的比例 */
    private static final float ZOOM_SLIDER_RATIO = 0.4f;
    /** 最小可用宽度检查（像素），小于此值不渲染滑块 */
    private static final float MIN_AVAILABLE_WIDTH = 50f;
    
    /** 时间定位滑块最小剩余空间阈值（像素），小于此值不显示 */
    private static final float SCRUBBER_MIN_REMAINING_WIDTH = 120f;
    /** 时间定位滑块右边距（像素），防止 ImGui 计算误差导致换行 */
    private static final float SCRUBBER_RIGHT_MARGIN = 20f;
    /** 时间定位滑块最大宽度（像素） */
    private static final float SCRUBBER_MAX_WIDTH = 300f;
    
    /** 播放头自动跟随边距（像素），播放头距离可见区域边缘的最小距离 */
    private static final float PLAYHEAD_AUTO_FOLLOW_MARGIN = 50f;
    /** 滚动条宽度估算（像素），用于计算内容区域宽度 */
    private static final float SCROLLBAR_WIDTH_ESTIMATE = 20f;

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String btnLabel(String icon, String key) {
        return icon + " " + tr(key);
    }

    /**
     * 控制栏动作回调接口
     */
    public interface Callbacks {
        void onAddTrackClicked();
        void onDeleteSelectedClicked();
        void onFitToViewClicked();
    }

    public TimelineControlBar(TimelineModel model,
                              TimelineViewportState viewportState,
                              TimelineCommandHistory commandHistory,
                              Callbacks callbacks,
                              TimelineNavigationHandler navigationHandler) {
        this.model = model;
        this.viewportState = viewportState;
        this.commandHistory = commandHistory;
        this.callbacks = callbacks;
        this.navigationHandler = navigationHandler;
    }

    /**
     * 渲染控制栏
     */
    public void render() {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.ELEMENT_SPACING, UITheme.Spacing.ELEMENT_SPACING);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);

        // ========== 轨道管理按钮 ==========
        if (ImGui.button(btnLabel(Icons.ADD, DirectorLang.Timeline.NEW_TRACK), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            callbacks.onAddTrackClicked();
        }

        ImGui.sameLine();

        boolean canDelete = canDeleteSelection();
        if (!canDelete) ImGui.beginDisabled();
        if (ImGui.button(btnLabel(Icons.DELETE, DirectorLang.Common.DELETE), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            callbacks.onDeleteSelectedClicked();
        }
        if (!canDelete) ImGui.endDisabled();

        renderSeparator();

        // ========== 撤销/重做按钮 ==========
        boolean canUndo = commandHistory.canUndo();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(btnLabel(Icons.UNDO, DirectorLang.Menu.EDIT_UNDO), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.undo();
        }
        if (!canUndo) ImGui.endDisabled();

        ImGui.sameLine();

        boolean canRedo = commandHistory.canRedo();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(btnLabel(Icons.REDO, DirectorLang.Menu.EDIT_REDO), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.redo();
        }
        if (!canRedo) ImGui.endDisabled();

        ImGui.sameLine();
        
        // 使用 alignTextToFramePadding 确保纯文本与按钮垂直对齐
        ImGui.alignTextToFramePadding();
        UITheme.label(String.format("(%d/%d)", 
            commandHistory.getUndoSize(), 
            commandHistory.getRedoSize()), UITheme.DarkTheme.TEXT_DISABLED);

        renderSeparator();

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.ICON_BUTTON_PADDING_X, UITheme.Spacing.ICON_BUTTON_PADDING_Y);

        // ========== 播放控制（Facade 架构）==========
        if (ImGui.button(Icons.SKIP_BACK, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            if (commands != null && commands.playback() != null) {
                commands.playback().goToStart();
            } else {
                // 降级方案：直接调用 model（向后兼容）
                model.jumpToStart();
            }
        }
        ImGui.sameLine();
        String playPauseKey = model.isPlaying()
                ? DirectorLang.Menu.PLAYBACK_PAUSE
                : DirectorLang.Menu.PLAYBACK_PLAY;
        String playPauseIcon = model.isPlaying() ? Icons.PAUSE : Icons.PLAY;
        if (ImGui.button(btnLabel(playPauseIcon, playPauseKey), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            if (commands != null && commands.playback() != null) {
                commands.playback().togglePlayPause();
            } else {
                // 降级方案：直接调用 model（向后兼容）
                model.togglePlay();
            }
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.SKIP_FORWARD, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            if (commands != null && commands.playback() != null) {
                commands.playback().goToEnd();
            } else {
                // 降级方案：直接调用 model（向后兼容）
                model.jumpToEnd();
            }
        }

        ImGui.popStyleVar();

        ImGui.sameLine();
        // 轨迹调试开关
        imgui.type.ImBoolean pathFlag = new imgui.type.ImBoolean(showCameraPath);
        if (ImGui.checkbox(btnLabel(Icons.CAMERA, DirectorLang.Timeline.CAMERA_PATH), pathFlag)) {
            showCameraPath = pathFlag.get();
            CameraPathDebugRenderer.getInstance().setEnabled(showCameraPath);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Timeline.CAMERA_PATH_TOOLTIP));
        }
        ImGui.sameLine();
        
        ImGui.alignTextToFramePadding();
        ImGui.text(tr(DirectorLang.Timeline.TIME_LABEL));

        // ========== 当前时间显示 (只读) ==========
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.text(String.format("%.2f", model.getCurrentTime()));
        ImGui.sameLine();
        ImGui.text("/");

        // ========== 总时长输入（可编辑）==========
        ImGui.sameLine();
        
        // 数据同步：Model -> UI
        tempDuration[0] = model.getDuration();
        
        ImGui.pushItemWidth(UITheme.Spacing.INPUT_WIDTH_MEDIUM);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.15f, 0.25f, 0.45f, 0.8f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.2f, 0.35f, 0.6f, 0.9f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0.25f, 0.4f, 0.7f, 1.0f);
        
        // 优化：扩大最大时长支持到 24小时 (86400s)
        if (ImGui.dragFloat("##duration", tempDuration, DURATION_DRAG_STEP, MIN_DURATION, MAX_DURATION, "%.1fs")) {
            // 数据同步：UI -> Model
            float newDuration = Math.max(MIN_DURATION, Math.min(MAX_DURATION, tempDuration[0]));
            model.setDuration(newDuration);
        }
        
        ImGui.popStyleColor(3);
        ImGui.popItemWidth();

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(Icons.EDIT + " " + tr(DirectorLang.Timeline.DURATION_TOOLTIP_TITLE));
            ImGui.textDisabled(tr(DirectorLang.Timeline.DURATION_TOOLTIP_HINT));
            ImGui.endTooltip();
        }

        // ========== 精确时间输入 (跳转) ==========
        ImGui.sameLine();
        renderSeparator();
        
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.text(tr(DirectorLang.Timeline.JUMP_LABEL));
        
        ImGui.sameLine();
        
        // 数据同步
        tempCurrentTime[0] = model.getCurrentTime();
        
        ImGui.pushItemWidth(UITheme.Spacing.INPUT_WIDTH_SMALL);
        if (ImGui.dragFloat("##currentTime", tempCurrentTime, TIME_JUMP_DRAG_STEP, 0, model.getDuration(), "%.2fs")) {
            model.setCurrentTime(tempCurrentTime[0]);
            // ★ 功能：时间跳转时，立即更新片段方块显示状态
            com.director.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
        }
        ImGui.popItemWidth();

        ImGui.sameLine();

        // ========== 吸附控制 ==========
        boolean snapEnabled = model.isSnappingEnabled();
        if (ImGui.checkbox(tr(DirectorLang.Timeline.SNAP), snapEnabled)) {
            model.setSnappingEnabled(!snapEnabled);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Timeline.SNAP_TOOLTIP));
        }
        ImGui.sameLine();
        
        ImGui.alignTextToFramePadding();
        ImGui.text(DirectorI18n.translate(DirectorLang.Timeline.ZOOM_LABEL, viewportState.getPixelsPerSecond()));

        ImGui.sameLine();
        // ========== 适应视图按钮 ==========
        if (ImGui.button(tr(DirectorLang.Timeline.FIT_VIEW), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            callbacks.onFitToViewClicked();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(Icons.FIT_SCREEN + " " + tr(DirectorLang.Timeline.FIT_VIEW_TOOLTIP));
        }

        ImGui.sameLine();

        // ========== 缩放滑块 ==========
        tempPps[0] = viewportState.getPixelsPerSecond();
        
        // 计算剩余空间
        float availWidth = ImGui.getContentRegionAvailX();
        
        // 缩放滑块逻辑：
        // 优先保证缩放滑块显示。如果空间很小，缩放滑块至少保留最小宽度，
        // 如果连最小宽度都没有，ImGui 会自动处理（可能会重叠或换行），我们这里做一个软保护
        float zoomSliderWidth = Math.min(Math.max(ZOOM_SLIDER_MIN_WIDTH, availWidth * ZOOM_SLIDER_RATIO), ZOOM_SLIDER_MAX_WIDTH);
        
        if (availWidth > MIN_AVAILABLE_WIDTH) { 
            ImGui.pushItemWidth(zoomSliderWidth);
            if (ImGui.sliderFloat("##zoom", tempPps, viewportState.getMinPixelsPerSecond(), viewportState.getMaxPixelsPerSecond(), "")) {
                viewportState.setPixelsPerSecond(tempPps[0]);
            }
            ImGui.popItemWidth();
        }
        
        // 更新剩余空间
        ImGui.sameLine();
        renderSeparator();
        ImGui.sameLine();
        
        // ========== 时间定位滑块 (Scrubber) ==========
        // 这是一个很好的功能，但也是最容易破坏布局的组件。
        // 我们只在有足够剩余空间时显示它。
        
        float remainingForScrubber = ImGui.getContentRegionAvailX();
        // 在最右侧预留“播放速度”下拉的空间
        final float speedSectionWidth = 130f; // 文本 + 下拉宽度的经验值
        float remainingForScrubberAfterReserve = Math.max(0f, remainingForScrubber - speedSectionWidth);
        
        // 阈值：如果剩余空间小于最小阈值，就不显示这个长滑块了，避免挤压
        if (remainingForScrubberAfterReserve > SCRUBBER_MIN_REMAINING_WIDTH) {
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(tr(DirectorLang.Timeline.SCRUB_LABEL));
            ImGui.sameLine();
            
            float duration = model.getDuration();
            tempCurrentTime[0] = Math.max(0f, Math.min(duration, model.getCurrentTime()));
            
            // 占满剩余空间，但留一点右边距以防 ImGui 计算误差导致换行
            float scrubberWidth = Math.min(remainingForScrubberAfterReserve - SCRUBBER_RIGHT_MARGIN, SCRUBBER_MAX_WIDTH); 
            
            ImGui.pushItemWidth(scrubberWidth);
            
            String timeFormat = String.format("%.2fs", tempCurrentTime[0]);
            if (ImGui.sliderFloat("##timePosition", tempCurrentTime, 0f, duration, timeFormat)) {
                float newTime = Math.max(0f, Math.min(duration, tempCurrentTime[0]));
                // ⭐ Facade 架构：通过 Facade 统一处理 seek（包括时间更新、季节/天气/相机采样一致化）
                if (commands != null && commands.playback() != null) {
                    commands.playback().seek(newTime);
                } else {
                    // 降级方案：直接调用 model（向后兼容）
                    model.setCurrentTime(newTime);
                }
                
                // ★ 修复后的自动跟随逻辑
                ensurePlayheadVisible();
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.Timeline.SCRUB_TOOLTIP));
            }
            
            ImGui.popItemWidth();
        }

        // ===== 播放速度预设（最右侧）=====
        ImGui.sameLine();
        renderSeparator();
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled(tr(DirectorLang.Timeline.SPEED_LABEL));
        ImGui.sameLine();

        String[] speedLabels = new String[] { "0.5x", "1x", "2x", "3x" };
        float currentSpeed = model.getPlaybackSpeed();
        int speedIdx = (Math.abs(currentSpeed - 0.5f) < 0.001f) ? 0
                : (Math.abs(currentSpeed - 1.0f) < 0.001f) ? 1
                : (Math.abs(currentSpeed - 2.0f) < 0.001f) ? 2
                : (Math.abs(currentSpeed - 3.0f) < 0.001f) ? 3
                : 1; // 默认回到 1x

        imgui.type.ImInt speedBuf = new imgui.type.ImInt(speedIdx);
        ImGui.pushItemWidth(70f);
        if (ImGui.combo("##playSpeedPreset", speedBuf, speedLabels)) {
            float newSpeed = switch (speedBuf.get()) {
                case 0 -> 0.5f;
                case 2 -> 2.0f;
                case 3 -> 3.0f;
                default -> 1.0f;
            };
            // ⭐ Facade 架构：通过 Facade 统一控制播放速度
            if (commands != null && commands.playback() != null) {
                commands.playback().setPlaybackSpeed(newSpeed);
            } else {
                // 降级方案：直接调用 model（向后兼容）
                model.setPlaybackSpeed(newSpeed);
            }
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Timeline.SPEED_TOOLTIP));
        }

        ImGui.popStyleVar(2);
    }
    
    /**
     * 设置命令门面（供依赖注入使用）
     */
    public void setCommands(com.director.ui.commands.UICommandFacade commands) {
        this.commands = commands;
    }

    private boolean canDeleteSelection() {
        // 工具栏删除应支持：片段、关键帧、自动化关键帧等
        if (model == null) return false;
        if (model.getSelectedCount() <= 0) return false;

        for (Object it : model.getSelectedItems()) {
            switch (it) {
                case com.director.timeline.model.TimelineClip ignored -> {
                    return true;
                }
                case com.director.timeline.model.clip.ClipKeyframe ignored -> {
                    return true;
                }
                case com.director.timeline.model.TimelineSelectable selectable -> {
                    // 与 TimelinePanel.deleteSelectedClip 行为保持一致：AudioEvent 默认不走工具栏批量删除
                    if (!(selectable instanceof com.director.timeline.model.audio.AudioEvent)) {
                        return true;
                    }
                }
                case null, default -> {
                }
            }
        }
        return false;
    }
    
    /**
     * 辅助方法：统一的分隔符样式
     * 
     * <p>封装了分隔符的渲染逻辑，使主逻辑更易读。
     */
    private void renderSeparator() {
        ImGui.sameLine();
        UITheme.spacingSmall();
        ImGui.sameLine();
    }
    
    /**
     * 确保播放头始终在可见区域内（修复版）
     * 
     * <p>修复说明：
     * 原代码使用了 getCursorScreenPosX，这是控制栏光标的位置（上方）。
     * 真正的时间线内容区域是基于 WindowPos + LeftLabelWidth 计算的。
     * 
     * <p>当用户在时间定位滑块上拖动时，自动调整视图使播放头可见。
     * 这与播放时的自动跟随行为一致，确保用户始终能看到播放头位置。
     */
    private void ensurePlayheadVisible() {
        if (navigationHandler == null) {
            return; // 如果没有导航处理器，无法执行自动跟随
        }
        
        // 1. 获取窗口在屏幕上的绝对位置
        float winScreenX = ImGui.getWindowPos().x;
        
        // 2. 获取左侧轨道头部的宽度 (这是固定的布局偏移)
        float leftLabelWidth = TimelineRenderer.getLeftLabelWidth();
        
        // 3. 计算时间线内容区域的屏幕 X 起点
        float contentX = winScreenX + leftLabelWidth;
        
        // 4. 计算内容区域的可用宽度
        // 注意：在 ControlBar 渲染结束时调用此方法，getContentRegionAvailX 可能不准确，
        // 因为我们在同一行。更稳健的方法是使用 WindowWidth - LabelWidth。
        float winW = ImGui.getWindowWidth();
        float contentW = winW - leftLabelWidth - SCROLLBAR_WIDTH_ESTIMATE; // 减去滚动条大致宽度
        
        if (contentW <= 0) {
            return;
        }
        
        // 5. 调用导航处理器进行滚动
        // 使用与播放时相同的边距
        navigationHandler.ensurePlayheadVisible(contentX, contentW, PLAYHEAD_AUTO_FOLLOW_MARGIN);
    }
}