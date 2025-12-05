package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.ui.timeline.commands.AddKeyframeCommand;
import com.chronoblocks.ui.timeline.commands.RemoveKeyframeCommand;
import com.chronoblocks.ui.timeline.commands.ResizeClipCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.chronoblocks.ui.timeline.interaction.TimelineTrackHitTest.TrackControlHitResult;
import com.chronoblocks.ui.timeline.menu.TimelineContextMenu;
import com.chronoblocks.ui.timeline.menu.TimelineContextMenuFactory;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.render.TimelineRenderer;
import com.chronoblocks.ui.timeline.render.TimelineTrackControlHit;
import com.chronoblocks.ui.timeline.snapping.TimelineSnapping;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * ChronoBlocks TimelineInteractionHandler锛圫napping + Renderer v2�?
 *
 * <p>鑱岃矗锛氬皢 ImGui 杈撳叆杞崲涓哄�?TimelineModel 鐨勪慨鏀癸紝鍚屾椂缁熶竴浣跨�?
 * TimelineRenderer 鐨勫潗鏍囩郴缁熶�?TimelineSnapping 鐨勭鍚稿伐鍏枫�?/p>
 */
public class TimelineInteractionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineInteractionHandler.class);

    private static final float DOUBLE_CLICK_THRESHOLD = 0.25f; // �?
    // �?优化：增大边缘检测范围，使拖拽调整宽度更容易触发
    private static final float CLIP_EDGE_RESIZE_PIXEL = 8f;    // 判断靠近边缘进行缩放（从6f增加�?f�?
    private static final float MIN_CLIP_LENGTH = 0.05f;
    
    private final TimelineModel model;
    private final TimelineInteractionManager interactionManager;
    @SuppressWarnings("unused")
    private final TimelineCommandHistory commandHistory;
    private final TimelineRenderer renderer;
    
    private TimelineContextMenuFactory menuFactory;
    private InteractionCallbacks callbacks;
    private TimelineContextMenu activeContextMenu;

    private float lastClickTime = 0f;
    private float lastClickX = 0f;
    private float lastClickY = 0f;

    /**
     * 浜や簰鍥炶皟鎺ュ彛锛岀�?TimelinePanel 瀹炵幇銆?
     */
    public interface InteractionCallbacks {
        void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX);
        void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime);
        void onKeyframeDragFinished(TimelineClip clip, float originalTime, float newTime);
        void onDataChanged();
        void setHoveredTrack(TimelineTrack track);
        void setDropTargetTrackIndex(int index);
        boolean isMouseHoveringRect(float x1, float y1, float x2, float y2);
        void onAddKeyframeToTrack(TimelineTrack track);
        void onToggleTrackLock(TimelineTrack track);
        void onToggleTrackVisible(TimelineTrack track);
        void onContainerDropped(String containerId);
        void onContainerDroppedToTrack(String containerId, int trackIndex);
        void onContainerDropRejected(String containerId, int trackIndex);
    }
    
    public TimelineInteractionHandler(TimelineModel model,
            TimelineInteractionManager interactionManager,
            TimelineCommandHistory commandHistory,
            TimelineRenderer renderer) {
        this.model = Objects.requireNonNull(model, "model");
        this.interactionManager = Objects.requireNonNull(interactionManager, "interactionManager");
        this.commandHistory = Objects.requireNonNull(commandHistory, "commandHistory");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }
    
    public void setCallbacks(InteractionCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void setMenuFactory(TimelineContextMenuFactory menuFactory) {
        this.menuFactory = menuFactory;
    }

    public TimelineContextMenu getActiveContextMenu() {
        return activeContextMenu;
    }
    
    /**
     * ⭐ 静默取消所有交互（不触发任何回调，用于 UI 关闭时清理状态）
     */
    public void cancelAllInteractionsSilently() {
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode == InteractionMode.DRAG_PLAYHEAD) {
            // 静默结束 scrubbing，不通知摄像机控制器
            model.setScrubbing(false);
        }
        interactionManager.cancelAllInteractionsSilently();
        LOGGER.debug("[TimelineInteractionHandler] 静默取消所有交互");
    }

    /**
     * @param contentX     轨道内容区域 X 起点�?= winX + leftLabelWidth�?
     * @param trackAreaTop 轨道区域顶部 Y�?= winY + headerHeight�?
     * @param contentW     内容宽度
     * @param viewportH    内容高度
     * @param scrollY      垂直滚动
     * @param rightClickedOnCanvas 是否在画布上右键点击（由 invisibleButton 检测）
     */
    public void handleCanvasInteractions(float contentX,
                                         float trackAreaTop,
                                         float contentW,
                                         float viewportH,
                                         float scrollY,
                                         boolean rightClickedOnCanvas) {
        ImGuiIO io = ImGui.getIO();
        float mouseX = io.getMousePosX();
        float mouseY = io.getMousePosY();

        // 1) 轨道头部控件命中（锁 / 眼睛�?
        TrackControlHitResult trackControlHit = TimelineTrackHitTest.hitTrackControls(
                renderer, mouseX, mouseY
        );

        // 2) 时间线内容命中（Clip / Keyframe / Empty�?
        TimelineHitResult hit = TimelineHitTest.compute(
                renderer, model,
                mouseX, mouseY,
                contentX, trackAreaTop,
                contentW, viewportH, scrollY,
                trackControlHit
        );

        // 更新 hover 轨道（供 UI 高亮用）
        if (callbacks != null) {
            if (hit.track() != null && hit.trackIndex() >= 0) {
                callbacks.setHoveredTrack(hit.track());
            } else {
                callbacks.setHoveredTrack(null);
            }
        }

        // �?优化：更新鼠标光标（边缘时显示调整光标，中间显示箭头�?
        updateMouseCursor(hit, contentX, mouseX);

        // 鼠标事件
        handleMouseButtons(hit, trackControlHit, contentX, trackAreaTop, contentW, viewportH, scrollY, rightClickedOnCanvas);
        handleDrag(hit, contentX, trackAreaTop, contentW, viewportH, scrollY);
        
        // 注意：容器拖放处理现在在 TimelinePanel 中直接处理（�?invisibleButton 之后�?
        // 这样可以确保在正确的窗口上下文中设置拖放目标
    }

    // ========== 鼠标按键处理 ==========

    private void handleMouseButtons(TimelineHitResult hit,
                                    TrackControlHitResult trackControlHit,
                                    float contentX,
                                    float trackAreaTop,
                                    float contentW,
                                    float viewportH,
                                    float scrollY,
                                    boolean rightClickedOnCanvas) {
        ImGuiIO io = ImGui.getIO();
        float mouseX = io.getMousePosX();
        float mouseY = io.getMousePosY();

        // 左键点击
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {

            // 0) 优先处理轨道头部控件（锁 / 眼睛�?
            if (trackControlHit != null && trackControlHit.isHit() && callbacks != null) {
                TimelineTrack track = trackControlHit.track();
                TimelineTrackControlHit.TrackControlType type = trackControlHit.type();

                switch (type) {
                    case ADD_KEYFRAME -> callbacks.onAddKeyframeToTrack(track);
                    case LOCK -> callbacks.onToggleTrackLock(track);
                    case VISIBILITY -> callbacks.onToggleTrackVisible(track);
                }
                // 控件点击不再向下传递，不影�?Clip / Keyframe 选择
                return;
            }

            // 1) 记录 double-click 信息
            float now = (float) ImGui.getTime();
            boolean isDoubleClick = (now - lastClickTime) <= DOUBLE_CLICK_THRESHOLD &&
                    Math.hypot(mouseX - lastClickX, mouseY - lastClickY) < 5.0f;

            lastClickTime = now;
            lastClickX = mouseX;
            lastClickY = mouseY;

            // 2) 根据 hit 类型分发
            switch (hit.type()) {
                case CLIP -> onClipClicked(hit, mouseX, contentX, isDoubleClick);
                case KEYFRAME -> onKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
                case AUTOMATION_KEYFRAME -> onAutomationKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
                case EMPTY -> onEmptyClicked(hit, mouseX, contentX);
                default -> { /* NONE / TRACK_HEADER 在这里忽�?*/ }
            }
        }

        // 右键打开上下文菜单（如果不是点在轨道头控件上�?
        // 使用 rightClickedOnCanvas 参数，因�?invisibleButton 会拦截右键事�?
        if (rightClickedOnCanvas) {
            System.out.println("[TimelineInteractionHandler] 检测到右键点击，hit.type() = " + 
                    (hit != null ? hit.type() : "null") + 
                    ", trackControlHit = " + (trackControlHit != null && trackControlHit.isHit()));
            LOGGER.info("[Timeline] 检测到右键点击，hit.type() = {}, trackControlHit = {}", 
                    hit != null ? hit.type() : "null", 
                    trackControlHit != null && trackControlHit.isHit());
            if (trackControlHit == null || !trackControlHit.isHit()) {
                openContextMenuForHit(hit);
            } else {
                System.out.println("[TimelineInteractionHandler] 右键点击被轨道控件拦截");
                LOGGER.info("[Timeline] 右键点击被轨道控件拦截");
            }
        } else {
            // 调试：检查为什么没有检测到右键点击
            if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                System.out.println("[TimelineInteractionHandler] ImGui 检测到右键点击，但 rightClickedOnCanvas = false");
            }
        }

        // 鼠标释放结束交互
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            endInteractionIfNeeded();
        }
    }

    /**
     * 更新鼠标光标（根据鼠标位置显示不同的光标�?
     * 
     * <p>�?优化：边缘时显示调整光标（ResizeEW），中间显示箭头（Arrow�?
     * 类似 Premiere 的行为，提供清晰的视觉反�?
     * 
     * <p>�?轨道锁定功能：锁定轨道上的片段不显示调整光标
     */
    private void updateMouseCursor(TimelineHitResult hit, float contentX, float mouseX) {
        // 如果正在交互中，不更新光标（避免闪烁�?
        if (interactionManager.isInteracting()) {
            return;
        }
        
        if (hit.type() == TimelineHitResult.HitType.CLIP) {
            TimelineClip clip = hit.clip();
            TimelineTrack track = hit.track();
            if (clip != null) {
                // �?轨道锁定功能：锁定轨道上的片段不显示调整光标
                if (track != null && track.isLocked()) {
                    ImGui.setMouseCursor(ImGuiMouseCursor.Arrow);
                    return;
                }
                
                float startX = renderer.timeToX(clip.getStartTime(), contentX);
                float endX = renderer.timeToX(clip.getEndTime(), contentX);
                
                boolean nearLeft = Math.abs(mouseX - startX) <= CLIP_EDGE_RESIZE_PIXEL;
                boolean nearRight = Math.abs(mouseX - endX) <= CLIP_EDGE_RESIZE_PIXEL;
                
                // 对于小片段，扩大边缘检测范�?
                float clipWidth = endX - startX;
                if (clipWidth < CLIP_EDGE_RESIZE_PIXEL * 3) {
                    nearLeft = mouseX <= startX + CLIP_EDGE_RESIZE_PIXEL * 1.5f;
                    nearRight = mouseX >= endX - CLIP_EDGE_RESIZE_PIXEL * 1.5f;
                }
                
                if (nearLeft || nearRight) {
                    ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
                } else {
                    ImGui.setMouseCursor(ImGuiMouseCursor.Arrow);
                }
            } else {
                ImGui.setMouseCursor(ImGuiMouseCursor.Arrow);
            }
        } else {
            ImGui.setMouseCursor(ImGuiMouseCursor.Arrow);
        }
    }

    private void onClipClicked(TimelineHitResult hit,
                               float mouseX,
                               float contentX,
                               boolean isDoubleClick) {
        TimelineTrack track = hit.track();
        TimelineClip clip = hit.clip();
        if (track == null || clip == null) return;

        // �?轨道锁定功能：如果轨道锁定，禁止所有操�?
        if (track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止操作片段: {} (轨道: {})", 
                    clip.getLabel(), track.getName());
            }
            return;
        }

        // 更新选中
        model.setSelectedClip(clip);
        
        // �?功能：选中片段时，同步�?SelectionManager，使属性面板显示片段属�?
        com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(clip);
        
        if (callbacks != null) callbacks.onDataChanged();
        
        // �?优化：添加日志，便于调试
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Timeline] 点击片段: {} (时间: {:.2f}s - {:.2f}s)", 
                clip.getLabel(), clip.getStartTime(), clip.getEndTime());
        }

        // �?优化：判断是否靠近左右边�?�?Resize
        // 使用更大的检测范围，使边缘拖拽更容易触发
        float startX = renderer.timeToX(clip.getStartTime(), contentX);
        float endX = renderer.timeToX(clip.getEndTime(), contentX);

        boolean nearLeft = Math.abs(mouseX - startX) <= CLIP_EDGE_RESIZE_PIXEL;
        boolean nearRight = Math.abs(mouseX - endX) <= CLIP_EDGE_RESIZE_PIXEL;
        
        // �?优化：如果片段很小，优先检测边缘（避免小片段无法调整）
        float clipWidth = endX - startX;
        if (clipWidth < CLIP_EDGE_RESIZE_PIXEL * 3) {
            // 对于很小的片段，扩大边缘检测范�?
            nearLeft = mouseX <= startX + CLIP_EDGE_RESIZE_PIXEL * 1.5f;
            nearRight = mouseX >= endX - CLIP_EDGE_RESIZE_PIXEL * 1.5f;
        }

        if (nearLeft) {
            interactionManager.startInteraction(
                    InteractionMode.RESIZE_CLIP_LEFT,
                    new ClipHandle(track, clip),
                    mouseX, ImGui.getMousePosY(),
                    clip.getStartTime(), clip.getEndTime()
            );
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 开始调整片段左边缘: {}", clip.getLabel());
            }
        } else if (nearRight) {
            interactionManager.startInteraction(
                    InteractionMode.RESIZE_CLIP_RIGHT,
                    new ClipHandle(track, clip),
                    mouseX, ImGui.getMousePosY(),
                    clip.getStartTime(), clip.getEndTime()
            );
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 开始调整片段右边缘: {}", clip.getLabel());
            }
        } else {
            // 普通拖�?
            interactionManager.startInteraction(
                    InteractionMode.DRAG_CLIP,
                    new ClipHandle(track, clip),
                    mouseX, ImGui.getMousePosY(),
                    clip.getStartTime(), clip.getEndTime()
            );
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 开始拖动片�? {} (�?{:.2f}s)", 
                    clip.getLabel(), clip.getStartTime());
            }
        }
    }

    private void onKeyframeClicked(TimelineHitResult hit,
                                   float mouseX,
                                   float contentX,
                                   boolean isDoubleClick) {
        TimelineTrack track = hit.track();
        TimelineClip clip = hit.clip();
        if (track == null || clip == null) return;

        // �?轨道锁定功能：如果轨道锁定，禁止关键帧操�?
        if (track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止操作关键�?(轨道: {})", track.getName());
            }
            return;
        }

        float keyTime = hit.time();

        // 双击：交�?Panel 处理添加/删除逻辑
        if (isDoubleClick && callbacks != null) {
            callbacks.onKeyframeDoubleClick(clip, mouseX, contentX);
            return;
        }

        // 单击：开始拖动关键帧
        KeyframeHandle handle = new KeyframeHandle(track, clip, keyTime);
        interactionManager.startInteraction(
                InteractionMode.DRAG_KEYFRAME,
                handle,
                mouseX, ImGui.getMousePosY(),
                keyTime, 0f
        );
    }

    /**
     * 处理自动化关键帧的点击（轨道级别的关键帧，如摄像机关键帧�?
     */
    private void onAutomationKeyframeClicked(TimelineHitResult hit,
                                            float mouseX,
                                            float contentX,
                                            boolean isDoubleClick) {
        TimelineTrack track = hit.track();
        com.chronoblocks.ui.timeline.model.TimelineSelectable keyframe = hit.automationKeyframe();
        
        if (track == null || keyframe == null) {
            return;
        }

        // �?轨道锁定功能：如果轨道锁定，禁止关键帧操�?
        if (track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止操作自动化关键帧 (轨道: {})", track.getName());
            }
            return;
        }

        // �?功能：选中自动化关键帧时，同步�?SelectionManager，使属性面板显示关键帧属�?
        com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(keyframe);
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Timeline] 点击自动化关键帧: {} (时间: {:.2f}s)", 
                keyframe.getName(), keyframe.getStartTime());
        }

        // ⭐ 如果是摄像机关键帧，单击时预览该机位
        if (keyframe instanceof com.chronoblocks.ui.timeline.model.camera.CameraKeyframe cameraKeyframe) {
            float timeSeconds = cameraKeyframe.getTime();
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().previewAt(timeSeconds);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 预览摄像机关键帧 @ {:.2f}s", timeSeconds);
            }
        }

        // 双击：可以在这里添加删除逻辑�?
        if (isDoubleClick && callbacks != null) {
            // TODO: 可以添加双击删除关键帧的逻辑
            LOGGER.debug("[Timeline] 双击自动化关键帧，但当前未实现删除功能");
        }

        // 单击：可以选择性地开始拖动关键帧（如果需要）
        // 注意：自动化关键帧的拖动逻辑可能与片段内关键帧不�?
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }

    private void onEmptyClicked(TimelineHitResult hit,
                                float mouseX,
                                float contentX) {
        // 空白区域：不移动播放头，允许用户在轨道上操作片段或关键帧
        // 只有点击顶部时间刻度尺时才会移动播放头（�?TimelinePanel.handleRulerInteractions 处理�?
    }

    // ========================================================
    // 鍙抽敭鑿滃崟
    // ========================================================

    private void openContextMenuForHit(TimelineHitResult hit) {
        System.out.println("[TimelineInteractionHandler] openContextMenuForHit 被调用，hit = " + 
                (hit != null ? hit.type() : "null"));
        
        if (menuFactory == null) {
            System.out.println("[TimelineInteractionHandler] ERROR: menuFactory 为 null");
            LOGGER.warn("[Timeline] menuFactory 为 null，无法打开上下文菜单");
            return;
        }

        if (hit == null) {
            System.out.println("[TimelineInteractionHandler] ERROR: hit 为 null");
            LOGGER.warn("[Timeline] hit 为 null，无法打开上下文菜单");
            return;
        }

        System.out.println("[TimelineInteractionHandler] 尝试打开上下文菜单，hit.type() = " + hit.type() + 
                ", track = " + hit.track() + ", clip = " + hit.clip());
        LOGGER.info("[Timeline] 尝试打开上下文菜单，hit.type() = {}, track = {}, clip = {}", 
                hit.type(), hit.track(), hit.clip());

        activeContextMenu = menuFactory.buildContextMenu(hit);
        if (activeContextMenu != null) {
            activeContextMenu.open();
            System.out.println("[TimelineInteractionHandler] 成功打开上下文菜�? " + hit.type());
            LOGGER.info("[Timeline] 成功打开上下文菜�? {}", hit.type());
        } else {
            System.out.println("[TimelineInteractionHandler] ERROR: buildContextMenu 返回 null，hit.type() = " + hit.type());
            LOGGER.warn("[Timeline] buildContextMenu 返回 null，hit.type() = {}", hit.type());
        }
    }

    /**
     * 结束交互（鼠标释放时调用�?
     * 
     * <p>�?优化：为所有操作添�?Undo 支持，使用命令模�?
     */
    private void endInteractionIfNeeded() {
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode == InteractionMode.NONE) return;

        Object obj = interactionManager.getActiveObject();

        if (mode == InteractionMode.DRAG_CLIP) {
            // 拖动片段：使�?MoveClipCommand（已�?onClipInteractionFinished 中处理）
            if (obj instanceof ClipHandle handle && callbacks != null) {
                callbacks.onClipInteractionFinished(
                        handle.clip,
                        interactionManager.getDragStartValue(),
                        interactionManager.getDragStartValue2()
                );
            }
        } else if (mode == InteractionMode.RESIZE_CLIP_LEFT) {
            // �?优化：调整左边缘，使�?ResizeClipCommand 支持 Undo
            if (obj instanceof ClipHandle handle) {
                float originalStart = interactionManager.getDragStartValue();
                float currentStart = handle.clip.getStartTime();
                
                // 只有当位置真正改变时才创建命�?
                if (Math.abs(currentStart - originalStart) > 0.001f) {
                    commandHistory.executeCommand(
                        new ResizeClipCommand(model, handle.clip, ResizeClipCommand.Edge.START, currentStart)
                    );
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] 调整片段左边�? {}s -> {}s (片段: {})", 
                            originalStart, currentStart, handle.clip.getLabel());
                    }
                }
                
                if (callbacks != null) callbacks.onDataChanged();
            }
        } else if (mode == InteractionMode.RESIZE_CLIP_RIGHT) {
            // �?优化：调整右边缘，使�?ResizeClipCommand 支持 Undo
            if (obj instanceof ClipHandle handle) {
                float originalEnd = interactionManager.getDragStartValue2();
                float currentEnd = handle.clip.getEndTime();
                
                // 只有当位置真正改变时才创建命�?
                if (Math.abs(currentEnd - originalEnd) > 0.001f) {
                    commandHistory.executeCommand(
                        new ResizeClipCommand(model, handle.clip, ResizeClipCommand.Edge.END, currentEnd)
                    );
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] 调整片段右边�? {}s -> {}s (片段: {})", 
                            originalEnd, currentEnd, handle.clip.getLabel());
                    }
                }
                
                if (callbacks != null) callbacks.onDataChanged();
            }
        } else if (mode == InteractionMode.DRAG_KEYFRAME) {
            if (obj instanceof KeyframeHandle handle) {
                float originalTime = interactionManager.getDragStartValue();
                float finalTime = handle.currentTime;

                handle.clip.removeKeyframe(handle.currentTime);
                handle.clip.addKeyframe(originalTime);

                commandHistory.executeCommand(new RemoveKeyframeCommand(model, handle.clip, originalTime));
                commandHistory.executeCommand(new AddKeyframeCommand(model, handle.clip, finalTime));

                if (callbacks != null) callbacks.onDataChanged();

                LOGGER.debug("[Timeline] 关键帧拖动完�? {} -> {}", originalTime, finalTime);
            }
        }

        
        else if (mode == InteractionMode.DRAG_PLAYHEAD) {
            // ⭐ 拖动播放头结束：通知摄像机控制器结束 scrubbing
            float finalTime = model.getCurrentTime();
            model.setScrubbing(false);
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().endScrubbing(finalTime);
            LOGGER.debug("[TimelineInteractionHandler] 拖动播放头结束 @ {:.2f}s", finalTime);
        }

        interactionManager.endInteraction();
    }

    // ========== 拖动处理（帧更新�?=========

    private void handleDrag(TimelineHitResult hit,
                            float contentX,
                            float trackAreaTop,
                            float contentW,
                            float viewportH,
                            float scrollY) {
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) return;
        if (!interactionManager.isInteracting()) return;

        InteractionMode mode = interactionManager.getCurrentMode();
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();

        switch (mode) {
            case DRAG_CLIP -> dragClip(mouseX, contentX, mouseY, trackAreaTop);
            case RESIZE_CLIP_LEFT -> resizeClipLeft(mouseX, contentX);
            case RESIZE_CLIP_RIGHT -> resizeClipRight(mouseX, contentX);
            case DRAG_KEYFRAME -> dragKeyframe(mouseX, contentX);
            case DRAG_PLAYHEAD -> dragPlayhead(mouseX, contentX);
            default -> { /* 其他模式暂不处理 */ }
        }
    }

    /**
     * 拖动片段（移动位置，支持跨轨道）
     * 
     * <p>�?修复：确保跨轨道拖动时，X轴（时间）和Y轴（轨道）都能正常工�?
     * 
     * @param mouseX 鼠标 X 坐标
     * @param contentX 内容区域 X 起点
     * @param mouseY 鼠标 Y 坐标（用于跨轨道检测）
     * @param trackAreaTop 轨道区域顶部 Y 坐标（用于跨轨道检测）
     */
    private void dragClip(float mouseX, float contentX, float mouseY, float trackAreaTop) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof ClipHandle handle)) {
            return;
        }

        // �?轨道锁定功能：如果轨道锁定，禁止拖动
        TimelineTrack currentTrack = model.findTrackForClip(handle.clip);
        if (currentTrack == null) {
            currentTrack = handle.track;
        }
        if (currentTrack != null && currentTrack.isLocked()) {
            // 如果轨道被锁定，结束交互
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动片段 (轨道: {})", currentTrack.getName());
            }
            return;
        }

        // �?优化：确保片段被选中，这样拖动后可以继续拖动
        if (model.getSelectedClip() != handle.clip) {
            model.setSelectedClip(handle.clip);
        }

        float start0 = interactionManager.getDragStartValue();
        float end0 = interactionManager.getDragStartValue2();
        float length = end0 - start0;

        // �?修复：先计算时间变化（X轴拖动）
        float startMouseX = interactionManager.getDragStartMouseX();
        float startMouseTime = renderer.xToTime(startMouseX, contentX);
        float currentMouseTime = renderer.xToTime(mouseX, contentX);
        float delta = currentMouseTime - startMouseTime;

        float candidateStart = start0 + delta;
        candidateStart = Math.max(0f, candidateStart);

        // �?修复：支持跨轨道拖动（Y轴拖动）
        // 先检测目标轨道，再使用正确的轨道进行吸附计算
        // �?关键修复：使用片段当前所在的轨道，而不�?handle.track（可能已过时�?
        if (currentTrack == null) {
            // 回退到原始轨�?
        }
        TimelineTrack targetTrack = currentTrack;
        
        if (mouseY > 0 && trackAreaTop > 0) {
            float scrollY = model.getViewportState().getScrollY();
            float relativeY = mouseY - trackAreaTop;
            int newTrackIndex = renderer.yToTrackIndex(relativeY, scrollY);
            if (newTrackIndex >= 0 && newTrackIndex < model.getTrackCount()) {
                TimelineTrack newTrack = model.getTrackAt(newTrackIndex);
                // �?关键修复：只在目标轨道与当前轨道不同时才移动，避免重复移�?
                if (newTrack != null && newTrack != currentTrack && !newTrack.isLocked()) {
                    // 检查轨道类型兼容性（可选，根据业务需求）
                    // 如果允许跨轨道，则移动片�?

                    // �?关键修复：确保片段从旧轨道移除（即使它可能已经在其他轨道上）
                    if (currentTrack != null) {
                        currentTrack.removeClip(handle.clip);
                    }
                    
                    // �?关键修复：确保片段不在新轨道上才添加（避免重复添加）
                    if (!newTrack.getClips().contains(handle.clip)) {
                        newTrack.addClip(handle.clip);
                    }
                    
                    targetTrack = newTrack; // 更新目标轨道用于吸附计算
                    
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] 片段跨轨道移�? {} -> {} (片段: {})", 
                            currentTrack != null ? currentTrack.getName() : "null",
                            newTrack.getName(), handle.clip.getLabel());
                    }
                }
            }
        }

        // �?修复：使用目标轨道进行吸附计算，确保跨轨道拖动时吸附正确
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, candidateStart, targetTrack, handle.clip, 0f
        );
        float snappedStart = Math.max(0f, snap.snappedTime);
        float snappedEnd = snappedStart + length;

        // �?修复：确保片段时长保持不变，只移动位置（X轴和Y轴都更新�?
        handle.clip.setStartTime(snappedStart);
        handle.clip.setEndTime(snappedEnd);
        model.markDurationDirty();

        if (callbacks != null) callbacks.onDataChanged();
    }

    /**
     * 调整片段左边缘（调整开始时间和时长�?
     * 
     * <p>�?优化：确保调整后片段可以被继续调整，不会被固�?
     */
    private void resizeClipLeft(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof ClipHandle handle)) {
            return;
        }

        // �?轨道锁定功能：如果轨道锁定，禁止调整宽度
        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止调整片段宽度 (轨道: {})", handle.track.getName());
            }
            return;
        }

        // �?优化：确保片段被选中
        if (model.getSelectedClip() != handle.clip) {
            model.setSelectedClip(handle.clip);
        }

        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float minLength = MIN_CLIP_LENGTH;

        float startMouseX = interactionManager.getDragStartMouseX();
        float startMouseTime = renderer.xToTime(startMouseX, contentX);
        float currentMouseTime = renderer.xToTime(mouseX, contentX);
        float delta = currentMouseTime - startMouseTime;

        float candidateStart = originalStart + delta;
        candidateStart = Math.min(candidateStart, originalEnd - minLength);
        candidateStart = Math.max(0f, candidateStart);

        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, candidateStart, handle.track, handle.clip, 0f
        );
        float snappedStart = Math.min(snap.snappedTime, handle.clip.getEndTime() - minLength);
        snappedStart = Math.max(0f, snappedStart);

        // �?优化：实时更新片段开始时间，保持结束时间不变（调整时长）
        handle.clip.setStartTime(snappedStart);
        model.markDurationDirty();

        if (callbacks != null) callbacks.onDataChanged();
    }

    /**
     * 调整片段右边缘（调整结束时间和时长）
     * 
     * <p>�?优化：确保调整后片段可以被继续调整，不会被固�?
     */
    private void resizeClipRight(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof ClipHandle handle)) {
            return;
        }

        // �?轨道锁定功能：如果轨道锁定，禁止调整宽度
        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止调整片段宽度 (轨道: {})", handle.track.getName());
            }
            return;
        }

        // �?优化：确保片段被选中
        if (model.getSelectedClip() != handle.clip) {
            model.setSelectedClip(handle.clip);
        }

        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float minLength = MIN_CLIP_LENGTH;

        float startMouseX = interactionManager.getDragStartMouseX();
        float startMouseTime = renderer.xToTime(startMouseX, contentX);
        float currentMouseTime = renderer.xToTime(mouseX, contentX);
        float delta = currentMouseTime - startMouseTime;

        float candidateEnd = originalEnd + delta;
        candidateEnd = Math.max(candidateEnd, originalStart + minLength);

        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, candidateEnd, handle.track, handle.clip, 0f
        );
        float snappedEnd = Math.max(snap.snappedTime, handle.clip.getStartTime() + minLength);

        // �?优化：实时更新片段结束时间，保持开始时间不变（调整时长�?
        handle.clip.setEndTime(snappedEnd);
        model.markDurationDirty();

        if (callbacks != null) callbacks.onDataChanged();
    }

    private void dragKeyframe(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof KeyframeHandle handle)) {
            return;
        }

        // �?轨道锁定功能：如果轨道锁定，禁止拖动关键�?
        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动关键�?(轨道: {})", handle.track.getName());
            }
            return;
        }

        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, handle.track, handle.clip, 0f
        );
        float newTime = snap.snappedTime;

        // 限制�?clip 范围�?
        float eps = 0.001f;
        newTime = Math.max(handle.clip.getStartTime() + eps, newTime);
        newTime = Math.min(handle.clip.getEndTime() - eps, newTime);

        if (Math.abs(newTime - handle.currentTime) < eps) {
            return;
        }

        // 实时预览：直接修�?clip �?keyframes（先删旧后加新）
        handle.clip.removeKeyframe(handle.currentTime);
        handle.clip.addKeyframe(newTime);
        handle.currentTime = newTime;

        if (callbacks != null) callbacks.onDataChanged();
    }

    /**
     * 开始播放头拖动（由 TimelinePanel 调用，用于刻度尺交互�?
     * 
     * @param mouseX 鼠标 X 坐标
     * @param contentX 内容区域 X 起点
     */
    public void startPlayheadDrag(float mouseX, float contentX) {
        // 先更新播放头位置（如果还没有在正确位置）
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, null, null, 0f
        );
        float clampedTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));
        model.setCurrentTime(clampedTime);
        model.setScrubbing(true);
        
        // �?功能：开始拖动播放头时，立即更新片段方块显示状�?
        com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
        
        // 开始拖动交�?
        interactionManager.startInteraction(
                InteractionMode.DRAG_PLAYHEAD,
                null,
                mouseX, ImGui.getMousePosY(),
                clampedTime, 0f
        );
        // ⭐ 通知摄像机控制器开始 scrubbing
        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().beginScrubbing(clampedTime);
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
        
        LOGGER.debug("[TimelineInteractionHandler] 开始拖动播放头 @ {:.2f}s", clampedTime);
    }
    
    /**
     * ⭐ 点击时间标尺（只跳转时间，不进入 scrubbing）
     * 由 TimelinePanel 在检测到点击时间标尺时调用
     * 
     * @param mouseX 鼠标 X 坐标
     * @param contentX 内容区域 X 起点
     */
    public void clickTimeRuler(float mouseX, float contentX) {
        // 计算时间并应用吸附
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, null, null, 0f
        );
        float clampedTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));
        
        // 只更新时间，不进入 scrubbing
        model.setCurrentTime(clampedTime);
        
        // 点击时间刻度时跳转镜头到该时间并保持玩家态，便于继续微调
        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().jumpToTimeAsPlayer(clampedTime);
        
        // 更新片段方块显示状态
        com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
        
        LOGGER.debug("[TimelineInteractionHandler] 点击时间标尺，跳转到 {:.2f}s", clampedTime);
    }

    private void dragPlayhead(float mouseX, float contentX) {
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, null, null, 0f
        );
        float clampedTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));
        
        // 更新模型时间
        model.setCurrentTime(clampedTime);
        
        // ⭐ 通知摄像机控制器更新 scrubbing 位置
        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().updateScrubbing(clampedTime);
        
        // �?功能：播放头拖动时，立即更新片段方块显示状�?
        com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
        
        if (callbacks != null) callbacks.onDataChanged();
    }

    // ========== 小型 Handle 类型 ==========

    private static final class ClipHandle {
        final TimelineTrack track;
        final TimelineClip clip;

        ClipHandle(TimelineTrack track, TimelineClip clip) {
            this.track = track;
            this.clip = clip;
        }
    }

    private static final class KeyframeHandle {
        final TimelineTrack track;
        final TimelineClip clip;
        float currentTime;

        KeyframeHandle(TimelineTrack track, TimelineClip clip, float time) {
            this.track = track;
            this.clip = clip;
            this.currentTime = time;
        }
    }
}