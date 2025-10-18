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
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiStyleVar;
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
    
    // ========== 中键平移状态 ==========
    private boolean middleButtonPanning = false;
    private float panStartX = 0f;
    
    // ========== 缩放预设 ==========
    private static final float[] ZOOM_PRESETS = {40f, 80f, 120f, 200f, 400f};
    private static final String[] ZOOM_PRESET_LABELS = {"0.5x", "1x", "1.5x", "2.5x", "5x"};
    
    // ========== 布局常量 ==========
    private static final float CANVAS_HEIGHT = 220f;
    
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
        
        // 3. 渲染控制栏
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, UITheme.Spacing.NORMAL, UITheme.Spacing.SMALL);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, UITheme.Spacing.BUTTON_PADDING_X, UITheme.Spacing.BUTTON_PADDING_Y);
        renderControlBar();
        ImGui.popStyleVar(2);
        
        UITheme.separator();
        
        // 4. 渲染时间线画布
        renderTimelineCanvas();
        
        // 5. 渲染上下文菜单
        if (activeContextMenu != null) {
            activeContextMenu.render();
        }
    }
    
    /**
     * 渲染控制栏
     */
    private void renderControlBar() {
        // ========== 撤销/重做按钮 ==========
        boolean canUndo = commandHistory.canUndo();
        boolean canRedo = commandHistory.canRedo();
        
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(Icons.UNDO + " 撤销", 60, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.undo();
        }
        if (!canUndo) ImGui.endDisabled();
        
        ImGui.sameLine();
        
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(Icons.REDO + " 重做", 60, UITheme.Spacing.BUTTON_HEIGHT)) {
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
        
        // ========== 播放控制 ==========
        if (ImGui.button(Icons.SKIP_BACK, 40, UITheme.Spacing.BUTTON_HEIGHT)) {
            model.jumpToStart();
        }
        ImGui.sameLine();
        if (ImGui.button(model.isPlaying() ? (Icons.PAUSE + " 暂停") : (Icons.PLAY + " 播放"), 70, UITheme.Spacing.BUTTON_HEIGHT)) {
            model.togglePlay();
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.SKIP_FORWARD)) {
            model.jumpToEnd();
        }
        ImGui.sameLine();
        ImGui.text(String.format("时间: %.2f / %.2f", model.getCurrentTime(), model.getDuration()));
        
        // ========== 精确时间输入（新增功能）==========
        ImGui.sameLine();
        float[] timeRef = {model.getCurrentTime()};
        ImGui.pushItemWidth(80);
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
        
        float contentX = winX + TimelineRenderer.getLeftLabelWidth();
        float contentW = winW - TimelineRenderer.getLeftLabelWidth();
        float contentY = winY + TimelineRenderer.getRulerHeight();
        float contentH = CANVAS_HEIGHT - TimelineRenderer.getRulerHeight();
        
        // 使用渲染器绘制
        renderer.renderCanvas(drawList, winX, winY, winW, CANVAS_HEIGHT);
        
        // 处理鼠标输入
        handleMouseInput(contentX, contentY, contentW, contentH);
        
        // 渲染拖放高亮
        if (dropTargetTrackIndex >= 0) {
            float y = contentY + dropTargetTrackIndex * (TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap());
            renderer.renderDropHighlight(drawList, dropTargetTrackIndex, contentX, y, contentW);
        }
        
        // 处理片段交互
        handleClipInteractions(contentX, contentY, contentW, contentH);
        
        // 处理播放头交互
        handlePlayheadInteraction(contentX, contentY, contentW, contentH);
        
        // 不可见按钮接收鼠标事件
        ImGui.setCursorScreenPos(winX, winY);
        ImGui.invisibleButton("##TimelineCanvas", winW, CANVAS_HEIGHT);
        
        // 接收拖放
        handleDragDrop(contentX);
        
        ImGui.setCursorScreenPos(winX, winY + CANVAS_HEIGHT);
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
                float factor = (float)Math.pow(1.1, wheel);
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
        
        for (TimelineTrack track : model.getTracks()) {
            // 跳过不可见轨道
            if (y + TimelineRenderer.getTrackHeight() < contentY || y > contentY + contentH) {
                y += TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
                continue;
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
                float newStart = interactionManager.getDragStartValue() + deltaTime;
                if (effectiveSnap) newStart = model.snapToInterval(newStart);
                newStart = clamp(newStart, 0f, model.getDuration() - clip.getLength());
                clip.setEndTime(newStart + clip.getLength());
                clip.setStartTime(newStart);
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
            LOGGER.info("[Timeline] 完成 {} 操作（命令）", mode);
        }
        
        interactionManager.endInteraction();
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
     * 处理播放头交互
     */
    private void handlePlayheadInteraction(float contentX, float contentY, float contentW, float contentH) {
        float px = model.timeToScreen(model.getCurrentTime(), contentX);
        
        boolean hoveredHandle = isMouseHoveringRect(px - 6, contentY - 12, px + 6, contentY + 6);
        
        if (!interactionManager.isInteracting() && hoveredHandle && 
            ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            interactionManager.startInteraction(
                InteractionMode.DRAG_PLAYHEAD,
                null, ImGui.getMousePosX(), 0,
                model.getCurrentTime(), 0f
            );
        }
        
        if (interactionManager.isInteracting(InteractionMode.DRAG_PLAYHEAD) && 
            ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            
            float deltaX = interactionManager.calculateDeltaX(ImGui.getMousePosX());
            float deltaTime = deltaX / model.getPixelsPerSecond();
            float newTime = interactionManager.getDragStartValue() + deltaTime;
            
            boolean effectiveSnap = model.isSnapEnabled() ^ ImGui.getIO().getKeyCtrl();
            if (effectiveSnap) newTime = model.snapToInterval(newTime);
            
            model.setCurrentTime(newTime);
        }
        
        if (interactionManager.isInteracting(InteractionMode.DRAG_PLAYHEAD) && 
            ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            interactionManager.endInteraction();
            LOGGER.debug("[Timeline] 播放头移动到: {:.2f}s", model.getCurrentTime());
        }
        
        // 点击标尺跳转
        if (!interactionManager.isInteracting() && 
            ImGui.isMouseClicked(ImGuiMouseButton.Left) && 
            isMouseHoveringRect(contentX, contentY - 22, contentX + contentW, contentY)) {
            float t = model.screenToTime(ImGui.getMousePosX(), contentX);
            model.setCurrentTime(t);
            LOGGER.debug("[Timeline] 点击跳转到: {:.2f}s", model.getCurrentTime());
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
            LOGGER.debug("[Timeline] 删除关键帧 at {:.2f}（命令）", existingKf);
        } else {
            AddKeyframeCommand cmd = new AddKeyframeCommand(clip.getKeyframes(), clickTime);
            commandHistory.executeCommand(cmd);
            LOGGER.debug("[Timeline] 添加关键帧 at {:.2f}（命令）", clickTime);
        }
    }
    
    /**
     * 打开片段上下文菜单
     */
    private void openClipContextMenu(TimelineClip clip) {
        activeContextMenu = TimelineContextMenuBuilder.createStandardClipMenu(
            commandHistory,
            clip,
                this::deleteSelectedClip,
                this::duplicateSelectedClip
        );
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
            }
        );
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