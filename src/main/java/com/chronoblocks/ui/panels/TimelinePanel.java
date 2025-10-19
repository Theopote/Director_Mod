package com.chronoblocks.ui.panels;

import com.chronoblocks.core.timeline.TimelineCommandHistory;
import com.chronoblocks.core.timeline.commands.*;
import com.chronoblocks.ui.timeline.*;
import com.chronoblocks.ui.timeline.TimelineInteractionManager.InteractionMode;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImDrawList;
import imgui.type.ImString;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ChronoBlocks 专业级时间轴面板（架构重构版 v6.0）
 * <p>
 * 基于完全解耦的 MVC 架构设计：
 * <ul>
 *   <li>✅ Model: {@link TimelineModel} - 纯数据模型</li>
 *   <li>✅ View: {@link TimelineRenderer} - 纯渲染逻辑</li>
 *   <li>✅ Controller: {@link TimelinePanel} - 交互控制</li>
 *   <li>✅ Command Pattern: 完整的撤销/重做</li>
 *   <li>✅ Interaction Manager: 统一状态管理</li>
 *   <li>✅ Shortcut Manager: 模块化快捷键</li>
 *   <li>✅ Context Menu Builder: 流式API菜单</li>
 * </ul>
 * </p>
 * 
 * @author ChronoBlocks Team
 * @version 6.0
 * @since 1.0
 */
public class TimelinePanel extends AbstractPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePanel.class);
    
    // ========== 核心组件（架构优化）==========
    private final TimelineModel model;
    private final TimelineRenderer renderer;
    private final TimelineCommandHistory commandHistory;
    private final TimelineInteractionManager interactionManager;
    private final ShortcutManager shortcutManager;
    
    // ========== UI 状态 ==========
    private TimelineContextMenu activeContextMenu = null;
    private int dropTargetTrackIndex = -1;
    private TimelineTrack hoveredTrack = null;  // 鼠标悬停的轨道
    
    // ========== 剪贴板 ==========
    private TimelineClip clipboardClip = null;  // 剪贴板中的片段
    private boolean clipboardIsCut = false;      // 是否是剪切操作
    
    // ========== 原位重命名 ==========
    private TimelineTrack editingTrack = null;  // 正在编辑的轨道
    private ImString trackEditBuffer = new ImString(256);  // 编辑输入缓冲区
    
    // ========== 中键平移状态 ==========
    private boolean middleButtonPanning = false;
    private float panStartX = 0f;
    
    // ========== 自动保存 ==========
    private long lastAutoSaveTime = System.currentTimeMillis();
    private static final long AUTO_SAVE_INTERVAL = 5000; // 5秒自动保存
    private boolean dataChanged = false; // 数据是否改变
    
    // ========== 缩放预设 ==========
    private static final float[] ZOOM_PRESETS = {10f, 40f, 80f, 120f, 200f, 400f};
    private static final String[] ZOOM_PRESET_LABELS = {"0.1x", "0.5x", "1x", "1.5x", "2.5x", "5x"};
    
    // ========== 布局常量 ==========
    // 根据轨道数量动态计算高度，最多支持40条轨道
    private static final int MAX_TRACKS = 40;
    private float getCanvasHeight() {
        int trackCount = Math.min(model.getTracks().size(), MAX_TRACKS);
        // 每条轨道高度 + 间隙，加上标尺高度
        return TimelineRenderer.getRulerHeight() + 
               trackCount * (TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap()) + 
               4; // 额外缓冲空间
    }
    
    // ========== Drag&Drop payload type ==========
    public static final String DND_PAYLOAD_ITEM = "CHRONOBLOCKS_ITEM";
    
    public TimelinePanel() {
        super();
        
        // 初始化核心组件
        this.model = new TimelineModel();
        this.renderer = new TimelineRenderer(model);
        this.commandHistory = new TimelineCommandHistory();
        this.interactionManager = new TimelineInteractionManager();
        this.shortcutManager = new ShortcutManager();
        
        // 配置历史管理
        commandHistory.setMaxHistorySize(100);
        
        // 注册快捷键
        registerShortcuts();
    }
    
    /**
     * 注册所有快捷键
     */
    private void registerShortcuts() {
        // 撤销/重做
        shortcutManager.registerCtrl("undo", ImGuiKey.Z,
                commandHistory::undo, "撤销");
        shortcutManager.registerCtrl("redo", ImGuiKey.Y,
                commandHistory::redo, "重做");
        
        // 删除
        shortcutManager.registerKey("delete", ImGuiKey.Delete, 
            this::deleteSelectedClip, "删除选中片段");
        
        // 剪切/复制/粘贴
        shortcutManager.registerCtrl("cut", ImGuiKey.X,
            this::cutSelectedClip, "剪切");
        shortcutManager.registerCtrl("copy", ImGuiKey.C,
            this::copySelectedClip, "复制");
        shortcutManager.registerCtrl("paste", ImGuiKey.V,
            this::pasteClip, "粘贴");
        
        // 取消
        shortcutManager.registerKey("cancel", ImGuiKey.Escape,
                interactionManager::cancelInteraction, "取消交互");
        
        // 播放控制
        shortcutManager.registerKey("togglePlay", ImGuiKey.Space,
                model::togglePlay, "播放/暂停");
        shortcutManager.registerKey("jumpStart", ImGuiKey.Home,
                model::jumpToStart, "跳到开始");
        shortcutManager.registerKey("jumpEnd", ImGuiKey.End,
                model::jumpToEnd, "跳到结束");
        
        LOGGER.debug("[Timeline] 注册了 {} 个快捷键", shortcutManager.getShortcuts().size());
    }
    
    @Override
    public String getPanelName() {
        return "Timeline";
    }
    
    @Override
    public String getWindowName() {
        return "Timeline###TimelinePanel";
    }
    
    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.NoCollapse;
    }
    
    @Override
    protected void doRender() {
        // 1. 处理快捷键
        shortcutManager.handleInput(ImGui.getIO());
        
        // 2. 播放逻辑现在由 ChronoBlocksUIManager 统一驱动
        // （已废弃 model.updatePlayback()，由 PlaybackController.update() 替代）
        
        // 3. 渲染控制栏（使用统一的元素间距4px）
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.ELEMENT_SPACING, UITheme.Spacing.ELEMENT_SPACING);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);
        renderControlBar();
        ImGui.popStyleVar(2);
        
        UITheme.separator();
        
        // 4. 渲染时间线画布
        renderTimelineCanvas();
        
        // 5. 渲染新建轨道菜单
        renderAddTrackMenu();
        
        // 6. 渲染轨道上下文菜单
        renderTrackContextMenu();
        
        // 7. 渲染片段上下文菜单
        if (activeContextMenu != null) {
            activeContextMenu.render();
        }
        
        // 8. 自动保存（每5秒保存一次）
        autoSaveIfNeeded();
    }
    
    /**
     * 自动保存时间线数据
     */
    private void autoSaveIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (dataChanged && (currentTime - lastAutoSaveTime) >= AUTO_SAVE_INTERVAL) {
            model.save();
            dataChanged = false;
            lastAutoSaveTime = currentTime;
            LOGGER.debug("[Timeline] 自动保存时间线数据");
        }
    }
    
    /**
     * 标记数据已改变
     */
    private void markDataChanged() {
        dataChanged = true;
    }
    
    /**
     * 渲染控制栏
     */
    private void renderControlBar() {
        // ========== 轨道管理按钮 ==========
        if (ImGui.button(Icons.ADD + " 新建轨道", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            openAddTrackMenu();
        }
        
        ImGui.sameLine();
        
        boolean hasSelection = model.getSelectedClip() != null;
        if (!hasSelection) ImGui.beginDisabled();
        if (ImGui.button(Icons.DELETE + " 删除", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            deleteSelectedClip();
        }
        if (!hasSelection) ImGui.endDisabled();
        
        ImGui.sameLine();
        UITheme.spacingSmall();
        ImGui.sameLine();
        
        // ========== 撤销/重做按钮（自动宽度）==========
        boolean canUndo = commandHistory.canUndo();
        boolean canRedo = commandHistory.canRedo();
        
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(Icons.UNDO + " 撤销", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.undo();
        }
        if (!canUndo) ImGui.endDisabled();
        
        ImGui.sameLine();
        
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(Icons.REDO + " 重做", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.redo();
        }
        if (!canRedo) ImGui.endDisabled();
        
        ImGui.sameLine();
        UITheme.label(String.format("(%d/%d)", 
            commandHistory.getUndoSize(), 
            commandHistory.getRedoSize()), UITheme.DarkTheme.TEXT_DISABLED);
        
        ImGui.sameLine();
        UITheme.spacingSmall();
        ImGui.sameLine();
        
        // ========== 播放控制（自动宽度）==========
        if (ImGui.button(Icons.SKIP_BACK, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            model.jumpToStart();
        }
        ImGui.sameLine();
        if (ImGui.button(model.isPlaying() ? (Icons.PAUSE + " 暂停") : (Icons.PLAY + " 播放"), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            model.togglePlay();
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.SKIP_FORWARD, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            model.jumpToEnd();
        }
        ImGui.sameLine();
        ImGui.text("时间:");
        
        // ========== 当前时间显示 ==========
        ImGui.sameLine();
        ImGui.text(String.format("%.2f", model.getCurrentTime()));
        
        ImGui.sameLine();
        ImGui.text("/");
        
        // ========== 总时长输入（可编辑，带明显提示）==========
        ImGui.sameLine();
        float[] durationRef = {model.getDuration()};
        ImGui.pushItemWidth(100);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.15f, 0.25f, 0.45f, 0.8f); // 深蓝色背景提示可编辑
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.2f, 0.35f, 0.6f, 0.9f); // 悬停高亮
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0.25f, 0.4f, 0.7f, 1.0f); // 激活高亮
        if (ImGui.dragFloat("##duration", durationRef, 0.5f, 1.0f, 3600.0f, "%.1fs")) {
            float newDuration = Math.max(1.0f, Math.min(3600.0f, durationRef[0]));
            model.setDuration(newDuration);
            LOGGER.info("[Timeline] 手动设置总时长: {:.2f}s", newDuration);
        }
        ImGui.popStyleColor(3);
        ImGui.popItemWidth();
        
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(Icons.EDIT + " 可编辑总时长");
            ImGui.separator();
            ImGui.text("• 左键拖动调整");
            ImGui.text("• Ctrl+点击输入数字");
            ImGui.text("• 范围: 1.0s - 3600.0s");
            ImGui.endTooltip();
        }
        
        // ========== 精确时间输入 ==========
        ImGui.sameLine();
        UITheme.spacingSmall();
        ImGui.sameLine();
        ImGui.text("跳转:");
        ImGui.sameLine();
        float[] timeRef = {model.getCurrentTime()};
        ImGui.pushItemWidth(70);
        if (ImGui.dragFloat("##currentTime", timeRef, 0.01f, 0, model.getDuration(), "%.2fs")) {
            model.setCurrentTime(timeRef[0]);
        }
        ImGui.popItemWidth();
        
        ImGui.sameLine();
        
        // ========== 吸附控制 ==========
        boolean snapEnabled = model.isSnapEnabled();
        if (ImGui.checkbox("吸附 (Ctrl切换)", snapEnabled)) {
            model.setSnapEnabled(!snapEnabled);
        }
        
        ImGui.sameLine();
        ImGui.text(String.format("缩放: %.0f px/s", model.getPixelsPerSecond()));
        
        // ========== 缩放预设按钮 ==========
        ImGui.sameLine();
        for (int i = 0; i < ZOOM_PRESETS.length; i++) {
            if (ImGui.button(ZOOM_PRESET_LABELS[i])) {
                model.setZoom(ZOOM_PRESETS[i]);
            }
            if (i < ZOOM_PRESETS.length - 1) {
                ImGui.sameLine();
            }
        }
        
        ImGui.sameLine();
        
        // ========== 适应视图按钮 ==========
        if (ImGui.button("适应")) {
            fitTimelineToView();
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(Icons.FIT_SCREEN + " 自动缩放以查看整个时间线");
            ImGui.endTooltip();
        }
        
        ImGui.sameLine();
        
        // ========== 缩放滑块 ==========
        float[] ppsRef = {model.getPixelsPerSecond()};
        if (ImGui.sliderFloat("##zoom", ppsRef, model.getMinPps(), model.getMaxPps(), "%.0f")) {
            model.setZoom(ppsRef[0]);
        }
    }
    
    /**
     * 渲染时间线画布
     */
    private void renderTimelineCanvas() {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float winX = ImGui.getCursorScreenPosX();
        float winY = ImGui.getCursorScreenPosY();
        float winW = ImGui.getContentRegionAvailX();
        
        float canvasHeight = getCanvasHeight();
        
        float contentX = winX + TimelineRenderer.getLeftLabelWidth();
        float contentW = winW - TimelineRenderer.getLeftLabelWidth();
        float contentY = winY + TimelineRenderer.getRulerHeight();
        float contentH = canvasHeight - TimelineRenderer.getRulerHeight();
        
        // 使用渲染器绘制
        renderer.renderCanvas(drawList, winX, winY, winW, canvasHeight);
        
        // 渲染原位编辑输入框
        renderInlineEdit(winX, contentY);
        
        // 处理鼠标输入
        handleMouseInput(contentX, contentY, contentW, contentH);
        
        // 渲染拖放高亮
        if (dropTargetTrackIndex >= 0) {
            float y = contentY + dropTargetTrackIndex * (TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap());
            renderer.renderDropHighlight(drawList, dropTargetTrackIndex, contentX, y, contentW);
        }
        
        // 不可见按钮接收鼠标事件（必须在交互处理之前）
        ImGui.setCursorScreenPos(winX, winY);
        ImGui.invisibleButton("##TimelineCanvas", winW, canvasHeight);
        
        // 处理片段交互
        handleClipInteractions(contentX, contentY, contentW, contentH);
        
        // 处理播放头交互
        handlePlayheadInteraction(contentX, contentY, contentW, contentH);
        
        // 接收拖放
        handleDragDrop(contentX);
        
        ImGui.setCursorScreenPos(winX, winY + canvasHeight);
    }
    
    /**
     * 处理鼠标输入（缩放、平移）
     */
    private void handleMouseInput(float contentX, float contentY, float contentW, float contentH) {
        ImGuiIO io = ImGui.getIO();
        float mx = ImGui.getMousePosX();
        
        // 中键拖拽平移
        boolean middleDown = ImGui.isMouseDown(ImGuiMouseButton.Middle);
        if (middleDown && !middleButtonPanning && ImGui.isMouseClicked(ImGuiMouseButton.Middle)) {
            middleButtonPanning = true;
            panStartX = mx;
        }
        if (middleButtonPanning && middleDown) {
            float dx = mx - panStartX;
            model.scroll(-dx);
            panStartX = mx;
            model.clampScroll(contentW);
        }
        if (middleButtonPanning && ImGui.isMouseReleased(ImGuiMouseButton.Middle)) {
            middleButtonPanning = false;
        }
        
        // 鼠标滚轮缩放
        boolean hoverContent = isMouseHoveringRect(contentX, contentY, contentX + contentW, contentY + contentH);
        if (hoverContent) {
            float wheel = io.getMouseWheel();
            if (wheel != 0f) {
                float oldPps = model.getPixelsPerSecond();
                // Ctrl+滚轮：精细缩放（50%速度）
                boolean ctrlPressed = io.getKeyCtrl();
                float baseFactor = (float)Math.pow(1.1, wheel);
                float factor = ctrlPressed ? (float)Math.pow(baseFactor, 0.5) : baseFactor;
                model.zoom(factor);
                model.adjustScrollAfterZoom(mx, contentX, contentW, oldPps);
            }
            
            // 水平滚轮滚动
            float wheelH = io.getMouseWheelH();
            if (wheelH != 0f) {
                model.scroll(-wheelH * 40f);
                model.clampScroll(contentW);
            }
        }
    }
    
    /**
     * 处理片段交互
     */
    private void handleClipInteractions(float contentX, float contentY, float contentW, float contentH) {
        float y = contentY;
        float winX = contentX - TimelineRenderer.getLeftLabelWidth();
        
        for (TimelineTrack track : model.getTracks()) {
            // 跳过不可见轨道
            if (y + TimelineRenderer.getTrackHeight() < contentY || y > contentY + contentH) {
                y += TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
                continue;
            }
            
            // 检测轨道标签区域的右键点击
            boolean hoverTrackLabel = isMouseHoveringRect(
                winX, y, 
                contentX, y + TimelineRenderer.getTrackHeight()
            );
            
            if (hoverTrackLabel) {
                hoveredTrack = track;
                if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                    LOGGER.debug("[Timeline] 右键点击轨道: {}", track.getName());
                    openTrackContextMenu(track);
                    return; // 阻止事件穿透，防止下方轨道也触发
                }
            }
            
            for (TimelineClip clip : track.getClips()) {
                handleClipInteraction(clip, track, contentX, y);
            }
            
            y += TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        }
    }
    
    /**
     * 处理单个 Clip 的交互
     */
    private void handleClipInteraction(TimelineClip clip, TimelineTrack track, float contentX, float y) {
        float x1 = model.timeToScreen(clip.getStartTime(), contentX);
        float x2 = model.timeToScreen(clip.getEndTime(), contentX);
        float mx = ImGui.getMousePosX();
        
        boolean hovered = isMouseHoveringRect(x1, y, x2, y + TimelineRenderer.getTrackHeight());
        
        // 右键菜单
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            model.setSelectedClip(clip);
            openClipContextMenu(clip);
            return;
        }
        
        // 绘制调整把手
        renderer.renderResizeHandles(ImGui.getWindowDrawList(), clip, contentX, y, TimelineRenderer.getTrackHeight());
        
        // 检测边缘悬浮
        boolean hoverLeft = Math.abs(mx - x1) < TimelineRenderer.getResizeHandleWidth() && hovered;
        boolean hoverRight = Math.abs(mx - x2) < TimelineRenderer.getResizeHandleWidth() && hovered;
        
        // 开始交互
        if (!interactionManager.isInteracting() && hovered && 
            ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            
            model.setSelectedClip(clip);
            
            InteractionMode mode;
            if (hoverLeft) {
                mode = InteractionMode.RESIZE_CLIP_LEFT;
            } else if (hoverRight) {
                mode = InteractionMode.RESIZE_CLIP_RIGHT;
            } else {
                mode = InteractionMode.DRAG_CLIP;
            }
            
            interactionManager.startInteraction(
                mode, clip, mx, 0,
                clip.getStartTime(), clip.getEndTime()
            );
        }
        
        // 执行交互
        if (interactionManager.getActiveObject() == clip && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            handleActiveClipInteraction(clip);
        }
        
        // 结束交互
        if (interactionManager.getActiveObject() == clip && ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            endClipInteraction(clip);
        }
        
        // 双击添加/删除关键帧
        if (hovered && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            handleKeyframeDoubleClick(clip, mx, contentX);
        }
        
        // 处理关键帧交互
        handleKeyframeInteractions(clip, contentX, y);
    }
    
    /**
     * 处理活动片段的交互（拖动/调整）
     */
    private void handleActiveClipInteraction(TimelineClip clip) {
        float mx = ImGui.getMousePosX();
        float deltaX = interactionManager.calculateDeltaX(mx);
        float deltaTime = deltaX / model.getPixelsPerSecond();
        
        boolean effectiveSnap = model.isSnapEnabled() ^ ImGui.getIO().getKeyCtrl();
        
        switch (interactionManager.getCurrentMode()) {
            case DRAG_CLIP:
                // 检查是否是组拖拽（多选模式）
                List<Object> selectedItems = model.getSelectedItems();
                boolean isGroupDrag = selectedItems.size() > 1 && model.isSelected(clip);

                float newStart = interactionManager.getDragStartValue() + deltaTime;
                if (effectiveSnap) newStart = model.snapToInterval(newStart);
                if (isGroupDrag) {
                    // 组拖拽：同时移动所有选中的片段
                    float actualDelta = newStart - interactionManager.getDragStartValue();
                    
                    // 对所有选中的片段应用相同的偏移
                    for (Object item : selectedItems) {
                        if (item instanceof TimelineClip) {
                            TimelineClip selectedClip = (TimelineClip) item;
                            float clipNewStart = selectedClip.getStartTime() + actualDelta;
                            clipNewStart = clamp(clipNewStart, 0f, model.getDuration() - selectedClip.getLength());
                            selectedClip.setStartTime(clipNewStart);
                            selectedClip.setEndTime(clipNewStart + selectedClip.getLength());
                        }
                    }
                } else {
                    // 单个片段拖拽
                    newStart = clamp(newStart, 0f, model.getDuration() - clip.getLength());
                    clip.setEndTime(newStart + clip.getLength());
                    clip.setStartTime(newStart);
                }
                break;
                
            case RESIZE_CLIP_LEFT:
                float newLeft = interactionManager.getDragStartValue() + deltaTime;
                if (effectiveSnap) newLeft = model.snapToInterval(newLeft);
                clip.setStartTime(clamp(newLeft, 0f, clip.getEndTime() - 0.1f));
                break;
                
            case RESIZE_CLIP_RIGHT:
                float newRight = interactionManager.getDragStartValue2() + deltaTime;
                if (effectiveSnap) newRight = model.snapToInterval(newRight);
                clip.setEndTime(clamp(newRight, clip.getStartTime() + 0.1f, model.getDuration()));
                break;
                
            default:
                break;
        }
    }
    
    /**
     * 结束片段交互并创建命令
     */
    private void endClipInteraction(TimelineClip clip) {
        InteractionMode mode = interactionManager.getCurrentMode();
        
        if (mode == InteractionMode.DRAG_CLIP || 
            mode == InteractionMode.RESIZE_CLIP_LEFT || 
            mode == InteractionMode.RESIZE_CLIP_RIGHT) {
            
            // 检查是否需要自动扩展时间长度
            autoExtendDuration(clip.getEndTime());
            
            // 创建移动命令
            MoveClipCommand cmd = new MoveClipCommand(
                new MoveClipCommand.Clip() {
                    @Override
                    public float getStartTime() { return clip.getStartTime(); }
                    @Override
                    public void setStartTime(float time) { clip.setStartTime(time); }
                    @Override
                    public float getEndTime() { return clip.getEndTime(); }
                    @Override
                    public void setEndTime(float time) { clip.setEndTime(time); }
                    @Override
                    public float getLength() { return clip.getLength(); }
                },
                clip.getStartTime(),
                clip.getEndTime()
            );
            
            commandHistory.executeCommand(cmd);
            markDataChanged(); // 标记数据已改变
            LOGGER.info("[Timeline] 完成 {} 操作（命令）", mode);
        }
        
        interactionManager.endInteraction();
    }
    
    /**
     * 自动扩展时间长度（如果片段超出当前时长）
     */
    private void autoExtendDuration(float requiredTime) {
        float currentDuration = model.getDuration();
        if (requiredTime > currentDuration) {
            // 扩展到片段结束时间，再加1秒缓冲
            float newDuration = (float)Math.ceil(requiredTime + 1.0f);
            model.setDuration(newDuration);
            LOGGER.info("[Timeline] 自动扩展时长: {:.2f}s -> {:.2f}s", currentDuration, newDuration);
        }
    }
    
    /**
     * 处理关键帧交互
     */
    private void handleKeyframeInteractions(TimelineClip clip, float contentX, float y) {
        float mx = ImGui.getMousePosX();
        
        for (Float kf : new ArrayList<>(clip.getKeyframes())) {
            float kx = model.timeToScreen(kf, contentX);
            boolean hoverKeyframe = Math.abs(mx - kx) < 5 && 
                                   isMouseHoveringRect(kx - 5, y, kx + 5, y + TimelineRenderer.getTrackHeight());
            
            // 右键菜单
            if (hoverKeyframe && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                openKeyframeContextMenu(clip, kf);
                return;
            }
            
            // 拖动关键帧
            if (!interactionManager.isInteracting() && hoverKeyframe && 
                ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                interactionManager.startInteraction(
                    InteractionMode.DRAG_KEYFRAME,
                    kf, mx, 0, kf, 0f
                );
            }
        }
        
        // 处理关键帧拖动
        if (interactionManager.isInteracting(InteractionMode.DRAG_KEYFRAME) && 
            ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            
            Object activeObj = interactionManager.getActiveObject();
            if (activeObj instanceof Float) {
                float oldTime = (Float) activeObj;
                float deltaX = interactionManager.calculateDeltaX(mx);
                float deltaTime = deltaX / model.getPixelsPerSecond();
                float newTime = interactionManager.getDragStartValue() + deltaTime;
                
                boolean effectiveSnap = model.isSnapEnabled() ^ ImGui.getIO().getKeyCtrl();
                if (effectiveSnap) newTime = model.snapToInterval(newTime);
                
                newTime = clamp(newTime, clip.getStartTime(), clip.getEndTime());
                
                int index = clip.getKeyframes().indexOf(oldTime);
                if (index >= 0) {
                    clip.getKeyframes().set(index, newTime);
                }
            }
        }
        
        if (interactionManager.isInteracting(InteractionMode.DRAG_KEYFRAME) && 
            ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            Collections.sort(clip.getKeyframes());
            interactionManager.endInteraction();
            LOGGER.debug("[Timeline] 关键帧拖动完成");
        }
    }
    
    /**
     * 处理播放头交互（优化版：更大的拖动区域，更流畅）
     */
    private void handlePlayheadInteraction(float contentX, float contentY, float contentW, float contentH) {
        float px = model.timeToScreen(model.getCurrentTime(), contentX);
        
        // 扩大拖动区域：整个标尺高度 + 垂直线区域
        float rulerTop = contentY - TimelineRenderer.getRulerHeight();
        boolean hoveredHandle = isMouseHoveringRect(px - 12, rulerTop, px + 12, contentY + contentH);
        
        // 如果正在拖动其他元素，不处理播放头
        if (interactionManager.isInteracting() && 
            interactionManager.getCurrentMode() != InteractionMode.DRAG_PLAYHEAD) {
            return;
        }
        
        // 开始拖动播放头
        if (!interactionManager.isInteracting() && hoveredHandle && 
            ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            interactionManager.startInteraction(
                InteractionMode.DRAG_PLAYHEAD,
                null, ImGui.getMousePosX(), 0,
                model.getCurrentTime(), 0f
            );
            LOGGER.debug("[Timeline] 开始拖动播放头");
        }
        
        // 拖动中
        if (interactionManager.isInteracting(InteractionMode.DRAG_PLAYHEAD) && 
            ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            
            float deltaX = interactionManager.calculateDeltaX(ImGui.getMousePosX());
            float deltaTime = deltaX / model.getPixelsPerSecond();
            float newTime = interactionManager.getDragStartValue() + deltaTime;
            
            // 吸附支持（Ctrl 切换）
            boolean effectiveSnap = model.isSnapEnabled() ^ ImGui.getIO().getKeyCtrl();
            if (effectiveSnap) newTime = model.snapToInterval(newTime);
            
            // 限制范围
            newTime = clamp(newTime, 0f, model.getDuration());
            model.setCurrentTime(newTime);
        }
        
        // 结束拖动
        if (interactionManager.isInteracting(InteractionMode.DRAG_PLAYHEAD) && 
            ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            interactionManager.endInteraction();
            LOGGER.debug("[Timeline] 播放头移动到: {:.2f}s", model.getCurrentTime());
        }
        
        // 点击标尺跳转（只在未拖动时）
        if (!interactionManager.isInteracting() && 
            ImGui.isMouseClicked(ImGuiMouseButton.Left) && 
            isMouseHoveringRect(contentX, rulerTop, contentX + contentW, contentY)) {
            float t = model.screenToTime(ImGui.getMousePosX(), contentX);
            t = clamp(t, 0f, model.getDuration());
            model.setCurrentTime(t);
            LOGGER.debug("[Timeline] 点击标尺跳转到: {:.2f}s", t);
        }
    }
    
    /**
     * 处理拖放
     */
    private void handleDragDrop(float contentX) {
        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(DND_PAYLOAD_ITEM);
            if (payload != null) {
                String name = "物品";
                try {
                    if (payload instanceof byte[]) {
                        name = new String((byte[]) payload);
                    } else if (payload instanceof String) {
                        name = (String) payload;
                    }
                } catch (Exception e) {
                    LOGGER.warn("[Timeline] 解析拖放数据失败: {}", e.getMessage());
                }
                
                float mouseX = ImGui.getMousePosX();
                float t = model.screenToTime(mouseX, contentX);
                t = clamp(t, 0f, model.getDuration());
                
                TimelineClip newClip = new TimelineClip(name, t, 
                    Math.min(model.getDuration(), t + 1.0f), rgba(160,200,255,255));
                
                int targetIdx = Math.max(dropTargetTrackIndex, 0);
                TimelineTrack targetTrack = model.getTrackAt(targetIdx);
                if (targetTrack != null) {
                    targetTrack.addClip(newClip);
                    LOGGER.info("[Timeline] 拖放物品 '{}' -> 轨道 {} 在 {:.2f}s", 
                        name, targetIdx, newClip.getStartTime());
                }
            }
            ImGui.endDragDropTarget();
        }
    }
    
    /**
     * 双击处理关键帧添加/删除
     */
    private void handleKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        final float clickTime = clamp(model.screenToTime(mouseX, contentX), 
            clip.getStartTime(), clip.getEndTime());
        
        Float existingKf = clip.getKeyframeAt(clickTime, 0.1f);
        
        if (existingKf != null) {
            RemoveKeyframeCommand cmd = new RemoveKeyframeCommand(clip.getKeyframes(), existingKf);
            commandHistory.executeCommand(cmd);
            markDataChanged(); // 标记数据已改变
            LOGGER.debug("[Timeline] 删除关键帧 at {:.2f}（命令）", existingKf);
        } else {
            AddKeyframeCommand cmd = new AddKeyframeCommand(clip.getKeyframes(), clickTime);
            commandHistory.executeCommand(cmd);
            markDataChanged(); // 标记数据已改变
            LOGGER.debug("[Timeline] 添加关键帧 at {:.2f}（命令）", clickTime);
        }
    }
    
    /**
     * 打开新建轨道菜单
     */
    private void openAddTrackMenu() {
        ImGui.openPopup("新建轨道##AddTrackMenu");
    }
    
    /**
     * 渲染新建轨道弹出菜单
     */
    private void renderAddTrackMenu() {
        if (ImGui.beginPopup("新建轨道##AddTrackMenu")) {
            ImGui.text("选择轨道类型：");
            ImGui.separator();
            
            for (TrackType type : TrackType.values()) {
                if (ImGui.menuItem(type.getIconAndName())) {
                    // 计算同类型轨道的数量
                    int sameTypeCount = 0;
                    for (TimelineTrack track : model.getTracks()) {
                        if (track.getType() == type) {
                            sameTypeCount++;
                        }
                    }
                    
                    // 创建新轨道（带编号）
                    String trackName = type.getDisplayName() + " " + (sameTypeCount + 1);
                    TimelineTrack newTrack = new TimelineTrack(trackName, type);
                    model.addTrack(newTrack);
                    markDataChanged(); // 标记数据已改变，触发自动保存
                    LOGGER.info("[Timeline] 创建新轨道: {} (类型: {}, 同类型第{}条)", 
                               trackName, type.getDisplayName(), sameTypeCount + 1);
                }
                
                // 显示轨道类型说明
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text(type.getDescription());
                    ImGui.endTooltip();
                }
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 打开轨道上下文菜单
     */
    private void openTrackContextMenu(TimelineTrack track) {
        ImGui.openPopup("轨道菜单##TrackContextMenu");
        hoveredTrack = track;
    }
    
    /**
     * 渲染轨道上下文菜单
     */
    private void renderTrackContextMenu() {
        if (ImGui.beginPopup("轨道菜单##TrackContextMenu")) {
            if (hoveredTrack != null) {
                // 显示轨道信息
                ImGui.text(hoveredTrack.getType().getIconAndName());
                ImGui.text("\"" + hoveredTrack.getName() + "\"");
                ImGui.separator();
                
                // 重命名
                if (ImGui.menuItem(Icons.EDIT + " 重命名", "F2")) {
                    startEditingTrack(hoveredTrack);
                }
                
                // 复制
                if (ImGui.menuItem(Icons.COPY + " 复制轨道")) {
                    duplicateTrack(hoveredTrack);
                }
                
                ImGui.separator();
                
                // 上移轨道
                int currentIndex = model.getTracks().indexOf(hoveredTrack);
                boolean canMoveUp = currentIndex > 0;
                if (ImGui.menuItem(Icons.ARROW_UP + " 上移轨道", null, false, canMoveUp)) {
                    moveTrackUp(hoveredTrack);
                }
                
                // 下移轨道
                boolean canMoveDown = currentIndex < model.getTracks().size() - 1 && currentIndex >= 0;
                if (ImGui.menuItem(Icons.ARROW_DOWN + " 下移轨道", null, false, canMoveDown)) {
                    moveTrackDown(hoveredTrack);
                }
                
                ImGui.separator();
                
                // 锁定/解锁
                String lockText = hoveredTrack.isLocked() ? 
                    (Icons.UNLOCK + " 解锁") : (Icons.LOCK + " 锁定");
                if (ImGui.menuItem(lockText)) {
                    hoveredTrack.setLocked(!hoveredTrack.isLocked());
                    LOGGER.info("[Timeline] {} 轨道: {}", 
                               hoveredTrack.isLocked() ? "锁定" : "解锁", 
                               hoveredTrack.getName());
                }
                
                // 显示/隐藏
                String visText = hoveredTrack.isVisible() ? 
                    (Icons.INVISIBLE + " 隐藏") : (Icons.VISIBLE + " 显示");
                if (ImGui.menuItem(visText)) {
                    hoveredTrack.setVisible(!hoveredTrack.isVisible());
                    LOGGER.info("[Timeline] {} 轨道: {}", 
                               hoveredTrack.isVisible() ? "显示" : "隐藏", 
                               hoveredTrack.getName());
                }
                
                ImGui.separator();
                
                // 删除（红色高亮）
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f);
                if (ImGui.menuItem(Icons.DELETE + " 删除轨道")) {
                    deleteTrack(hoveredTrack);
                }
                ImGui.popStyleColor();
            }
            
            ImGui.endPopup();
        }
    }
    
    /**
     * 删除轨道（支持撤销）
     */
    private void deleteTrack(TimelineTrack track) {
        if (track == null) return;
        
        // 确认删除（如果轨道有片段）
        if (!track.getClips().isEmpty()) {
            LOGGER.warn("[Timeline] 轨道 '{}' 包含 {} 个片段，确认删除", 
                       track.getName(), track.getClips().size());
        }
        
        // 使用命令模式支持撤销
        @SuppressWarnings("unchecked")
        List<Object> trackListAsObjects = (List<Object>)(List<?>) model.getTracks();
        DeleteTrackCommand cmd = new DeleteTrackCommand(track, trackListAsObjects);
        commandHistory.executeCommand(cmd);
        
        hoveredTrack = null;
        markDataChanged(); // 标记数据已改变，触发自动保存
        
        LOGGER.info("[Timeline] 已删除轨道: {} (类型: {})", 
                   track.getName(), track.getType().getDisplayName());
    }
    
    /**
     * 开始原位编辑轨道名称
     */
    private void startEditingTrack(TimelineTrack track) {
        editingTrack = track;
        // 先清空缓冲区，避免遗留字符
        trackEditBuffer.set("");
        // 再设置新值
        trackEditBuffer.set(track.getName());
        LOGGER.debug("[Timeline] 开始编辑轨道: '{}'", track.getName());
    }
    
    /**
     * 完成原位编辑
     */
    private void finishEditingTrack(boolean apply) {
        if (editingTrack == null) return;
        
        if (apply) {
            // 获取字符串并清理
            String newName = cleanTrackName(trackEditBuffer.get());
            
            if (!newName.isEmpty()) {
                String oldName = editingTrack.getName();
                editingTrack.setName(newName);
                markDataChanged();
                LOGGER.info("[Timeline] 轨道重命名: '{}' -> '{}'", oldName, newName);
            } else {
                LOGGER.warn("[Timeline] 重命名失败：名称为空");
            }
        }
        
        editingTrack = null;
        LOGGER.debug("[Timeline] 完成编辑轨道");
    }
    
    /**
     * 清理轨道名称（去除空字符、不可见字符等）
     */
    private String cleanTrackName(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        // 在第一个空字符处截断（ImString 缓冲区可能包含空字符）
        int nullIndex = input.indexOf('\0');
        if (nullIndex >= 0) {
            input = input.substring(0, nullIndex);
        }
        
        // 去除所有不可打印字符（ASCII < 32 或 >= 127）
        StringBuilder cleaned = new StringBuilder();
        for (char c : input.toCharArray()) {
            // 保留可打印字符（包括中文等 Unicode 字符）
            if (c >= 32 && c != 127) {
                cleaned.append(c);
            }
        }
        
        // 去除首尾空格
        return cleaned.toString().trim();
    }
    
    /**
     * 渲染原位编辑输入框
     */
    private void renderInlineEdit(float winX, float contentY) {
        if (editingTrack == null) return;
        
        // 找到正在编辑的轨道索引
        int trackIndex = model.getTracks().indexOf(editingTrack);
        if (trackIndex < 0) {
            editingTrack = null;
            return;
        }
        
        // 计算轨道名称位置
        float trackY = contentY + trackIndex * (TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap());
        float nameX = winX + 28;  // 图标后面
        float nameY = trackY + 6;
        float inputWidth = TimelineRenderer.getLeftLabelWidth() - 35;  // 留出空间给图标和锁定按钮
        
        // 设置输入框位置
        ImGui.setCursorScreenPos(nameX, nameY);
        
        // 设置输入框宽度
        ImGui.pushItemWidth(inputWidth);
        
        // 自动聚焦（第一帧）
        ImGui.setKeyboardFocusHere();
        
        // 输入框（支持 Enter 确认）
        boolean enterPressed = ImGui.inputText("##TrackNameEdit", trackEditBuffer, 
            ImGuiInputTextFlags.EnterReturnsTrue);
        
        ImGui.popItemWidth();
        
        // Enter 键确认
        if (enterPressed) {
            finishEditingTrack(true);
        }
        
        // ESC 键取消
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            finishEditingTrack(false);
        }
        
        // 点击其他地方完成编辑
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && !ImGui.isItemHovered()) {
            finishEditingTrack(true);
        }
    }
    
    /**
     * 复制轨道
     */
    private void duplicateTrack(TimelineTrack track) {
        if (track == null) return;
        
        // 计算同类型轨道数量
        int sameTypeCount = 0;
        for (TimelineTrack t : model.getTracks()) {
            if (t.getType() == track.getType()) {
                sameTypeCount++;
            }
        }
        
        // 创建副本
        String newName = track.getType().getDisplayName() + " " + (sameTypeCount + 1);
        TimelineTrack newTrack = new TimelineTrack(newName, track.getType());
        
        // 复制所有片段（偏移0.5秒）
        for (TimelineClip clip : track.getClips()) {
            TimelineClip newClip = new TimelineClip(
                clip.getLabel() + " (副本)",
                clip.getStartTime() + 0.5f,
                clip.getEndTime() + 0.5f,
                clip.getColor()
            );
            
            // 复制关键帧
            for (Float kf : clip.getKeyframes()) {
                newClip.addKeyframe(kf + 0.5f);
            }
            
            newTrack.addClip(newClip);
        }
        
        model.addTrack(newTrack);
        markDataChanged(); // 标记数据已改变，触发自动保存
        LOGGER.info("[Timeline] 已复制轨道: {} -> {}", track.getName(), newName);
    }
    
    /**
     * 上移轨道（交换索引）
     */
    private void moveTrackUp(TimelineTrack track) {
        if (track == null) return;
        
        List<TimelineTrack> tracks = model.getTracks();
        int index = tracks.indexOf(track);
        
        if (index > 0) {
            // 交换位置
            Collections.swap(tracks, index, index - 1);
            markDataChanged();
            LOGGER.info("[Timeline] 上移轨道: {} (索引 {} -> {})", track.getName(), index, index - 1);
        }
    }
    
    /**
     * 下移轨道（交换索引）
     */
    private void moveTrackDown(TimelineTrack track) {
        if (track == null) return;
        
        List<TimelineTrack> tracks = model.getTracks();
        int index = tracks.indexOf(track);
        
        if (index >= 0 && index < tracks.size() - 1) {
            // 交换位置
            Collections.swap(tracks, index, index + 1);
            markDataChanged();
            LOGGER.info("[Timeline] 下移轨道: {} (索引 {} -> {})", track.getName(), index, index + 1);
        }
    }
    
    /**
     * 打开片段上下文菜单（支持剪切/粘贴）
     */
    private void openClipContextMenu(TimelineClip clip) {
        TimelineTrack track = model.findTrackForClip(clip);
        
        activeContextMenu = TimelineContextMenuBuilder.forClip(commandHistory)
            .item("剪切", () -> cutClip(clip, track), "Ctrl+X")
            .item("复制", () -> copyClip(clip), "Ctrl+C")
            .item("粘贴", () -> pasteClipToTrack(track), "Ctrl+V")
            .separator()
            .item("复制片段", this::duplicateSelectedClip, "Ctrl+D")
            .separator()
            .item("删除", this::deleteSelectedClip, "Del")
            .separator()
            .item("设置时长为 1s", () -> {
                clip.setLength(1.0f);
                markDataChanged();
            })
            .item("设置时长为 2s", () -> {
                clip.setLength(2.0f);
                markDataChanged();
            })
            .item("设置时长为 5s", () -> {
                clip.setLength(5.0f);
                markDataChanged();
            })
            .build();
    }
    
    /**
     * 剪切片段
     */
    private void cutClip(TimelineClip clip, TimelineTrack track) {
        if (clip == null || track == null) return;
        
        // 复制到剪贴板
        clipboardClip = clip.clone();
        clipboardIsCut = true;
        
        // 删除原片段（使用命令支持撤销）
        @SuppressWarnings("unchecked")
        List<Object> clipListAsObjects = (List<Object>)(List<?>) track.getClips();
        DeleteClipCommand cmd = new DeleteClipCommand(clip, clipListAsObjects);
        commandHistory.executeCommand(cmd);
        
        model.setSelectedClip(null);
        markDataChanged();
        LOGGER.info("[Timeline] 剪切片段: {}", clip.getLabel());
    }
    
    /**
     * 复制片段
     */
    private void copyClip(TimelineClip clip) {
        if (clip == null) return;
        
        clipboardClip = clip.clone();
        clipboardIsCut = false;
        LOGGER.info("[Timeline] 复制片段: {}", clip.getLabel());
    }
    
    /**
     * 粘贴片段到指定轨道
     */
    private void pasteClipToTrack(TimelineTrack track) {
        if (clipboardClip == null || track == null) return;
        
        // 创建副本
        TimelineClip newClip = clipboardClip.clone();
        
        // 调整时间以避免重叠
        float pasteTime = model.getCurrentTime();
        newClip.setStartTime(pasteTime);
        newClip.setEndTime(pasteTime + newClip.getLength());
        
        // 添加到轨道
        track.addClip(newClip);
        model.setSelectedClip(newClip);
        
        markDataChanged();
        LOGGER.info("[Timeline] 粘贴片段: {} 到轨道 {}", newClip.getLabel(), track.getName());
        
        // 如果是剪切操作，清空剪贴板
        if (clipboardIsCut) {
            clipboardClip = null;
            clipboardIsCut = false;
        }
    }
    
    /**
     * 打开关键帧上下文菜单
     */
    private void openKeyframeContextMenu(TimelineClip clip, float keyframeTime) {
        activeContextMenu = TimelineContextMenuBuilder.createStandardKeyframeMenu(
            commandHistory,
            keyframeTime,
            () -> {
                RemoveKeyframeCommand cmd = new RemoveKeyframeCommand(clip.getKeyframes(), keyframeTime);
                commandHistory.executeCommand(cmd);
                markDataChanged(); // 标记数据已改变
            }
        );
    }
    
    /**
     * 剪切选中的片段（快捷键）
     */
    private void cutSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        cutClip(selectedClip, track);
    }
    
    /**
     * 复制选中的片段（快捷键）
     */
    private void copySelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        copyClip(selectedClip);
    }
    
    /**
     * 粘贴片段（快捷键）
     * 粘贴到当前选中片段所在的轨道，如果没有选中则粘贴到第一个轨道
     */
    private void pasteClip() {
        if (clipboardClip == null) return;
        
        TimelineTrack targetTrack = null;
        
        // 优先粘贴到选中片段所在的轨道
        if (model.getSelectedClip() != null) {
            targetTrack = model.findTrackForClip(model.getSelectedClip());
        }
        
        // 如果没有选中片段，粘贴到第一个轨道
        if (targetTrack == null && !model.getTracks().isEmpty()) {
            targetTrack = model.getTracks().getFirst();
        }
        
        if (targetTrack != null) {
            pasteClipToTrack(targetTrack);
        }
    }
    
    /**
     * 适应视图：自动缩放以查看整个时间线
     */
    private void fitTimelineToView() {
        float duration = model.getDuration();
        if (duration <= 0) {
            duration = 10.0f; // 默认时长
        }
        
        // 获取可用的内容宽度（假设为窗口宽度 - 左侧标签宽度）
        float availableWidth = ImGui.getContentRegionAvailX() - TimelineRenderer.getLeftLabelWidth();
        
        // 计算需要的缩放级别（留20%的边距）
        float targetPps = (availableWidth * 0.8f) / duration;
        
        // 限制在合理范围内
        targetPps = clamp(targetPps, model.getMinPps(), model.getMaxPps());
        
        model.setZoom(targetPps);
        model.setScrollX(0); // 重置到开始位置
        
        LOGGER.info("[Timeline] 适应视图：时长={:.1f}s, 缩放={:.1f}px/s", duration, targetPps);
    }
    
    /**
     * 删除选中的片段
     */
    private void deleteSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null) {
            @SuppressWarnings("unchecked")
            List<Object> clipListAsObjects = (List<Object>)(List<?>) track.getClips();
            DeleteClipCommand cmd = new DeleteClipCommand(selectedClip, clipListAsObjects);
            commandHistory.executeCommand(cmd);
            markDataChanged(); // 标记数据已改变
            model.setSelectedClip(null);
            LOGGER.info("[Timeline] 删除片段（命令）");
        }
    }
    
    /**
     * 复制选中的片段
     */
    private void duplicateSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        TimelineClip duplicate = selectedClip.clone();
        duplicate.setStartTime(duplicate.getEndTime() + 0.5f);
        duplicate.setEndTime(duplicate.getStartTime() + selectedClip.getLength());
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null) {
            track.addClip(duplicate);
            model.setSelectedClip(duplicate);
            LOGGER.info("[Timeline] 复制片段");
        }
    }
    
    // ========== 工具方法 ==========
    
    private boolean isMouseHoveringRect(float x1, float y1, float x2, float y2) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }
    
    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
    
    private static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | ((r & 0xFF));
    }
    
    @Override
    public int getPriority() {
        return 200;
    }
}