package com.chronoblocks.ui.timeline;

import com.chronoblocks.core.timeline.TimelineCommandHistory;
import com.chronoblocks.ui.panels.TimelinePanel;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiMouseButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 时间线交互处理器
 * 
 * 职责：专门负责处理时间线画布的所有鼠标和键盘交互
 * - 鼠标输入处理（缩放、平移、滚轮）
 * - 片段交互（拖动、调整大小、右键菜单）
 * - 关键帧交互（拖动、添加、删除）
 * - 播放头交互（拖动、点击跳转）
 * - 拖放处理
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineInteractionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineInteractionHandler.class);
    
    private final TimelineModel model;
    private final TimelineInteractionManager interactionManager;
    private final TimelineCommandHistory commandHistory;
    private final TimelineRenderer renderer;
    
    // ========== 中键平移状态 ==========
    private boolean middleButtonPanning = false;
    private float panStartX = 0f;
    
    // ========== 回调接口 ==========
    private InteractionCallbacks callbacks;
    
    /**
     * 交互回调接口
     */
    public interface InteractionCallbacks {
        void onClipRightClick(TimelineClip clip);
        void onTrackRightClick(TimelineTrack track);
        void onKeyframeRightClick(TimelineClip clip, float keyframeTime);
        void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX);
        void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime);
        void onKeyframeDragFinished(TimelineClip clip, float originalTime, float newTime);
        void onDataChanged();
        void setHoveredTrack(TimelineTrack track);
        void setDropTargetTrackIndex(int index);
        boolean isMouseHoveringRect(float x1, float y1, float x2, float y2);
        void onToggleTrackLock(TimelineTrack track);
        void onToggleTrackVisible(TimelineTrack track);
        void onContainerDropped(String containerId);
        void onContainerDroppedToTrack(String containerId, int trackIndex);
        void onContainerDropRejected(String containerId, int trackIndex);
    }
    
    public TimelineInteractionHandler(
            TimelineModel model,
            TimelineInteractionManager interactionManager,
            TimelineCommandHistory commandHistory,
            TimelineRenderer renderer) {
        this.model = model;
        this.interactionManager = interactionManager;
        this.commandHistory = commandHistory;
        this.renderer = renderer;
    }
    
    public void setCallbacks(InteractionCallbacks callbacks) {
        this.callbacks = callbacks;
    }
    
    /**
     * 处理画布所有交互（主入口方法）
     */
    public void handleCanvasInteractions(float contentX, float trackAreaTop, float contentW, float viewportHeight, float trackScrollY) {
        handleMouseInput(contentX, trackAreaTop, contentW, viewportHeight);
        handleClipInteractions(contentX, trackAreaTop, contentW, viewportHeight, trackScrollY);
        handleTrackControlInteractions();
        handlePlayheadInteraction(contentX, trackAreaTop, contentW, viewportHeight, trackScrollY);
        handleDragDrop(contentX, trackAreaTop);
    }

    private void handleMouseInput(float contentX, float trackAreaTop, float contentW, float viewportHeight) {
        ImGuiIO io = ImGui.getIO();
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();

        // 检查鼠标是否在时间线面板范围内
        boolean mouseInTimeline = callbacks.isMouseHoveringRect(contentX, trackAreaTop, contentX + contentW, trackAreaTop + viewportHeight);

        boolean middleDown = ImGui.isMouseDown(ImGuiMouseButton.Middle);
        if (middleDown && !middleButtonPanning && ImGui.isMouseClicked(ImGuiMouseButton.Middle)) {
            // 只有在鼠标在时间线面板范围内时才开始中键拖拽
            if (mouseInTimeline) {
                middleButtonPanning = true;
                panStartX = mx;
            }
        }
        if (middleButtonPanning && middleDown) {
            // 只有在鼠标仍在时间线面板范围内时才继续拖拽
            if (mouseInTimeline) {
                float dx = mx - panStartX;
                model.scroll(-dx);
                panStartX = mx;
                model.clampScroll(contentW);
            } else {
                // 如果鼠标移出时间线面板，停止拖拽
                middleButtonPanning = false;
            }
        }
        if (middleButtonPanning && ImGui.isMouseReleased(ImGuiMouseButton.Middle)) {
            middleButtonPanning = false;
        }

        boolean hoverContent = callbacks.isMouseHoveringRect(contentX, trackAreaTop, contentX + contentW, trackAreaTop + viewportHeight);
        if (hoverContent) {
            float wheel = io.getMouseWheel();
            if (wheel != 0f) {
                float oldPps = model.getPixelsPerSecond();
                boolean ctrlPressed = io.getKeyCtrl();
                float baseFactor = (float) Math.pow(1.1, wheel);
                float factor = ctrlPressed ? (float) Math.pow(baseFactor, 0.5) : baseFactor;
                model.zoom(factor);
                model.adjustScrollAfterZoom(mx, contentX, contentW, oldPps);
            }

            float wheelH = io.getMouseWheelH();
            if (wheelH != 0f) {
                model.scroll(-wheelH * 40f);
                model.clampScroll(contentW);
            }
        }
    }

    private void handleClipInteractions(float contentX, float trackAreaTop, float contentW, float viewportHeight, float trackScrollY) {
        float y = trackAreaTop - trackScrollY;
        float viewportBottom = trackAreaTop + viewportHeight;
        float winX = contentX - TimelineRenderer.getLeftLabelWidth();

        for (TimelineTrack track : model.getTracks()) {
            float trackBottom = y + TimelineRenderer.getTrackHeight();
            if (trackBottom < trackAreaTop) {
                y += TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
                continue;
            }
            if (y > viewportBottom) {
                break;
            }

            // 检测轨道标签区域的右键点击
            boolean hoverTrackLabel = callbacks.isMouseHoveringRect(
                winX, y,
                contentX, y + TimelineRenderer.getTrackHeight()
            );
            
            if (hoverTrackLabel) {
                callbacks.setHoveredTrack(track);
                if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                    LOGGER.debug("[Timeline] 右键点击轨道: {}", track.getName());
                    callbacks.onTrackRightClick(track);
                    return; // 阻止事件穿透
                }
            }

            handleClipInteraction(track, y, contentX, contentW);
            y += TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        }
    }

    private void handleClipInteraction(TimelineTrack track, float trackY, float contentX, float contentW) {
        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();

        for (TimelineClip clip : track.getClips()) {
            float x1 = contentX + (clip.getStartTime() * pps) - scrollX;
            float x2 = contentX + (clip.getEndTime() * pps) - scrollX;
            float mx = ImGui.getMousePosX();

            boolean hovered = callbacks.isMouseHoveringRect(x1, trackY, x2, trackY + TimelineRenderer.getTrackHeight());

            if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                model.setSelectedClip(clip);
                callbacks.onClipRightClick(clip);
                return;
            }

            renderer.renderResizeHandles(ImGui.getWindowDrawList(), clip, contentX, trackY, TimelineRenderer.getTrackHeight());

            boolean hoverLeft = Math.abs(mx - x1) < TimelineRenderer.getResizeHandleWidth() && hovered;
            boolean hoverRight = Math.abs(mx - x2) < TimelineRenderer.getResizeHandleWidth() && hovered;

            if (!interactionManager.isInteracting() && hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                model.setSelectedClip(clip);
                TimelineInteractionManager.InteractionMode mode;
                if (hoverLeft) {
                    mode = TimelineInteractionManager.InteractionMode.RESIZE_CLIP_LEFT;
                } else if (hoverRight) {
                    mode = TimelineInteractionManager.InteractionMode.RESIZE_CLIP_RIGHT;
                } else {
                    mode = TimelineInteractionManager.InteractionMode.DRAG_CLIP;
                }

                interactionManager.startInteraction(mode, clip, mx, 0, clip.getStartTime(), clip.getEndTime());
            }

            if (interactionManager.getActiveObject() == clip && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                handleActiveClipInteraction(clip);
            }

            if (interactionManager.getActiveObject() == clip && ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
                endClipInteraction(clip);
            }

            // 双击添加/删除关键帧
            if (hovered && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                callbacks.onKeyframeDoubleClick(clip, mx, contentX);
            }

            // 处理关键帧交互
            handleKeyframeInteractions(clip, contentX, trackY);
        }
    }

    private void handleActiveClipInteraction(TimelineClip clip) {
        float mx = ImGui.getMousePosX();
        float deltaX = interactionManager.calculateDeltaX(mx);
        float deltaTime = deltaX / model.getPixelsPerSecond();

        boolean effectiveSnap = model.isSnapEnabled() ^ ImGui.getIO().getKeyCtrl();

        switch (interactionManager.getCurrentMode()) {
            case DRAG_CLIP -> {
                List<Object> selectedItems = model.getSelectedItems();
                boolean isGroupDrag = selectedItems.size() > 1 && model.isSelected(clip);

                float newStart = interactionManager.getDragStartValue() + deltaTime;
                if (effectiveSnap) newStart = model.snapToInterval(newStart);
                if (isGroupDrag) {
                    float actualDelta = newStart - interactionManager.getDragStartValue();
                    for (Object item : selectedItems) {
                        if (item instanceof TimelineClip selectedClip) {
                            float clipNewStart = selectedClip.getStartTime() + actualDelta;
                            clipNewStart = clamp(clipNewStart, 0f, model.getDuration() - selectedClip.getLength());
                            selectedClip.setStartTime(clipNewStart);
                            selectedClip.setEndTime(clipNewStart + selectedClip.getLength());
                        }
                    }
                } else {
                    newStart = clamp(newStart, 0f, model.getDuration() - clip.getLength());
                    clip.setEndTime(newStart + clip.getLength());
                    clip.setStartTime(newStart);
                }
            }
            case RESIZE_CLIP_LEFT -> {
                float newLeft = interactionManager.getDragStartValue() + deltaTime;
                if (effectiveSnap) newLeft = model.snapToInterval(newLeft);
                clip.setStartTime(clamp(newLeft, 0f, clip.getEndTime() - 0.1f));
            }
            case RESIZE_CLIP_RIGHT -> {
                float newRight = interactionManager.getDragStartValue2() + deltaTime;
                if (effectiveSnap) newRight = model.snapToInterval(newRight);
                clip.setEndTime(clamp(newRight, clip.getStartTime() + 0.1f, model.getDuration()));
            }
            default -> { }
        }
    }

    private void endClipInteraction(TimelineClip clip) {
        TimelineInteractionManager.InteractionMode mode = interactionManager.getCurrentMode();

        if (mode == TimelineInteractionManager.InteractionMode.DRAG_CLIP ||
            mode == TimelineInteractionManager.InteractionMode.RESIZE_CLIP_LEFT ||
            mode == TimelineInteractionManager.InteractionMode.RESIZE_CLIP_RIGHT) {

            autoExtendDuration(clip.getEndTime());

            // 不再创建命令，而是通过回调通知控制器交互已完成
            // 控制器负责创建命令和管理历史
            if (callbacks != null) {
                callbacks.onClipInteractionFinished(
                    clip,
                    interactionManager.getDragStartValue(),  // 原始开始时间
                    interactionManager.getDragStartValue2()  // 原始结束时间
                );
            }
            
            LOGGER.debug("[Timeline] 完成 {} 交互", mode);
        }

        interactionManager.endInteraction();
    }

    private void autoExtendDuration(float requiredTime) {
        float currentDuration = model.getDuration();
        if (requiredTime > currentDuration) {
            float newDuration = (float)Math.ceil(requiredTime + 1.0f);
            model.setDuration(newDuration);
            LOGGER.info("[Timeline] 自动扩展时长: {:.2f}s -> {:.2f}s", currentDuration, newDuration);
            if (callbacks != null) {
                callbacks.onDataChanged();
            }
        }
    }

    private void handleTrackControlInteractions() {
        if (callbacks == null) {
            return;
        }
        if (!ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            return;
        }
        for (TimelineRenderer.TrackControlHit hit : renderer.getTrackControlHitAreas()) {
            if (!callbacks.isMouseHoveringRect(hit.x1, hit.y1, hit.x2, hit.y2)) {
                continue;
            }
            switch (hit.type) {
                case LOCK -> callbacks.onToggleTrackLock(hit.track);
                case VISIBILITY -> callbacks.onToggleTrackVisible(hit.track);
                default -> { }
            }
            return;
        }
    }

    private void handlePlayheadInteraction(float contentX, float trackAreaTop, float contentW, float viewportHeight, float trackScrollY) {
        float px = model.timeToScreen(model.getCurrentTime(), contentX);

        if (px < contentX - 20 || px > contentX + contentW + 20) return;

        float rulerTop = trackAreaTop - TimelineRenderer.getRulerBottomPadding() - TimelineRenderer.getRulerHeight();
        float rulerBottom = rulerTop + TimelineRenderer.getRulerHeight();
        boolean hoveredHandle = callbacks.isMouseHoveringRect(px - 12, rulerTop, px + 12, trackAreaTop + viewportHeight);

        if (interactionManager.isInteracting() && interactionManager.getCurrentMode() != TimelineInteractionManager.InteractionMode.DRAG_PLAYHEAD) {
            return;
        }

        if (!interactionManager.isInteracting() && hoveredHandle && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            interactionManager.startInteraction(
                    TimelineInteractionManager.InteractionMode.DRAG_PLAYHEAD,
                    null, ImGui.getMousePosX(), 0,
                    model.getCurrentTime(), 0f
            );
        }

        if (interactionManager.isInteracting(TimelineInteractionManager.InteractionMode.DRAG_PLAYHEAD) &&
                ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            float newTime = model.screenToTime(ImGui.getMousePosX(), contentX);
            boolean effectiveSnap = model.isSnapEnabled() ^ ImGui.getIO().getKeyCtrl();
            if (effectiveSnap) newTime = model.snapToInterval(newTime);
            newTime = clamp(newTime, 0f, model.getDuration());
            model.setCurrentTime(newTime);
        }

        if (interactionManager.isInteracting(TimelineInteractionManager.InteractionMode.DRAG_PLAYHEAD) &&
                ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            interactionManager.endInteraction();
        }

        if (!interactionManager.isInteracting() &&
                ImGui.isMouseClicked(ImGuiMouseButton.Left) &&
                callbacks.isMouseHoveringRect(contentX, rulerTop, contentX + contentW, rulerBottom)) {
            float t = model.screenToTime(ImGui.getMousePosX(), contentX);
            t = clamp(t, 0f, model.getDuration());
            model.setCurrentTime(t);
        }
    }

    private void handleDragDrop(float contentX, float trackAreaTop) {
        if (ImGui.beginDragDropTarget()) {
            // 计算鼠标位置对应的轨道索引
            int targetTrackIndex = calculateTargetTrackIndex(trackAreaTop);
            
            // 检查轨道是否支持容器拖放
            boolean isValidTrack = isTrackValidForContainerDrop(targetTrackIndex);
            
            // 设置拖放目标高亮（只有有效轨道才高亮）
            if (callbacks != null) {
                callbacks.setDropTargetTrackIndex(isValidTrack ? targetTrackIndex : -1);
            }
            
            // 处理容器拖放
            Object containerPayload = ImGui.acceptDragDropPayload("CHRONOBLOCKS_CONTAINER");
            if (containerPayload != null) {
                String containerId = (String) containerPayload;
                
                if (isValidTrack) {
                    LOGGER.info("[Timeline] 接收到容器拖放: {} -> 轨道索引: {}", containerId, targetTrackIndex);
                    
                    // 清除拖放目标高亮
                    if (callbacks != null) {
                        callbacks.setDropTargetTrackIndex(-1);
                    }
                    
                    // 通知回调处理容器到轨道的转换
                    if (callbacks != null) {
                        callbacks.onContainerDroppedToTrack(containerId, targetTrackIndex);
                    }
                } else {
                    LOGGER.warn("[Timeline] 容器拖放被拒绝: {} -> 轨道索引: {} (不支持的轨道类型)", 
                        containerId, targetTrackIndex);
                    
                    // 清除拖放目标高亮
                    if (callbacks != null) {
                        callbacks.setDropTargetTrackIndex(-1);
                    }
                    
                    // 通知回调显示错误提示
                    if (callbacks != null) {
                        callbacks.onContainerDropRejected(containerId, targetTrackIndex);
                    }
                }
            }
            
            // 处理其他拖放类型
            ImGui.acceptDragDropPayload(TimelinePanel.DND_PAYLOAD_ITEM);
            ImGui.endDragDropTarget();
        } else {
            // 清除拖放目标高亮
            if (callbacks != null) {
                callbacks.setDropTargetTrackIndex(-1);
            }
        }
    }
    
    /**
     * 计算鼠标位置对应的轨道索引
     */
    private int calculateTargetTrackIndex(float trackAreaTop) {
        float mouseY = ImGui.getMousePosY();
        
        // 计算轨道高度和间隙
        float trackHeight = TimelineRenderer.getTrackHeight();
        float trackGap = TimelineRenderer.getTrackGap();
        float totalTrackHeight = trackHeight + trackGap;
        
        // 计算鼠标相对于轨道区域的Y坐标
        float relativeY = mouseY - trackAreaTop;
        
        // 计算轨道索引
        int trackIndex = (int) (relativeY / totalTrackHeight);
        
        // 限制在有效范围内
        int maxTrackIndex = model.getTracks().size();
        if (trackIndex < 0) trackIndex = 0;
        if (trackIndex > maxTrackIndex) trackIndex = maxTrackIndex;
        
        return trackIndex;
    }
    
    /**
     * 检查轨道是否支持容器拖放
     * 容器只能被拖放到容器、摄像机、天气、光照、标记轨道上
     * 不能拖放到音频和特效轨道上
     */
    private boolean isTrackValidForContainerDrop(int trackIndex) {
        if (trackIndex < 0 || trackIndex >= model.getTracks().size()) {
            return false; // 超出范围，无效
        }
        
        TimelineTrack track = model.getTrackAt(trackIndex);
        if (track == null) {
            return false; // 轨道不存在，无效
        }
        
        TrackType trackType = track.getType();
        
        // 允许的轨道类型：容器、摄像机、天气、光照、标记
        // 不允许的轨道类型：音频、特效
        return trackType == TrackType.CONTAINER ||
               trackType == TrackType.CAMERA ||
               trackType == TrackType.WEATHER ||
               trackType == TrackType.LIGHTING ||
               trackType == TrackType.MARKER;
    }

    /**
     * 处理关键帧交互（纯输入处理，不修改数据）
     */
    private void handleKeyframeInteractions(TimelineClip clip, float contentX, float y) {
        float mx = ImGui.getMousePosX();
        
        for (Float kf : new java.util.ArrayList<>(clip.getKeyframes())) {
            float kx = model.timeToScreen(kf, contentX);
            boolean hoverKeyframe = Math.abs(mx - kx) < 5 && 
                callbacks.isMouseHoveringRect(kx - 5, y, kx + 5, y + TimelineRenderer.getTrackHeight());
            
            // 右键菜单
            if (hoverKeyframe && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                callbacks.onKeyframeRightClick(clip, kf);
                return;
            }
            
            // 开始拖动关键帧
            if (!interactionManager.isInteracting() && hoverKeyframe && 
                ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                interactionManager.startInteraction(
                    TimelineInteractionManager.InteractionMode.DRAG_KEYFRAME,
                    kf, mx, 0, kf, 0f
                );
            }
        }
        
        // 拖动过程中不修改数据，只做预览（可选：可以在这里计算 newTime 用于渲染幽灵关键帧）
        if (interactionManager.isInteracting(TimelineInteractionManager.InteractionMode.DRAG_KEYFRAME) && 
            ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            // 拖动过程中不做任何数据修改
            // Handler 只负责输入处理，不负责业务逻辑
        }
        
        // 拖动结束时，通过回调通知控制器
        if (interactionManager.isInteracting(TimelineInteractionManager.InteractionMode.DRAG_KEYFRAME) && 
            ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            
            Object activeObj = interactionManager.getActiveObject();
            if (activeObj instanceof Float) {
                float originalTime = (Float) activeObj;
                
                // 重新计算最终的 newTime
                float deltaX = interactionManager.calculateDeltaX(mx);
                float deltaTime = deltaX / model.getPixelsPerSecond();
                float newTime = interactionManager.getDragStartValue() + deltaTime;
                
                boolean effectiveSnap = model.isSnapEnabled() ^ ImGui.getIO().getKeyCtrl();
                if (effectiveSnap) newTime = model.snapToInterval(newTime);
                
                newTime = clamp(newTime, clip.getStartTime(), clip.getEndTime());
                
                // 通过回调通知控制器，让控制器负责创建命令和修改数据
                if (callbacks != null && Math.abs(newTime - originalTime) > 0.0001f) {
                    callbacks.onKeyframeDragFinished(clip, originalTime, newTime);
                }
            }
            
            interactionManager.endInteraction();
            LOGGER.debug("[Timeline] 关键帧拖动交互完成");
        }
    }

    // ========== 工具方法 ==========
    
    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
}