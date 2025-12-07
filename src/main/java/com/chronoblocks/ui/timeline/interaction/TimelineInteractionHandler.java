package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.ui.timeline.commands.ResizeClipCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.chronoblocks.ui.timeline.interaction.TimelineTrackHitTest.TrackControlHitResult;
import com.chronoblocks.ui.timeline.menu.TimelineContextMenu;
import com.chronoblocks.ui.timeline.menu.TimelineContextMenuFactory;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.render.TimelineRenderer;
import com.chronoblocks.ui.timeline.render.TimelineTrackControlHit;
import com.chronoblocks.ui.timeline.snapping.TimelineSnapping;
import com.chronoblocks.ui.timeline.commands.MoveAutomationKeyframeCommand;

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
     * ⭐ 修复：检查并清理已关闭的菜单
     * 应该在每帧渲染时调用，确保菜单状态正确
     */
    public void updateMenuState() {
        if (activeContextMenu != null) {
            // 检查 popup 是否仍然打开
            String popupId = activeContextMenu.getPopupId();
            if (popupId != null && !imgui.ImGui.isPopupOpen(popupId, imgui.flag.ImGuiPopupFlags.None)) {
                // Popup 已关闭，清理引用
                activeContextMenu = null;
                LOGGER.debug("[Timeline] 菜单已关闭，清理引用");
            }
        }
    }
    
    /**
     * 获取正在拖动中的自动化关键帧的临时时间（用于渲染系统）
     * 
     * <p>在拖动过程中，关键帧的实际时间不会改变，但 UI 需要显示拖动中的位置。
     * 此方法返回拖动中的临时时间，如果没有拖动则返回 -1。
     * 
     * @param keyframe 关键帧
     * @return 临时时间，如果没有拖动则返回 -1
     */
    public float getDraggedAutomationKeyframeTime(TimelineSelectable keyframe) {
        if (keyframe == null) {
            return -1f;
        }
        
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode == InteractionMode.DRAG_AUTOMATION_KEYFRAME) {
            Object active = interactionManager.getActiveObject();
            if (active instanceof AutomationKeyframeHandle handle) {
                if (handle.keyframe == keyframe || handle.keyframe.getId().equals(keyframe.getId())) {
                    return handle.currentTime;
                }
            }
        }
        
        return -1f;
    }
    
    /**
     * 获取正在拖动中的 Clip 关键帧的临时时间（用于渲染系统）
     * 
     * <p>在拖动过程中，关键帧的实际时间不会改变，但 UI 需要显示拖动中的位置。
     * 此方法返回拖动中的临时时间，如果没有拖动则返回 -1。
     * 
     * @param clip 片段
     * @param keyframeTime 关键帧的原始时间
     * @return 临时时间，如果没有拖动则返回 -1
     */
    public float getDraggedClipKeyframeTime(TimelineClip clip, float keyframeTime) {
        if (clip == null) {
            return -1f;
        }
        
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode == InteractionMode.DRAG_KEYFRAME) {
            Object active = interactionManager.getActiveObject();
            if (active instanceof KeyframeHandle handle) {
                // 检查是否是同一个 clip
                if (handle.clip == clip) {
                    // 检查是否是同一个关键帧
                    // 使用拖动开始时的原始时间来匹配
                    float originalTime = interactionManager.getDragStartValue();
                    if (Math.abs(originalTime - keyframeTime) < 0.001f) {
                        return handle.currentTime; // 返回拖动中的临时时间
                    }
                }
            }
        }
        
        return -1f;
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
        
        // ⭐ v5.0：显示轨道控制按钮的 tooltip
        renderTrackControlTooltips(trackControlHit);
        
        // 注意：容器拖放处理现在在 TimelinePanel 中直接处理（�?invisibleButton 之后�?
        // 这样可以确保在正确的窗口上下文中设置拖放目标
    }
    
    /**
     * 为轨道控制按钮显示 tooltip（激活摄像机、关键帧、锁定）
     */
    private void renderTrackControlTooltips(TrackControlHitResult trackControlHit) {
        if (trackControlHit == null || !trackControlHit.isHit()) {
            return;
        }
        
        TimelineTrack track = trackControlHit.track();
        if (track == null) {
            return;
        }
        
        TimelineTrackControlHit.TrackControlType type = trackControlHit.type();
        String tooltip = null;
        
        switch (type) {
            case ACTIVE_CAMERA -> {
                com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
                boolean isActive = controller.getActiveCameraTrack() == track;
                tooltip = isActive ? "当前激活的摄像机轨道（点击切换到其他轨道）" : "设为当前激活的摄像机轨道";
            }
            case ACTIVE_TIME -> {
                com.chronoblocks.ui.timeline.playback.time.TimelineWorldController controller =
                        com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance();
                boolean isActive = controller.getActiveTimeTrack() == track;
                tooltip = isActive ? "当前激活的时间轨道（点击切换到其他轨道）" : "设为当前激活的时间轨道";
            }
            case ACTIVE_SEASON -> {
                com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController controller =
                        com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance();
                boolean isActive = controller.getActiveSeasonTrack() == track;
                tooltip = isActive ? "当前激活的季节轨道（点击切换到其他轨道）" : "设为当前激活的季节轨道";
            }
            case ADD_KEYFRAME -> {
                if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
                    tooltip = "添加摄像机关键帧（仅激活轨道可用）";
                } else {
                    tooltip = "添加关键帧";
                }
            }
            case LOCK -> tooltip = track.isLocked() ? "解锁轨道" : "锁定轨道";
            case VISIBILITY -> tooltip = track.isVisible() ? "隐藏轨道" : "显示轨道";
        }
        
        if (tooltip != null) {
            ImGui.setTooltip(tooltip);
        }
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
                    case ACTIVE_CAMERA -> // ⭐ v5.0：设置激活摄像机轨道
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance()
                            .setActiveCameraTrack(track);
            case ACTIVE_TIME -> // ⭐ v3.5：设置激活时间轨道
                    com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance()
                            .setActiveTimeTrack(track);
            case ACTIVE_SEASON -> // ⭐ v1.1：设置激活季节轨道
                    com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance()
                            .setActiveSeasonTrack(track);
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
        // 右键：统一使用 ImGui 的点击检测
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            // ⭐⭐ 修复 F：右键按下后阻止拖动行为
            interactionManager.cancelAllInteractionsSilently();
            
            LOGGER.info("[Timeline] 右键点击: hit.type = {}, rightClickedOnCanvas = {}, trackControlHit = {}",
                    hit != null ? hit.type() : "null",
                    rightClickedOnCanvas,
                    (trackControlHit != null && trackControlHit.isHit()));

            // 1) 右键点击在轨道头控件上：预留给轨道头菜单（暂时拦截，不弹其他菜单）
            if (trackControlHit != null && trackControlHit.isHit()) {
                LOGGER.info("[Timeline] 右键点击被轨道头控件拦截");
                // 未来可以在这里加"轨道菜单"（静音、复制轨道等）
                return;
            }

            // 2) 命中 Clip / Keyframe / AutomationKeyframe：无论 rightClickedOnCanvas 是否为 true，都打开对应元素菜单
            if (hit != null && (hit.type() == TimelineHitResult.HitType.KEYFRAME
                             || hit.type() == TimelineHitResult.HitType.AUTOMATION_KEYFRAME
                             || hit.type() == TimelineHitResult.HitType.CLIP)) {
                openContextMenuForHit(hit);
            }
            // 3) 命中空白区域：只有在 rightClickedOnCanvas = true 时才弹"空白菜单"
            else if (rightClickedOnCanvas) {
                openContextMenuForHit(hit);
            }
            // 4) 其他情况：不弹菜单，但输出一点调试日志
            else {
                LOGGER.debug("[Timeline] 右键点击未匹配任何菜单区域 (hit = {}, rightClickedOnCanvas = {})",
                        hit != null ? hit.type() : "null", false);
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

        // ⭐ 从 Clip 中查找真正的 ClipKeyframe 对象
        final float EPS = 0.001f;
        com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf =
                clip.findKeyframeAt(keyTime, EPS);
        
        if (kf == null) {
            LOGGER.warn("[Timeline] onKeyframeClicked: 未找到关键帧对象 @ {}s", keyTime);
            return;
        }

        // 单击：选中关键帧
        imgui.ImGuiIO io = imgui.ImGui.getIO();
        boolean addToSelection = io.getKeyCtrl();
        model.selectItem(kf, addToSelection);
        com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(kf);

        // 启动拖动交互（交给 MoveClipKeyframeCommand 在结束时记录 Undo）
        KeyframeHandle handle = new KeyframeHandle(track, clip, kf, kf.getTime());
        interactionManager.startInteraction(
                InteractionMode.DRAG_KEYFRAME,
                handle,
                mouseX, ImGui.getMousePosY(),
                kf.getTime(), 0f
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
        // 双击：可以在这里添加删除逻辑
        if (isDoubleClick && callbacks != null) {
            // TODO: 可以添加双击删除关键帧的逻辑
            LOGGER.debug("[Timeline] 双击自动化关键帧，但当前未实现删除功能");
            return;
        }

        // ⭐ v5.0：如果是摄像机轨道，检查是否是激活的轨道
        if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
            if (activeTrack != track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止操作关键帧 (轨道: {})", track.getName());
                }
                return;
            }
        }
        
        // ⭐ v3.5：如果是时间轨道，检查是否是激活的轨道
        if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.TIME) {
            com.chronoblocks.ui.timeline.playback.time.TimelineWorldController controller =
                    com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveTimeTrack();
            
            if (activeTrack != track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 时间轨道未激活，禁止操作关键帧 (轨道: {})", track.getName());
                }
                return;
            }
        }
        
        // ⭐ v1.1：如果是季节轨道，检查是否是激活的轨道
        if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.SEASON) {
            com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController controller =
                    com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveSeasonTrack();
            
            if (activeTrack != track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 季节轨道未激活，禁止操作关键帧 (轨道: {})", track.getName());
                }
                return;
            }
        }

        // ⭐ 单击：选中关键帧（支持 Ctrl 多选）
        ImGuiIO io = ImGui.getIO();
        boolean addToSelection = io.getKeyCtrl();
        model.selectItem(keyframe, addToSelection);
        
        // 同步到 SelectionManager，使属性面板显示关键帧属性
        com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(keyframe);
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Timeline] 点击自动化关键帧: {} (时间: {:.2f}s, 多选: {})", 
                keyframe.getName(), keyframe.getStartTime(), addToSelection);
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
        // ⭐ 单击：开始拖动自动化关键帧
        AutomationKeyframeHandle handle = new AutomationKeyframeHandle(track, keyframe);
        float keyTime = keyframe.getStartTime();
        interactionManager.startInteraction(
                InteractionMode.DRAG_AUTOMATION_KEYFRAME,
                handle,
                mouseX, ImGui.getMousePosY(),
                keyTime, 0f
        );
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }

    private void onEmptyClicked(TimelineHitResult hit,
                                float mouseX,
                                float contentX) {
        // 空白区域：清除选中状态
        model.clearSelection();
        
        // 清除 SelectionManager 的选择，使属性面板恢复到默认状态
        com.chronoblocks.ui.selection.SelectionManager.get().clearSelection();
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Timeline] 点击空白区域，清除所有选中状态");
        }
        
        // 空白区域：不移动播放头，允许用户在轨道上操作片段或关键帧
        // 只有点击顶部时间刻度尺时才会移动播放头（�?TimelinePanel.handleRulerInteractions 处理�?
    }

    // ========================================================
    // 鍙抽敭鑿滃崟
    // ========================================================

    private void openContextMenuForHit(TimelineHitResult hit) {
        if (menuFactory == null) {
            LOGGER.warn("[Timeline] menuFactory 为 null，无法打开上下文菜单");
            return;
        }

        if (hit == null) {
            LOGGER.warn("[Timeline] hit 为 null，无法打开上下文菜单");
            return;
        }

        // ⭐ 右键时，先做"自动选中"，保证菜单操作对象和属性面板对象一致
        switch (hit.type()) {
            case CLIP -> {
                TimelineClip clip = hit.clip();
                if (clip != null) {
                    model.setSelectedClip(clip);
                    // 同步 SelectionManager
                    com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(clip);
                }
            }
            case KEYFRAME -> {
                // ⭐⭐ 修复：正确选择 ClipKeyframe 对象
                com.chronoblocks.ui.timeline.model.clip.ClipKeyframe clipKeyframe = hit.clipKeyframe();
                if (clipKeyframe != null) {
                    ImGuiIO io = ImGui.getIO();
                    boolean addToSelection = io.getKeyCtrl();
                    model.selectItem(clipKeyframe, addToSelection);
                    com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(clipKeyframe);
                    LOGGER.debug("[Timeline] 右键选择 ClipKeyframe @ {}s", clipKeyframe.getTime());
                } else {
                    // 回退：如果没有 ClipKeyframe 对象，选择 clip
                    TimelineClip clip = hit.clip();
                    if (clip != null) {
                        model.setSelectedClip(clip);
                        com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(clip);
                    }
                }
            }
            case AUTOMATION_KEYFRAME -> {
                TimelineSelectable keyframe = hit.automationKeyframe();
                TimelineTrack track = hit.track();
                
                if (keyframe != null && track != null) {
                    // ⭐ v5.0：检查轨道锁定和激活状态
                    if (track.isLocked()) {
                        LOGGER.debug("[Timeline] 轨道已锁定，禁止右键操作关键帧 (轨道: {})", track.getName());
                        return; // 阻止打开菜单
                    }
                    
                    // 如果是摄像机轨道，检查是否是激活的轨道
                    if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
                        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                                com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
                        com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
                        
                        if (activeTrack != track) {
                            LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止右键操作关键帧 (轨道: {})", track.getName());
                            return; // 阻止打开菜单
                        }
                    }
                    
                    // 如果是时间轨道，检查是否是激活的轨道
                    if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.TIME) {
                        com.chronoblocks.ui.timeline.playback.time.TimelineWorldController controller =
                                com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance();
                        com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveTimeTrack();
                        
                        if (activeTrack != track) {
                            LOGGER.debug("[Timeline] 时间轨道未激活，禁止右键操作关键帧 (轨道: {})", track.getName());
                            return; // 阻止打开菜单
                        }
                    }
                    
                    // 如果是季节轨道，检查是否是激活的轨道
                    if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.SEASON) {
                        com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController controller =
                                com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance();
                        com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveSeasonTrack();
                        
                        if (activeTrack != track) {
                            LOGGER.debug("[Timeline] 季节轨道未激活，禁止右键操作关键帧 (轨道: {})", track.getName());
                            return; // 阻止打开菜单
                        }
                    }
                    
                    // TimelineModel 内部选中
                    ImGuiIO io = ImGui.getIO();
                    boolean addToSelection = io.getKeyCtrl(); // 右键支持 Ctrl 多选也合理
                    model.selectItem(keyframe, addToSelection);
                    // 同步 SelectionManager → 属性面板显示具体的 CameraKeyframe 属性
                    com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(keyframe);
                    if (keyframe instanceof com.chronoblocks.ui.timeline.model.camera.CameraKeyframe ck) {
                        LOGGER.debug("[Timeline] 右键摄像机关键帧 @ {}s，准备弹出关键帧菜单", ck.getTime());
                    }
                }
            }
            case EMPTY, NONE, TRACK_HEADER -> {
                // 空白区域右键：保持现有选择，很多软件右键空白也不清空
            }
        }

        LOGGER.info("[Timeline] 尝试打开上下文菜单，hit.type = {}, track = {}, clip = {}",
                hit.type(), hit.track(), hit.clip());

        activeContextMenu = menuFactory.buildContextMenu(hit);
        if (activeContextMenu != null) {
            activeContextMenu.open();
            System.out.println("[TimelineInteractionHandler] 成功打开上下文菜�? " + hit.type());
            LOGGER.info("[Timeline] 成功打开上下文菜�? {}", hit.type());
        } else {
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

                // ⭐⭐ 修复：使用 MoveClipKeyframeCommand 而不是 Remove + Add
                // 这样确保 Undo/Redo 正确工作，且不会导致数据结构混乱
                commandHistory.executeCommand(
                    new com.chronoblocks.ui.timeline.commands.MoveClipKeyframeCommand(
                        model, handle.track, handle.clip, handle.keyframe, originalTime, finalTime)
                );

                if (callbacks != null) callbacks.onDataChanged();

                LOGGER.debug("[Timeline] 关键帧拖动完�? {} -> {}", originalTime, finalTime);
            }
        } else if (mode == InteractionMode.DRAG_AUTOMATION_KEYFRAME) {
            if (obj instanceof AutomationKeyframeHandle handle) {
                float originalTime = interactionManager.getDragStartValue();
                float finalTime = handle.currentTime;

                // ⭐⭐ 修复：改成单一命令处理
                // 所有数据结构修改都在这里执行一次，拖动过程中不会修改模型
                commandHistory.executeCommand(
                    new com.chronoblocks.ui.timeline.commands.MoveAutomationKeyframeCommand(
                        model, handle.track, handle.keyframe, originalTime, finalTime)
                );

                // MoveAutomationKeyframeCommand 内部已经调用了 onKeyframeChanged()，这里不需要再调用

                if (callbacks != null) callbacks.onDataChanged();

                LOGGER.debug("[Timeline] 自动化关键帧拖动完成 {} -> {}", originalTime, finalTime);
            }
        } else if (mode == InteractionMode.DRAG_PLAYHEAD) {
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
            case DRAG_AUTOMATION_KEYFRAME -> dragAutomationKeyframe(mouseX, contentX);
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
        // ⭐⭐ 只更新 Handle 的临时时间，不修改模型
        handle.currentTime = newTime;

        // ⭐⭐ 通知 UI 刷新（渲染系统会读取 handle.currentTime 来显示关键帧位置）
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }

    /**
     * 拖动自动化关键帧（轨道级别的关键帧，如摄像机关键帧）
     * 
     * <p>⭐ 修复：遵循专业软件（AE / Blender / Premiere）的拖动结构：
     * <ul>
     *   <li>在拖动过程中只更新 runtime 状态（handle.currentTime），不修改 model</li>
     *   <li>UI 上显示关键帧移动位置（通过渲染系统），但不改 model</li>
     *   <li>只做预览（TimelineCameraController.previewAt），不改变 track.keyframes 列表结构</li>
     *   <li>不改变 keyframe.time（只暂存到 handle.currentTime）</li>
     * </ul>
     * 
     * <p>所有数据结构修改（track.remove/add, keyframe.setTime）都留给
     * {@link MoveAutomationKeyframeCommand} 在鼠标松开时统一处理。
     */
    private void dragAutomationKeyframe(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof AutomationKeyframeHandle handle)) {
            return;
        }

        // 轨道锁定功能：如果轨道锁定，禁止拖动关键帧
        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动自动化关键帧(轨道: {})", handle.track.getName());
            }
            return;
        }

        // ⭐ v5.0：如果是摄像机轨道，检查是否是激活的轨道
        if (handle.track != null && handle.track.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
            if (activeTrack != handle.track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止拖动关键帧(轨道: {})", handle.track.getName());
                }
                return;
            }
        }
        
        // ⭐ v3.5：如果是时间轨道，检查是否是激活的轨道
        if (handle.track != null && handle.track.getType() == com.chronoblocks.ui.timeline.model.TrackType.TIME) {
            com.chronoblocks.ui.timeline.playback.time.TimelineWorldController controller =
                    com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveTimeTrack();
            
            if (activeTrack != handle.track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 时间轨道未激活，禁止拖动关键帧(轨道: {})", handle.track.getName());
                }
                return;
            }
        }
        
        // ⭐ v1.1：如果是季节轨道，检查是否是激活的轨道
        if (handle.track != null && handle.track.getType() == com.chronoblocks.ui.timeline.model.TrackType.SEASON) {
            com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController controller =
                    com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveSeasonTrack();
            
            if (activeTrack != handle.track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 季节轨道未激活，禁止拖动关键帧(轨道: {})", handle.track.getName());
                }
                return;
            }
        }

        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, handle.track, null, 0f
        );
        float newTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));

        // 若变化太小，则不更新
        if (Math.abs(newTime - handle.currentTime) < 0.0005f) {
            return;
        }

        // ⭐⭐ 只更新 Handle 的临时时间，不修改模型
        handle.currentTime = newTime;

        // ⭐⭐ UI 预览：摄像机预览但不修改模型
        if (handle.keyframe instanceof com.chronoblocks.ui.timeline.model.camera.CameraKeyframe) {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().previewAt(newTime);
        }

        // ⭐⭐ 通知 UI 刷新（渲染系统会读取 handle.currentTime 来显示关键帧位置）
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
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
        
        // ⭐ 季节轨道系统：点击时间刻度时更新季节采样
        com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance().onTimelineTick(clampedTime);
        
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
        
        // ⭐ 季节轨道系统：拖动播放头时更新季节采样
        com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance().onTimelineTick(clampedTime);
        
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
        final com.chronoblocks.ui.timeline.model.clip.ClipKeyframe keyframe;
        float currentTime;

        KeyframeHandle(TimelineTrack track,
                       TimelineClip clip,
                       com.chronoblocks.ui.timeline.model.clip.ClipKeyframe keyframe,
                       float time) {
            this.track = track;
            this.clip = clip;
            this.keyframe = keyframe;
            this.currentTime = time;
        }
    }

    private static final class AutomationKeyframeHandle {
        final TimelineTrack track;
        final TimelineSelectable keyframe;
        float currentTime;

        AutomationKeyframeHandle(TimelineTrack track, TimelineSelectable keyframe) {
            this.track = track;
            this.keyframe = keyframe;
            this.currentTime = keyframe.getStartTime();
        }
    }
}