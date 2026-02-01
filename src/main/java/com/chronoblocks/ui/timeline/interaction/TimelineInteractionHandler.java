package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRegistryManager;
import com.chronoblocks.actor.action.ActorActionType;
import com.chronoblocks.actor.rig.ActorRigProfile;
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
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip;
import com.chronoblocks.ui.timeline.model.entity.ActorActionClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTimelineClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import com.chronoblocks.ui.timeline.model.selection.TimelineKeyframeMultiSelection;
import com.chronoblocks.ui.timeline.render.TimelineRenderer;
import com.chronoblocks.ui.timeline.render.TimelineTrackControlHit;
import com.chronoblocks.ui.timeline.snapping.TimelineSnapping;
import com.chronoblocks.ui.timeline.commands.MoveAutomationKeyframeCommand;
import com.chronoblocks.ui.timeline.commands.MoveAutomationKeyframeBetweenTracksCommand;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImDrawList;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

    // ===================== 框选（Marquee Select）=====================
    private boolean marqueeCandidate = false; // 左键按下在空白处：候选（可能是点击清空，也可能是框选）
    private boolean marqueeActive = false;    // 进入拖拽阈值后：真正框选
    private float marqueeStartX = 0f;
    private float marqueeStartY = 0f;
    private float marqueeCurX = 0f;
    private float marqueeCurY = 0f;

    // ========== 吸附参考线状态 ==========
    private boolean snapGuideActive = false;
    private float snapGuideTime = -1f;
    private boolean snapGuideToGrid = false;
    private boolean snapGuideToClip = false;
    private boolean snapGuideToKey = false;

    /**
     * 浜や簰鍥炶皟鎺ュ彛锛岀�?TimelinePanel 瀹炵幇銆?
     */
    public interface InteractionCallbacks {
        void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX);
        void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime);
        void onDataChanged();
        void setHoveredTrack(TimelineTrack track);
        void onAddKeyframeToTrack(TimelineTrack track);
        void onToggleTrackLock(TimelineTrack track);
        void onToggleTrackVisible(TimelineTrack track);
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

    // ========== 吸附参考线查询（供 Renderer 使用）==========
    public boolean isSnapGuideActive() {
        return snapGuideActive;
    }

    public float getSnapGuideTime() {
        return snapGuideTime;
    }

    public boolean isSnapGuideToGrid() {
        return snapGuideToGrid;
    }

    public boolean isSnapGuideToClip() {
        return snapGuideToClip;
    }

    public boolean isSnapGuideToKeyframe() {
        return snapGuideToKey;
    }

    private void updateSnapGuide(TimelineSnapping.Result snap) {
        if (snap == null) {
            clearSnapGuide();
            return;
        }
        boolean active = snap.snappedToGrid || snap.snappedToClipEdge || snap.snappedToKeyframe;
        if (!active) {
            clearSnapGuide();
            return;
        }
        snapGuideActive = true;
        snapGuideTime = snap.snappedTime;
        snapGuideToGrid = snap.snappedToGrid;
        snapGuideToClip = snap.snappedToClipEdge;
        snapGuideToKey = snap.snappedToKeyframe;
    }

    private void clearSnapGuide() {
        snapGuideActive = false;
        snapGuideTime = -1f;
        snapGuideToGrid = false;
        snapGuideToClip = false;
        snapGuideToKey = false;
    }

    /**
     * 外部选中 Track（用于世界点击选中 Actor 时调用）
     * <p>避免重复逻辑，统一入口
     *
     * @param track TimelineTrack
     */
    public void selectTrackFromExternal(TimelineTrack track) {
        if (track == null) {
            model.selectItem(null, false);
            return;
        }

        // 选中 Track（单选模式）
        model.selectItem(track, false);
        
        // 同步到 UI SelectionManager
        com.chronoblocks.ui.selection.SelectionManager.get().setCurrentSelection(track);
        
        LOGGER.debug("[TimelineInteractionHandler] 外部选中 Track: {}", track.getName());
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

        // ⭐ 音频事件：支持片段拖动/缩放时的实时预览时间（不改数据，仅渲染使用）
        if (keyframe instanceof com.chronoblocks.ui.timeline.model.audio.AudioEvent audioEvent) {
            float preview = com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                    .getPreviewTimeForEvent(audioEvent);
            if (preview >= 0f) {
                return preview;
            }
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
     * ⭐ 获取正在拖动中的标记的临时时间（用于渲染系统）
     * 
     * <p>在拖动过程中，标记的实际时间不会改变，但 UI 需要显示拖动中的位置。
     * 此方法返回拖动中的临时时间，如果没有拖动则返回 -1。
     * 
     * @param marker 标记
     * @return 临时时间，如果没有拖动则返回 -1
     */
    public float getDraggedMarkerTime(com.chronoblocks.ui.timeline.model.TimelineMarker marker) {
        if (marker == null) {
            return -1f;
        }
        
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode == InteractionMode.DRAG_MARKER) {
            Object active = interactionManager.getActiveObject();
            if (active instanceof MarkerHandle handle) {
                if (handle.marker == marker || handle.marker.getId().equals(marker.getId())) {
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
     * ⭐ 获取正在拖动中的 Path Clip 的临时开始时间（用于渲染系统）
     */
    public float getDraggedPathClipStartTime(CameraPathClip clip) {
        if (clip == null) {
            return -1f;
        }
        
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode == InteractionMode.DRAG_PATH_CLIP || 
            mode == InteractionMode.RESIZE_PATH_CLIP_LEFT || 
            mode == InteractionMode.RESIZE_PATH_CLIP_RIGHT) {
            Object active = interactionManager.getActiveObject();
            if (active instanceof PathClipHandle handle) {
                if (handle.clip == clip || handle.clip.getId().equals(clip.getId())) {
                    return handle.tempStart;
                }
            }
        }
        
        return -1f;
    }
    
    /**
     * ⭐ 获取正在拖动中的 Path Clip 的临时结束时间（用于渲染系统）
     */
    public float getDraggedPathClipEndTime(CameraPathClip clip) {
        if (clip == null) {
            return -1f;
        }
        
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode == InteractionMode.DRAG_PATH_CLIP || 
            mode == InteractionMode.RESIZE_PATH_CLIP_LEFT || 
            mode == InteractionMode.RESIZE_PATH_CLIP_RIGHT) {
            Object active = interactionManager.getActiveObject();
            if (active instanceof PathClipHandle handle) {
                if (handle.clip == clip || handle.clip.getId().equals(clip.getId())) {
                    return handle.tempEnd;
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
        if (interactionManager.isInteracting(InteractionMode.DRAG_AUTOMATION_KEYFRAME)) {
            Object active = interactionManager.getActiveObject();
            if (active instanceof AutomationKeyframeHandle handle
                    && handle.keyframe instanceof com.chronoblocks.ui.timeline.model.audio.AudioEvent
                    && handle.currentTrack != handle.originalTrack) {
                handle.currentTrack.removeAutomationKeyframe(handle.keyframe.getId());
                handle.originalTrack.addAutomationKeyframe(handle.keyframe);
                handle.currentTrack = handle.originalTrack;
            }
        }
        clearSnapGuide();
        // 清理音频实时预览（避免右键取消交互后预览残留）
        com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance().endAllClipTimeRangePreviews();
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
        updateMarquee(contentX, trackAreaTop, contentW, viewportH, scrollY);
        
        // ⭐ v5.0：显示轨道控制按钮的 tooltip
        renderTrackControlTooltips(trackControlHit);
        
        // 注意：容器拖放处理现在在 TimelinePanel 中直接处理（�?invisibleButton 之后�?
        // 这样可以确保在正确的窗口上下文中设置拖放目标
    }

    /**
     * 渲染框选矩形（在 TimelinePanel 里调用，确保在正确的 drawList/clipRect 下绘制）
     */
    public void renderMarqueeOverlay(ImDrawList drawList) {
        if (!marqueeActive) return;

        float x1 = Math.min(marqueeStartX, marqueeCurX);
        float y1 = Math.min(marqueeStartY, marqueeCurY);
        float x2 = Math.max(marqueeStartX, marqueeCurX);
        float y2 = Math.max(marqueeStartY, marqueeCurY);

        int fill = com.chronoblocks.ui.timeline.render.TimelineColors.rgba(80, 160, 255, 35);
        int border = com.chronoblocks.ui.timeline.render.TimelineColors.rgba(80, 160, 255, 180);
        drawList.addRectFilled(x1, y1, x2, y2, fill, 2.0f);
        drawList.addRect(x1, y1, x2, y2, border, 2.0f, 0, 1.5f);
    }

    /**
     * 关键帧多选时，属性面板不应显示某一个关键帧的属性，否则会造成误解。
     * 这里根据 TimelineModel 的 selectedItems 决定提交给属性面板的对象：
     * - 单选：提交该对象本身
     * - 多选关键帧：提交 TimelineKeyframeMultiSelection（占位对象）
     */
    private void syncPropertiesSelection(Object lastFocused) {
        int count = model.getSelectedCount();
        if (count <= 0) {
            com.chronoblocks.ui.selection.SelectionManager.get().clearSelection();
            return;
        }
        if (count == 1) {
            com.chronoblocks.ui.selection.SelectionManager.get()
                    .setCurrentSelection(resolveSelectionTarget(lastFocused));
            return;
        }

        List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> clipKeyframes = new ArrayList<>();
        List<TimelineSelectable> automationKeyframes = new ArrayList<>();

        for (Object it : model.getSelectedItems()) {
            if (it instanceof com.chronoblocks.ui.timeline.model.clip.ClipKeyframe ckf) {
                clipKeyframes.add(ckf);
            } else if (it instanceof TimelineSelectable selectable && !(it instanceof TimelineClip)) {
                automationKeyframes.add(selectable);
            }
        }

        if (!clipKeyframes.isEmpty() || !automationKeyframes.isEmpty()) {
            com.chronoblocks.ui.selection.SelectionManager.get()
                    .setCurrentSelection(new TimelineKeyframeMultiSelection(clipKeyframes, automationKeyframes));
        } else {
            // 多选但不是关键帧（例如多选片段）：保持 lastFocused
            com.chronoblocks.ui.selection.SelectionManager.get()
                    .setCurrentSelection(resolveSelectionTarget(lastFocused));
        }
    }

    private Object resolveSelectionTarget(Object selection) {
        if (selection instanceof EntityTimelineClip entityTimelineClip) {
            return entityTimelineClip.getEntityClip();
        }
        return selection;
    }

    private void syncClipSelection(TimelineClip clip) {
        if (clip == null) {
            return;
        }
        com.chronoblocks.ui.selection.SelectionManager.get()
                .setCurrentSelection(resolveSelectionTarget(clip));
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
            case ACTIVE_WEATHER -> {
                com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController controller =
                        com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance();
                boolean isActive = controller.getActiveWeatherTrack() == track;
                tooltip = isActive ? "当前激活的天气轨道（点击切换到其他轨道）" : "设为当前激活的天气轨道";
            }
            case ADD_KEYFRAME -> {
                com.chronoblocks.ui.timeline.model.TrackType trackType = track.getType();
                if (trackType == com.chronoblocks.ui.timeline.model.TrackType.CONTAINER) {
                    tooltip = "容器轨道不支持轨道级关键帧，请使用片段内的动画配置";
                } else if (trackType == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
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
            case ACTIVE_WEATHER -> // ⭐ v1.0：设置激活天气轨道
                    com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance()
                            .bindActiveTrack(track);
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
                case PATH_CLIP -> onPathClipClicked(hit, mouseX, contentX, isDoubleClick); // ⭐ Path Clip 点击
                case CLIP -> onClipClicked(hit, mouseX, contentX, isDoubleClick);
                case KEYFRAME -> onKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
                case AUTOMATION_KEYFRAME -> onAutomationKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
                case MARKER -> {
                    // ⭐ 标记左键点击：选择标记（右键拖动在 TimelinePanel.handleRulerInteractions 中处理）
                    com.chronoblocks.ui.timeline.model.TimelineMarker marker = 
                        (com.chronoblocks.ui.timeline.model.TimelineMarker) hit.automationKeyframe();
                    if (marker != null) {
                        model.selectItem(marker, false);
                        syncPropertiesSelection(marker);
                    }
                }
                case EMPTY -> startMarqueeCandidate(mouseX, mouseY, trackAreaTop, scrollY);
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

    private void startMarqueeCandidate(float mouseX, float mouseY, float trackAreaTop, float scrollY) {
        // 如果当前正在进行其他交互（拖片段/拖关键帧/拖播放头），不启动框选
        if (interactionManager.isInteracting()) {
            return;
        }
        marqueeCandidate = true;
        marqueeActive = false;
        marqueeStartX = mouseX;
        marqueeStartY = mouseY;
        marqueeCurX = mouseX;
        marqueeCurY = mouseY;
    }

    private void updateMarquee(float contentX,
                               float trackAreaTop,
                               float contentW,
                               float viewportH,
                               float scrollY) {
        if (!marqueeCandidate) {
            return;
        }

        // 若鼠标仍按下：更新当前框
        if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            marqueeCurX = ImGui.getMousePosX();
            marqueeCurY = ImGui.getMousePosY();

            // 进入框选阈值
            if (!marqueeActive) {
                float dx = marqueeCurX - marqueeStartX;
                float dy = marqueeCurY - marqueeStartY;
                final float THRESHOLD_PX = 4.0f;
                if ((dx * dx + dy * dy) >= (THRESHOLD_PX * THRESHOLD_PX)) {
                    marqueeActive = true;
                }
            }
            return;
        }

        // 鼠标已松开：如果未进入框选，则视为“空白点击清空”；否则应用框选
        if (!marqueeActive) {
            // 复用原有清空逻辑
            model.clearSelection();
            com.chronoblocks.ui.selection.SelectionManager.get().clearSelection();
        } else {
            applyMarqueeSelection(contentX, trackAreaTop, scrollY);
        }

        marqueeCandidate = false;
        marqueeActive = false;
    }

    private void applyMarqueeSelection(float contentX, float trackAreaTop, float scrollY) {
        float x1 = Math.min(marqueeStartX, marqueeCurX);
        float y1 = Math.min(marqueeStartY, marqueeCurY);
        float x2 = Math.max(marqueeStartX, marqueeCurX);
        float y2 = Math.max(marqueeStartY, marqueeCurY);

        ImGuiIO io = ImGui.getIO();
        boolean additive = io.getKeyCtrl();

        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float rowHeight = TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        float r = TimelineRenderer.getKeyframeHitRadius();

        int startIdx = renderer.yToTrackIndex(y1 - trackAreaTop, scrollY);
        int endIdx = renderer.yToTrackIndex(y2 - trackAreaTop, scrollY);
        if (startIdx < 0) startIdx = 0;
        if (endIdx < 0) endIdx = 0;
        int maxIdx = model.getTrackCount() - 1;
        startIdx = Math.max(0, Math.min(maxIdx, startIdx));
        endIdx = Math.max(0, Math.min(maxIdx, endIdx));
        if (endIdx < startIdx) {
            int tmp = startIdx;
            startIdx = endIdx;
            endIdx = tmp;
        }

        List<Object> selected = new ArrayList<>();

        for (int idx = startIdx; idx <= endIdx; idx++) {
            TimelineTrack track = model.getTrackAt(idx);
            if (track == null || !track.isVisible()) continue;

            float trackTop = trackAreaTop + (idx * rowHeight - scrollY);
            float centerY = trackTop + (TimelineRenderer.getTrackHeight() / 2.0f);
            // 用“矩形与关键帧点的半径范围相交”判断，避免用户框选没覆盖到中心线就选不中
            if (centerY < (y1 - r) || centerY > (y2 + r)) {
                continue;
            }

            // Clip 内关键帧（ClipKeyframe）
            List<TimelineClip> clips = track.getClips();
            if (clips != null) {
                for (TimelineClip clip : clips) {
                    if (clip == null) continue;
                    List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> kfs = clip.getClipKeyframes();
                    if (kfs == null || kfs.isEmpty()) continue;
                    for (com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf : kfs) {
                        if (kf == null) continue;
                        float kx = contentX + (kf.getTime() * pps) - scrollX;
                        if (kx >= (x1 - r) && kx <= (x2 + r)) {
                            selected.add(kf);
                        }
                    }
                }
            }

            // 轨道级自动化关键帧（TimelineSelectable）
            if (track.hasAutomationKeyframes()) {
                for (TimelineSelectable kf : track.getAutomationKeyframes()) {
                    if (kf == null) continue;
                    float kx = contentX + (kf.getStartTime() * pps) - scrollX;
                    if (kx >= (x1 - r) && kx <= (x2 + r)) {
                        selected.add(kf);
                    }
                }
            }
        }

        model.addItemsToSelection(selected, !additive);
        if (!selected.isEmpty()) {
            Object last = selected.getLast();
            syncPropertiesSelection(last);
        }
        if (callbacks != null) {
            callbacks.onDataChanged();
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

        // 关键修复：不要在“非可调整区域”强行设置 Arrow。
        // 否则会覆盖 ImGui 内置的 dock 分割线/窗口边缘等系统光标反馈，导致“能拖拽但光标不变”。
        // ⭐ 支持容器片段（CLIP）和摄像机路径片段（PATH_CLIP）的光标更新
        if (hit == null) {
            return;
        }

        TimelineHitResult.HitType hitType = hit.type();
        
        // 处理容器片段
        if (hitType == TimelineHitResult.HitType.CLIP) {
            TimelineClip clip = hit.clip();
            if (clip == null) {
                return;
            }

            TimelineTrack track = hit.track();
            // 音频轨/锁定轨：不显示调整光标（但也不要强行覆盖成 Arrow）
            if (track != null) {
                if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.AUDIO) {
                    return;
                }
                if (track.isLocked()) {
                    return;
                }
            }

            float startX = renderer.timeToX(clip.getStartTime(), contentX);
            float endX = renderer.timeToX(clip.getEndTime(), contentX);

            boolean nearLeft = Math.abs(mouseX - startX) <= CLIP_EDGE_RESIZE_PIXEL;
            boolean nearRight = Math.abs(mouseX - endX) <= CLIP_EDGE_RESIZE_PIXEL;

            // 对于小片段，扩大边缘检测范围
            float clipWidth = endX - startX;
            if (clipWidth < CLIP_EDGE_RESIZE_PIXEL * 3) {
                nearLeft = mouseX <= startX + CLIP_EDGE_RESIZE_PIXEL * 1.5f;
                nearRight = mouseX >= endX - CLIP_EDGE_RESIZE_PIXEL * 1.5f;
            }

            if (nearLeft || nearRight) {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            }
        }
        // ⭐ 处理摄像机路径片段
        else if (hitType == TimelineHitResult.HitType.PATH_CLIP) {
            PathClipHit pathClipHit = hit.pathClipHit();
            if (pathClipHit == null) {
                return;
            }

            com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip clip = pathClipHit.clip();
            TimelineTrack track = pathClipHit.track();
            
            if (clip == null || track == null) {
                return;
            }

            // 锁定轨：不显示调整光标
            if (track.isLocked()) {
                return;
            }

            // ⭐ 摄像机轨道：检查是否是激活的轨道
            if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
                com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
                com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
                
                if (activeTrack != track) {
                    return; // 非激活轨道不显示调整光标
                }
            }

            float startX = renderer.timeToX(clip.getStartTime(), contentX);
            float endX = renderer.timeToX(clip.getEndTime(), contentX);

            boolean nearLeft = Math.abs(mouseX - startX) <= CLIP_EDGE_RESIZE_PIXEL;
            boolean nearRight = Math.abs(mouseX - endX) <= CLIP_EDGE_RESIZE_PIXEL;

            // 对于小片段，扩大边缘检测范围
            float clipWidth = endX - startX;
            if (clipWidth < CLIP_EDGE_RESIZE_PIXEL * 3) {
                nearLeft = mouseX <= startX + CLIP_EDGE_RESIZE_PIXEL * 1.5f;
                nearRight = mouseX >= endX - CLIP_EDGE_RESIZE_PIXEL * 1.5f;
            }

            if (nearLeft || nearRight) {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            }
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
        syncClipSelection(clip);
        
        if (callbacks != null) callbacks.onDataChanged();
        
        boolean isAudioTrack = track.getType() == com.chronoblocks.ui.timeline.model.TrackType.AUDIO;
        if (isAudioTrack) {
            if (isDoubleClick && callbacks != null) {
                callbacks.onKeyframeDoubleClick(clip, mouseX, contentX);
            }
        }
        
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

        if (isAudioTrack) {
            nearLeft = false;
            nearRight = false;
        }

        if (nearLeft) {
            // ⭐ 音频事件实时预览：开始缩放预览（拖动过程中不改事件真实时间）
            com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                    .beginClipTimeRangePreview(clip, clip.getStartTime(), clip.getEndTime());
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
            com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                    .beginClipTimeRangePreview(clip, clip.getStartTime(), clip.getEndTime());
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
            com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                    .beginClipTimeRangePreview(clip, clip.getStartTime(), clip.getEndTime());
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
        syncPropertiesSelection(kf);

        // ⭐ 功能：选中单个关键帧时，播放头自动跳转到该关键帧的时间位置
        if (!addToSelection && model.getSelectedCount() == 1) {
            // 确保选中的确实是关键帧（不是 Clip 或其他类型）
            Object selected = model.getSelectedItems().getFirst();
            if (selected instanceof com.chronoblocks.ui.timeline.model.clip.ClipKeyframe) {
                float keyframeTime = kf.getTime();
                model.setCurrentTime(keyframeTime);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 选中关键帧，播放头跳转到 {:.2f}s", keyframeTime);
                }
            }
        }

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
        
        // 同步到属性面板：多选关键帧时显示"多选关键帧"占位，避免显示单个对象属性
        syncPropertiesSelection(keyframe);

        // ⭐ 功能：选中单个关键帧时，播放头自动跳转到该关键帧的时间位置
        if (!addToSelection && model.getSelectedCount() == 1) {
            // 确保选中的确实是关键帧（不是 Clip 或其他类型）
            Object selected = model.getSelectedItems().getFirst();
            if (selected instanceof TimelineSelectable && !(selected instanceof TimelineClip)) {
                float keyframeTime = keyframe.getStartTime();
                model.setCurrentTime(keyframeTime);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 选中自动化关键帧，播放头跳转到 {:.2f}s", keyframeTime);
                }
            }
        }

        // 🔒 音频事件点：不允许鼠标拖动改变时间（它应与容器方块时刻严格对应）
        if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.AUDIO
                && keyframe instanceof com.chronoblocks.ui.timeline.model.audio.AudioEvent) {
            if (callbacks != null) {
                callbacks.onDataChanged();
            }
            return;
        }
        
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

    /**
     * ⭐ 处理 Path Clip 的点击
     */
    private void onPathClipClicked(TimelineHitResult hit,
                                   float mouseX,
                                   float contentX,
                                   boolean isDoubleClick) {
        PathClipHit pathClipHit = hit.pathClipHit();
        if (pathClipHit == null) {
            return;
        }
        
        TimelineTrack track = pathClipHit.track();
        com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip clip = pathClipHit.clip();
        
        if (track == null || clip == null) {
            return;
        }
        
        // 🔒 轨道锁定功能：如果轨道锁定，禁止操作
        if (track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止操作 Path Clip (轨道: {})", track.getName());
            }
            return;
        }
        
        // ⭐ v5.0：如果是摄像机轨道，检查是否是激活的轨道
        if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
            if (activeTrack != track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止操作 Path Clip (轨道: {})", track.getName());
                }
                return;
            }
        }
        
        // 双击：打开属性面板
        if (isDoubleClick) {
            model.selectItem(clip, false);
            syncPropertiesSelection(clip);
            if (callbacks != null) {
                callbacks.onDataChanged();
            }
            return;
        }
        
        // 单击：选中 Path Clip
        ImGuiIO io = ImGui.getIO();
        boolean addToSelection = io.getKeyCtrl();
        model.selectItem(clip, addToSelection);
        syncPropertiesSelection(clip);
        
        // ⭐ 功能：选中单个 Path Clip 时，播放头自动跳转到开始时间
        if (!addToSelection && model.getSelectedCount() == 1) {
            float startTime = clip.getStartTime();
            model.setCurrentTime(startTime);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 选中 Path Clip，播放头跳转到 {:.2f}s", startTime);
            }
        }
        
        // 根据命中区域决定交互模式
        PathClipHit.HitZone zone = pathClipHit.zone();
        PathClipHandle handle = new PathClipHandle(track, clip);
        
        InteractionMode mode;
        if (zone == PathClipHit.HitZone.RESIZE_L) {
            mode = InteractionMode.RESIZE_PATH_CLIP_LEFT;
        } else if (zone == PathClipHit.HitZone.RESIZE_R) {
            mode = InteractionMode.RESIZE_PATH_CLIP_RIGHT;
        } else {
            mode = InteractionMode.DRAG_PATH_CLIP;
        }
        
        interactionManager.startInteraction(
                mode,
                handle,
                mouseX, ImGui.getMousePosY(),
                clip.getStartTime(), clip.getEndTime()
        );
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Timeline] 开始 {} Path Clip: {} (时间: {:.2f}s - {:.2f}s)", 
                mode, clip.getName(), clip.getStartTime(), clip.getEndTime());
        }
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }

    // ========================================================
    // 右键菜单
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
                    // ✅ 右键命中已选中的对象时，不要破坏当前多选（否则无法批量操作）
                    if (!model.isSelected(clip)) {
                        model.setSelectedClip(clip);
                    }
                    // 同步到属性面板（单选 clip）
                    syncClipSelection(clip);
                }
            }
            case KEYFRAME -> {
                // ⭐⭐ 修复：正确选择 ClipKeyframe 对象
                com.chronoblocks.ui.timeline.model.clip.ClipKeyframe clipKeyframe = hit.clipKeyframe();
                if (clipKeyframe != null) {
                    ImGuiIO io = ImGui.getIO();
                    boolean addToSelection = io.getKeyCtrl();
                    // ✅ 右键点在已选中的关键帧上：保持现有选择（不清空多选）
                    if (!model.isSelected(clipKeyframe)) {
                        model.selectItem(clipKeyframe, addToSelection);
                    }
                    syncPropertiesSelection(clipKeyframe);
                    LOGGER.debug("[Timeline] 右键选择 ClipKeyframe @ {}s", clipKeyframe.getTime());
                    
                    // ⭐ 功能：选中单个关键帧时，播放头自动跳转到该关键帧的时间位置
                    if (!addToSelection && model.getSelectedCount() == 1) {
                        Object selected = model.getSelectedItems().getFirst();
                        if (selected instanceof com.chronoblocks.ui.timeline.model.clip.ClipKeyframe) {
                            float keyframeTime = clipKeyframe.getTime();
                            model.setCurrentTime(keyframeTime);
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("[Timeline] 右键选中关键帧，播放头跳转到 {:.2f}s", keyframeTime);
                            }
                        }
                    }
                } else {
                    // 回退：如果没有 ClipKeyframe 对象，选择 clip
                    TimelineClip clip = hit.clip();
                    if (clip != null) {
                        if (!model.isSelected(clip)) {
                            model.setSelectedClip(clip);
                        }
                        syncClipSelection(clip);
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
                    // ✅ 右键点在已选中的关键帧上：保持现有选择（不清空多选）
                    if (!model.isSelected(keyframe)) {
                        model.selectItem(keyframe, addToSelection);
                    }
                    // 同步属性面板：多选关键帧时显示"多选关键帧"占位
                    syncPropertiesSelection(keyframe);
                    if (keyframe instanceof com.chronoblocks.ui.timeline.model.camera.CameraKeyframe ck) {
                        LOGGER.debug("[Timeline] 右键摄像机关键帧 @ {}s，准备弹出关键帧菜单", ck.getTime());
                    }
                    
                    // ⭐ 功能：选中单个关键帧时，播放头自动跳转到该关键帧的时间位置
                    if (!addToSelection && model.getSelectedCount() == 1) {
                        Object selected = model.getSelectedItems().getFirst();
                        if (selected instanceof TimelineSelectable && !(selected instanceof TimelineClip)) {
                            float keyframeTime = keyframe.getStartTime();
                            model.setCurrentTime(keyframeTime);
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("[Timeline] 右键选中自动化关键帧，播放头跳转到 {:.2f}s", keyframeTime);
                            }
                        }
                    }
                }
            }
            case MARKER -> {
                // ⭐ 标记右键：选择标记并打开属性面板（已在 TimelinePanel.handleRulerInteractions 中处理）
                com.chronoblocks.ui.timeline.model.TimelineMarker marker = 
                    (com.chronoblocks.ui.timeline.model.TimelineMarker) hit.automationKeyframe();
                if (marker != null) {
                    if (!model.isSelected(marker)) {
                        model.selectItem(marker, false);
                    }
                    syncPropertiesSelection(marker);
                }
            }
            case PATH_CLIP -> {
                // ⭐ Path Clip 右键：选择 Path Clip 并打开属性面板
                PathClipHit pathClipHit = hit.pathClipHit();
                if (pathClipHit != null) {
                    com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip clip = pathClipHit.clip();
                    TimelineTrack track = pathClipHit.track();
                    
                    if (clip != null && track != null) {
                        // 检查轨道锁定和激活状态
                        if (track.isLocked()) {
                            LOGGER.debug("[Timeline] 轨道已锁定，禁止右键操作 Path Clip (轨道: {})", track.getName());
                            return;
                        }
                        
                        if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
                            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
                            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
                            
                            if (activeTrack != track) {
                                LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止右键操作 Path Clip (轨道: {})", track.getName());
                                return;
                            }
                        }
                        
                        if (!model.isSelected(clip)) {
                            model.selectItem(clip, false);
                        }
                        syncPropertiesSelection(clip);
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
                // 清理音频预览状态（提交后不再需要）
                com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                        .endClipTimeRangePreview(handle.clip);
            }
        } else if (mode == InteractionMode.RESIZE_CLIP_LEFT) {
            // �?优化：调整左边缘，使�?ResizeClipCommand 支持 Undo
            if (obj instanceof ClipHandle handle) {
                float originalStart = interactionManager.getDragStartValue();
                float originalEnd = interactionManager.getDragStartValue2();
                float currentStart = handle.clip.getStartTime();
                float currentEnd = handle.clip.getEndTime();
                
                // 只有当位置真正改变时才创建命�?
                if (Math.abs(currentStart - originalStart) > 0.001f) {
                    commandHistory.executeCommand(
                        new ResizeClipCommand(model, handle.clip, originalStart, originalEnd, currentStart, currentEnd)
                    );
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] 调整片段左边�? {}s -> {}s (片段: {})", 
                            originalStart, currentStart, handle.clip.getLabel());
                    }
                }
                
                // 清理音频预览状态（提交后不再需要）
                com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                        .endClipTimeRangePreview(handle.clip);

                if (callbacks != null) callbacks.onDataChanged();
            }
        } else if (mode == InteractionMode.RESIZE_CLIP_RIGHT) {
            // �?优化：调整右边缘，使�?ResizeClipCommand 支持 Undo
            if (obj instanceof ClipHandle handle) {
                float originalEnd = interactionManager.getDragStartValue2();
                float originalStart = interactionManager.getDragStartValue();
                float currentEnd = handle.clip.getEndTime();
                float currentStart = handle.clip.getStartTime();
                
                // 只有当位置真正改变时才创建命�?
                if (Math.abs(currentEnd - originalEnd) > 0.001f) {
                    commandHistory.executeCommand(
                        new ResizeClipCommand(model, handle.clip, originalStart, originalEnd, currentStart, currentEnd)
                    );
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] 调整片段右边�? {}s -> {}s (片段: {})", 
                            originalEnd, currentEnd, handle.clip.getLabel());
                    }
                }
                
                com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                        .endClipTimeRangePreview(handle.clip);

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
        } else if (mode == InteractionMode.PAINT_ACTION) {
            // ⭐ Action 涂抹：应用涂抹结果
            if (obj instanceof ActionPaintHandle handle) {
                applyActionPaint(handle);
            }
        } else if (mode == InteractionMode.DRAG_AUTOMATION_KEYFRAME) {
            if (obj instanceof AutomationKeyframeHandle handle) {
                float originalTime = interactionManager.getDragStartValue();
                float finalTime = handle.currentTime;

                if (handle.keyframe instanceof com.chronoblocks.ui.timeline.model.audio.AudioEvent
                        && handle.currentTrack != handle.originalTrack) {
                    commandHistory.executeCommand(
                        new MoveAutomationKeyframeBetweenTracksCommand(
                            model, handle.originalTrack, handle.currentTrack, handle.keyframe, originalTime, finalTime)
                    );
                } else {
                    // ⭐⭐ 修复：改成单一命令处理
                    // 所有数据结构修改都在这里执行一次，拖动过程中不会修改模型
                    commandHistory.executeCommand(
                        new MoveAutomationKeyframeCommand(
                            model, handle.currentTrack, handle.keyframe, originalTime, finalTime)
                    );
                }

                // MoveAutomationKeyframeCommand 内部已经调用了 onKeyframeChanged()，这里不需要再调用

                if (callbacks != null) callbacks.onDataChanged();

                LOGGER.debug("[Timeline] 自动化关键帧拖动完成 {} -> {}", originalTime, finalTime);
            }
        } else if (mode == InteractionMode.DRAG_MARKER) {
            if (obj instanceof MarkerHandle handle) {
                float originalTime = interactionManager.getDragStartValue();
                float finalTime = handle.currentTime;

                // ⭐ 使用 MoveMarkerCommand 处理标记移动（支持撤销/重做）
                commandHistory.executeCommand(
                    new com.chronoblocks.ui.timeline.commands.MoveMarkerCommand(
                        model, handle.marker, originalTime, finalTime)
                );

                if (callbacks != null) callbacks.onDataChanged();

                LOGGER.debug("[Timeline] 标记拖动完成 {} -> {}", originalTime, finalTime);
            }
        } else if (mode == InteractionMode.DRAG_PLAYHEAD) {
            // ⭐ 拖动播放头结束：通知摄像机控制器结束 scrubbing
            float finalTime = model.getCurrentTime();
            model.setScrubbing(false);
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().endScrubbing(finalTime);
            LOGGER.debug("[TimelineInteractionHandler] 拖动播放头结束 @ {:.2f}s", finalTime);
        } else if (mode == InteractionMode.DRAG_PATH_CLIP) {
            // ⭐ 拖动 Path Clip：使用 MovePathClipCommand
            if (obj instanceof PathClipHandle handle) {
                float originalStart = interactionManager.getDragStartValue();
                float originalEnd = interactionManager.getDragStartValue2();
                float finalStart = handle.tempStart;
                float finalEnd = handle.tempEnd;
                
                // 只有当位置真正改变时才创建命令
                if (Math.abs(finalStart - originalStart) > 0.001f || Math.abs(finalEnd - originalEnd) > 0.001f) {
                    commandHistory.executeCommand(
                        new com.chronoblocks.ui.timeline.commands.MovePathClipCommand(
                            model, handle.track, handle.clip, originalStart, originalEnd, finalStart, finalEnd)
                    );
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] Path Clip 拖动完成: {}s-{}s -> {}s-{}s", 
                            originalStart, originalEnd, finalStart, finalEnd);
                    }
                }
                
                if (callbacks != null) callbacks.onDataChanged();
            }
        } else if (mode == InteractionMode.RESIZE_PATH_CLIP_LEFT) {
            // ⭐ 拉伸 Path Clip 左边缘：使用 ResizePathClipCommand
            if (obj instanceof PathClipHandle handle) {
                float originalStart = interactionManager.getDragStartValue();
                float originalEnd = interactionManager.getDragStartValue2();
                float finalStart = handle.tempStart;
                float finalEnd = handle.tempEnd;
                
                // 只有当位置真正改变时才创建命令
                if (Math.abs(finalStart - originalStart) > 0.001f) {
                    commandHistory.executeCommand(
                        new com.chronoblocks.ui.timeline.commands.ResizePathClipCommand(
                            model, handle.track, handle.clip, originalStart, originalEnd, finalStart, finalEnd)
                    );
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] Path Clip 左边缘调整完成: {}s -> {}s", originalStart, finalStart);
                    }
                }
                
                if (callbacks != null) callbacks.onDataChanged();
            }
        } else if (mode == InteractionMode.RESIZE_PATH_CLIP_RIGHT) {
            // ⭐ 拉伸 Path Clip 右边缘：使用 ResizePathClipCommand
            if (obj instanceof PathClipHandle handle) {
                float originalStart = interactionManager.getDragStartValue();
                float originalEnd = interactionManager.getDragStartValue2();
                float finalStart = handle.tempStart;
                float finalEnd = handle.tempEnd;
                
                // 只有当位置真正改变时才创建命令
                if (Math.abs(finalEnd - originalEnd) > 0.001f) {
                    commandHistory.executeCommand(
                        new com.chronoblocks.ui.timeline.commands.ResizePathClipCommand(
                            model, handle.track, handle.clip, originalStart, originalEnd, finalStart, finalEnd)
                    );
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] Path Clip 右边缘调整完成: {}s -> {}s", originalEnd, finalEnd);
                    }
                }
                
                if (callbacks != null) callbacks.onDataChanged();
            }
        }

        clearSnapGuide();
        interactionManager.endInteraction();
    }

    // ========== 拖动处理（帧更新�?=========

    private void handleDrag(TimelineHitResult hit,
                            float contentX,
                            float trackAreaTop,
                            float contentW,
                            float viewportH,
                            float scrollY) {
        // ⭐ 支持右键拖动标记
        boolean isLeftMouseDown = ImGui.isMouseDown(ImGuiMouseButton.Left);
        boolean isRightMouseDown = ImGui.isMouseDown(ImGuiMouseButton.Right);
        if (!isLeftMouseDown && !isRightMouseDown) return;
        if (!interactionManager.isInteracting()) return;
        
        // 右键拖动只支持标记
        if (isRightMouseDown && !interactionManager.isInteracting(InteractionMode.DRAG_MARKER)) {
            return;
        }

        InteractionMode mode = interactionManager.getCurrentMode();
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();

        switch (mode) {
            case DRAG_CLIP -> dragClip(mouseX, contentX, mouseY, trackAreaTop);
            case RESIZE_CLIP_LEFT -> resizeClipLeft(mouseX, contentX);
            case RESIZE_CLIP_RIGHT -> resizeClipRight(mouseX, contentX);
            case DRAG_KEYFRAME -> dragKeyframe(mouseX, contentX);
            case DRAG_AUTOMATION_KEYFRAME -> dragAutomationKeyframe(mouseX, contentX, mouseY, trackAreaTop, scrollY);
            case DRAG_MARKER -> dragMarker(mouseX, contentX);  // ⭐ 拖动标记
            case DRAG_PLAYHEAD -> dragPlayhead(mouseX, contentX);
            case PAINT_ACTION -> paintAction(mouseX, contentX); // ⭐ Action 涂抹
            case DRAG_PATH_CLIP -> dragPathClip(mouseX, contentX); // ⭐ 拖动 Path Clip
            case RESIZE_PATH_CLIP_LEFT -> resizePathClipLeft(mouseX, contentX); // ⭐ 拉伸 Path Clip 左边缘
            case RESIZE_PATH_CLIP_RIGHT -> resizePathClipRight(mouseX, contentX); // ⭐ 拉伸 Path Clip 右边缘
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
            clearSnapGuide();
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

        boolean isBoundAudioClip = handle.clip instanceof com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip
                && com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                        .isAudioClipBound(handle.clip);
        if (isBoundAudioClip) {
            delta = 0f;
        }

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
                    // Track constraint: use isClipAllowedOnTrack
                    if (!isClipAllowedOnTrack(handle.clip, newTrack)) {
                        // Block invalid track types
                        newTrack = null;
                    }



                    if (newTrack == null) {
                        // 不允许跨轨道移动：保持在当前轨道
                        targetTrack = currentTrack;
                    } else {

                        // �?关键修复：确保片段从旧轨道移除（即使它可能已经在其他轨道上）
                        if (currentTrack != null) {
                            currentTrack.removeClip(handle.clip);
                        }
                        
                        // �?关键修复：确保片段不在新轨道上才添加（避免重复添加）
                        if (!newTrack.getClips().contains(handle.clip)) {
                            newTrack.addClip(handle.clip);
                        }
                        
                        targetTrack = newTrack; // 更新目标轨道用于吸附计算
                        
                        if (newTrack.getType() == TrackType.AUDIO) {
                            com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                                    .moveLinkedEventsToTrack(handle.clip, newTrack);
                        }
                        
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("[Timeline] 片段跨轨道移�? {} -> {} (片段: {})", 
                                currentTrack != null ? currentTrack.getName() : "null",
                                newTrack.getName(), handle.clip.getLabel());
                        }
                    }
                }
            }
        }

        // �?修复：使用目标轨道进行吸附计算，确保跨轨道拖动时吸附正确
        float snappedStart;
        if (isBoundAudioClip) {
            clearSnapGuide();
            snappedStart = Math.max(0f, start0);
        } else {
            TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                    model, candidateStart, targetTrack, handle.clip, 0f
            );
            updateSnapGuide(snap);
            snappedStart = Math.max(0f, snap.snappedTime);
        }
        float snappedEnd = snappedStart + length;

        // �?修复：确保片段时长保持不变，只移动位置（X轴和Y轴都更新�?
        handle.clip.setStartTime(snappedStart);
        handle.clip.setEndTime(snappedEnd);
        model.markDurationDirty();

        // ⭐ 音频事件实时预览：随片段拖动同步预览
        com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                .updateClipTimeRangePreview(handle.clip, handle.clip.getStartTime(), handle.clip.getEndTime());

        if (callbacks != null) callbacks.onDataChanged();
    }

    private boolean isClipAllowedOnTrack(TimelineClip clip, TimelineTrack track) {
        if (clip == null || track == null) return false;
        TrackType tt = track.getType();
        if (tt == null) return true;

        // 只对“绑定容器”的片段做强约束（防止误伤其他类型片段）
        if (clip.getBoundContainerId() == null) {
            return true;
        }

        // ✅ AudioTimelineClip：只允许在 AUDIO 轨道（它的 boundContainerId 用于音效同步，不是“容器片段”）
        if (clip instanceof com.chronoblocks.ui.timeline.model.audio.AudioTimelineClip) {
            return tt == TrackType.AUDIO;
        }

        // ✅ 容器片段：只能在 CONTAINER 轨道（拆除容器和普通容器都可以放在容器轨道上）
        return tt == TrackType.CONTAINER;// 其它轨道一律不允许容器片段进入
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
            clearSnapGuide();
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
        updateSnapGuide(snap);
        float snappedStart = Math.min(snap.snappedTime, handle.clip.getEndTime() - minLength);
        snappedStart = Math.max(0f, snappedStart);

        // �?优化：实时更新片段开始时间，保持结束时间不变（调整时长）
        handle.clip.setStartTime(snappedStart);
        model.markDurationDirty();

        // ⭐ 音频事件实时预览：随边缘拖动同步缩放/拉伸预览
        com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                .updateClipTimeRangePreview(handle.clip, handle.clip.getStartTime(), handle.clip.getEndTime());

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
            clearSnapGuide();
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
        updateSnapGuide(snap);
        float snappedEnd = Math.max(snap.snappedTime, handle.clip.getStartTime() + minLength);

        // �?优化：实时更新片段结束时间，保持开始时间不变（调整时长�?
        handle.clip.setEndTime(snappedEnd);
        model.markDurationDirty();

        com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                .updateClipTimeRangePreview(handle.clip, handle.clip.getStartTime(), handle.clip.getEndTime());

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
            clearSnapGuide();
            return;
        }

        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, handle.track, handle.clip, 0f
        );
        updateSnapGuide(snap);
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
    private void dragAutomationKeyframe(float mouseX, float contentX, float mouseY, float trackAreaTop, float scrollY) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof AutomationKeyframeHandle handle)) {
            return;
        }

        // ✅ 修复：容器轨道和清除轨道不支持轨道级关键帧，禁止拖动
        if (handle.currentTrack != null) {
            com.chronoblocks.ui.timeline.model.TrackType trackType = handle.currentTrack.getType();
            if (trackType == com.chronoblocks.ui.timeline.model.TrackType.CONTAINER) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 容器轨道不支持轨道级关键帧拖动 (轨道: {})", handle.currentTrack.getName());
                }
                clearSnapGuide();
                return;
            }
        }

        // 轨道锁定功能：如果轨道锁定，禁止拖动关键帧
        if (handle.currentTrack != null && handle.currentTrack.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动自动化关键帧(轨道: {})", handle.currentTrack.getName());
            }
            clearSnapGuide();
            return;
        }

        boolean isAudioEvent = handle.currentTrack != null
                && handle.currentTrack.getType() == com.chronoblocks.ui.timeline.model.TrackType.AUDIO
                && handle.keyframe instanceof com.chronoblocks.ui.timeline.model.audio.AudioEvent;
        if (isAudioEvent) {
            // 允许在不同音频轨道之间移动（垂直拖动），时间不变
            if (mouseY > 0 && trackAreaTop > 0) {
                float relativeY = mouseY - trackAreaTop;
                int newTrackIndex = renderer.yToTrackIndex(relativeY, scrollY);
                if (newTrackIndex >= 0 && newTrackIndex < model.getTrackCount()) {
                    TimelineTrack newTrack = model.getTrackAt(newTrackIndex);
                    if (newTrack != null
                            && newTrack != handle.currentTrack
                            && !newTrack.isLocked()
                            && newTrack.getType() == com.chronoblocks.ui.timeline.model.TrackType.AUDIO) {
                        handle.currentTrack.removeAutomationKeyframe(handle.keyframe.getId());
                        newTrack.addAutomationKeyframe(handle.keyframe);
                        handle.currentTrack = newTrack;
                    }
                }
            }
            handle.currentTime = handle.keyframe.getStartTime();
            if (callbacks != null) {
                callbacks.onDataChanged();
            }
            return;
        }

        // ⭐ v5.0：如果是摄像机轨道，检查是否是激活的轨道
        if (handle.currentTrack != null && handle.currentTrack.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
            if (activeTrack != handle.currentTrack) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止拖动关键帧(轨道: {})", handle.currentTrack.getName());
                }
                clearSnapGuide();
                return;
            }
        }
        
        // ⭐ v3.5：如果是时间轨道，检查是否是激活的轨道
        if (handle.currentTrack != null && handle.currentTrack.getType() == com.chronoblocks.ui.timeline.model.TrackType.TIME) {
            com.chronoblocks.ui.timeline.playback.time.TimelineWorldController controller =
                    com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveTimeTrack();
            
            if (activeTrack != handle.currentTrack) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 时间轨道未激活，禁止拖动关键帧(轨道: {})", handle.currentTrack.getName());
                }
                clearSnapGuide();
                return;
            }
        }
        
        // ⭐ v1.1：如果是季节轨道，检查是否是激活的轨道
        if (handle.currentTrack != null && handle.currentTrack.getType() == com.chronoblocks.ui.timeline.model.TrackType.SEASON) {
            com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController controller =
                    com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveSeasonTrack();
            
            if (activeTrack != handle.currentTrack) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 季节轨道未激活，禁止拖动关键帧(轨道: {})", handle.currentTrack.getName());
                }
                clearSnapGuide();
                return;
            }
        }

        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, handle.currentTrack, null, 0f
        );
        updateSnapGuide(snap);
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
        updateSnapGuide(snap);
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
        
        // ⭐ 天气轨道系统：点击时间刻度时更新天气采样（预览模式）
        com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance().previewAt(clampedTime);
        
        // 点击时间刻度时跳转镜头到该时间并保持玩家态，便于继续微调
        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().jumpToTimeAsPlayer(clampedTime);
        
        // 更新片段方块显示状态
        com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
        
        LOGGER.debug("[TimelineInteractionHandler] 点击时间标尺，跳转到 {:.2f}s", clampedTime);
    }
    
    /**
     * ⭐ 开始拖动标记（右键拖动）
     * 
     * @param marker 要拖动的标记
     * @param mouseX 鼠标 X 坐标
     * @param contentX 内容区域 X 起点
     */
    public void startMarkerDrag(com.chronoblocks.ui.timeline.model.TimelineMarker marker, float mouseX, float contentX) {
        if (marker == null) {
            LOGGER.warn("[Timeline] 开始拖动标记失败：marker 为 null");
            return;
        }
        
        // 开始拖动交互
        MarkerHandle handle = new MarkerHandle(marker);
        interactionManager.startInteraction(
                InteractionMode.DRAG_MARKER,
                handle,
                mouseX, ImGui.getMousePosY(),
                marker.getTime(), 0f
        );
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
        
        LOGGER.debug("[TimelineInteractionHandler] 开始拖动标记 @ {:.2f}s", marker.getTime());
    }
    
    /**
     * ⭐ 更新标记拖动位置
     * 
     * @param mouseX 鼠标 X 坐标
     * @param contentX 内容区域 X 起点
     */
    public void updateMarkerDrag(float mouseX, float contentX) {
        if (!interactionManager.isInteracting(InteractionMode.DRAG_MARKER)) {
            return;
        }
        
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof MarkerHandle handle)) {
            return;
        }
        
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, null, null, 0f
        );
        updateSnapGuide(snap);
        float newTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));
        
        float eps = 0.001f;
        if (Math.abs(newTime - handle.currentTime) < eps) {
            return;
        }
        
        // 实时预览：只更新 Handle 的临时时间，不修改模型
        handle.currentTime = newTime;
        
        // 通知 UI 刷新（渲染系统会读取 handle.currentTime 来显示标记位置）
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }
    
    /**
     * ⭐ 结束标记拖动
     */
    public void endMarkerDrag() {
        if (!interactionManager.isInteracting(InteractionMode.DRAG_MARKER)) {
            return;
        }
        
        Object active = interactionManager.getActiveObject();
        if (active instanceof MarkerHandle handle) {
            float originalTime = interactionManager.getDragStartValue();
            float finalTime = handle.currentTime;

            // 使用 MoveMarkerCommand 处理标记移动（支持撤销/重做）
            commandHistory.executeCommand(
                new com.chronoblocks.ui.timeline.commands.MoveMarkerCommand(
                    model, handle.marker, originalTime, finalTime)
            );

            if (callbacks != null) callbacks.onDataChanged();

            LOGGER.debug("[Timeline] 标记拖动完成 {} -> {}", originalTime, finalTime);
        }
        
        clearSnapGuide();
        interactionManager.endInteraction();
    }
    
    /**
     * ⭐ 检查是否正在拖动标记
     */
    public boolean isDraggingMarker() {
        return interactionManager.isInteracting(InteractionMode.DRAG_MARKER);
    }
    
    /**
     * ⭐ 拖动标记（内部方法，由 handleDrag 调用）
     */
    private void dragMarker(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof MarkerHandle handle)) {
            return;
        }
        
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, null, null, 0f
        );
        updateSnapGuide(snap);
        float newTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));
        
        float eps = 0.001f;
        if (Math.abs(newTime - handle.currentTime) < eps) {
            return;
        }
        
        // 实时预览：只更新 Handle 的临时时间，不修改模型
        handle.currentTime = newTime;
        
        // 通知 UI 刷新
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }

    private void dragPlayhead(float mouseX, float contentX) {
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, rawTime, null, null, 0f
        );
        updateSnapGuide(snap);
        float clampedTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));
        
        // 更新模型时间
        model.setCurrentTime(clampedTime);
        
        // ⭐ 季节轨道系统：拖动播放头时更新季节采样
        com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance().onTimelineTick(clampedTime);
        
        // ⭐ 天气轨道系统：拖动播放头时更新天气采样（预览模式）
        com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance().previewAt(clampedTime);
        
        // ⭐ 通知摄像机控制器更新 scrubbing 位置
        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().updateScrubbing(clampedTime);
        
        // �?功能：播放头拖动时，立即更新片段方块显示状�?
        com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
        
        if (callbacks != null) callbacks.onDataChanged();
    }
    
    /**
     * ⭐ 拖动 Path Clip（整体移动）
     */
    private void dragPathClip(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof PathClipHandle handle)) {
            return;
        }
        
        // 🔒 轨道锁定功能
        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动 Path Clip (轨道: {})", handle.track.getName());
            }
            clearSnapGuide();
            return;
        }
        
        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float length = originalEnd - originalStart;
        
        // 计算时间变化
        float startMouseX = interactionManager.getDragStartMouseX();
        float startMouseTime = renderer.xToTime(startMouseX, contentX);
        float currentMouseTime = renderer.xToTime(mouseX, contentX);
        float delta = currentMouseTime - startMouseTime;
        
        float candidateStart = originalStart + delta;
        candidateStart = Math.max(0f, candidateStart);
        
        // 应用吸附
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, candidateStart, handle.track, null, 0f
        );
        float snappedStart = Math.max(0f, snap.snappedTime);
        float snappedEnd = snappedStart + length;
        
        // 检查重叠（硬拒绝策略）
        if (hasPathClipOverlap(handle.track, snappedStart, snappedEnd, handle.clip)) {
            // 不允许重叠，保持原位置
            return;
        }
        updateSnapGuide(snap);
        
        // 更新临时值（用于渲染实时反馈）
        handle.tempStart = snappedStart;
        handle.tempEnd = snappedEnd;
        
        // ⭐ 注意：拖动过程中不直接修改 clip 的时间，只更新临时值
        // 实际的时间修改在 endInteractionIfNeeded 中通过命令完成
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }
    
    /**
     * ⭐ 拉伸 Path Clip 左边缘
     */
    private void resizePathClipLeft(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof PathClipHandle handle)) {
            return;
        }
        
        // 🔒 轨道锁定功能
        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止调整 Path Clip (轨道: {})", handle.track.getName());
            }
            clearSnapGuide();
            return;
        }
        
        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float minLength = 0.1f; // 最小长度 0.1 秒
        
        float startMouseX = interactionManager.getDragStartMouseX();
        float startMouseTime = renderer.xToTime(startMouseX, contentX);
        float currentMouseTime = renderer.xToTime(mouseX, contentX);
        float delta = currentMouseTime - startMouseTime;
        
        float candidateStart = originalStart + delta;
        candidateStart = Math.min(candidateStart, originalEnd - minLength);
        candidateStart = Math.max(0f, candidateStart);
        
        // 应用吸附
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, candidateStart, handle.track, null, 0f
        );
        float snappedStart = Math.min(snap.snappedTime, originalEnd - minLength);
        snappedStart = Math.max(0f, snappedStart);
        
        // 检查重叠
        if (hasPathClipOverlap(handle.track, snappedStart, originalEnd, handle.clip)) {
            return;
        }
        updateSnapGuide(snap);
        
        // 更新临时值
        handle.tempStart = snappedStart;
        handle.tempEnd = originalEnd;
        
        // ⭐ 实时更新 clip 的时间，确保属性面板能实时显示时间变化（与容器片段保持一致）
        // 注意：最终的时间修改仍然会在 endInteractionIfNeeded 中通过命令完成（用于撤销/重做）
        handle.clip.setStartTime(snappedStart);
        handle.clip.setEndTime(originalEnd);
        model.markDurationDirty();
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }
    
    /**
     * ⭐ 拉伸 Path Clip 右边缘
     */
    private void resizePathClipRight(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof PathClipHandle handle)) {
            return;
        }
        
        // 🔒 轨道锁定功能
        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止调整 Path Clip (轨道: {})", handle.track.getName());
            }
            clearSnapGuide();
            return;
        }
        
        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float minLength = 0.1f; // 最小长度 0.1 秒
        
        float startMouseX = interactionManager.getDragStartMouseX();
        float startMouseTime = renderer.xToTime(startMouseX, contentX);
        float currentMouseTime = renderer.xToTime(mouseX, contentX);
        float delta = currentMouseTime - startMouseTime;
        
        float candidateEnd = originalEnd + delta;
        candidateEnd = Math.max(candidateEnd, originalStart + minLength);
        
        // 应用吸附
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, candidateEnd, handle.track, null, 0f
        );
        float snappedEnd = Math.max(snap.snappedTime, originalStart + minLength);
        
        // 检查重叠
        if (hasPathClipOverlap(handle.track, originalStart, snappedEnd, handle.clip)) {
            return;
        }
        updateSnapGuide(snap);
        
        // 更新临时值
        handle.tempStart = originalStart;
        handle.tempEnd = snappedEnd;
        
        // ⭐ 实时更新 clip 的时间，确保属性面板能实时显示时间变化（与容器片段保持一致）
        // 注意：最终的时间修改仍然会在 endInteractionIfNeeded 中通过命令完成（用于撤销/重做）
        handle.clip.setStartTime(originalStart);
        handle.clip.setEndTime(snappedEnd);
        model.markDurationDirty();
        
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }
    
    /**
     * 检查 Path Clip 是否与其他 Path Clip 重叠
     * ⭐ 优化：委托给 TimelineTrack.hasCameraPathOverlap() 方法
     */
    private boolean hasPathClipOverlap(TimelineTrack track, float start, float end, CameraPathClip exclude) {
        if (track == null) {
            return false;
        }
        return track.hasCameraPathOverlap(start, end, exclude);
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
        final TimelineTrack originalTrack;
        TimelineTrack currentTrack;
        final TimelineSelectable keyframe;
        float currentTime;

        AutomationKeyframeHandle(TimelineTrack track, TimelineSelectable keyframe) {
            this.originalTrack = track;
            this.currentTrack = track;
            this.keyframe = keyframe;
            this.currentTime = keyframe.getStartTime();
        }
    }
    
    /**
     * 标记拖动 Handle（用于存储拖动状态）
     */
    private static final class MarkerHandle {
        final com.chronoblocks.ui.timeline.model.TimelineMarker marker;
        float currentTime;

        MarkerHandle(com.chronoblocks.ui.timeline.model.TimelineMarker marker) {
            this.marker = marker;
            this.currentTime = marker.getTime();
        }
    }

    /**
     * ⭐ Path Clip 拖动 Handle（用于存储拖动状态）
     */
    private static final class PathClipHandle {
        final TimelineTrack track;
        final com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip clip;
        float tempStart;  // 拖动中的临时开始时间
        float tempEnd;    // 拖动中的临时结束时间

        PathClipHandle(TimelineTrack track, com.chronoblocks.ui.timeline.model.camera.path.CameraPathClip clip) {
            this.track = track;
            this.clip = clip;
            this.tempStart = clip.getStartTime();
            this.tempEnd = clip.getEndTime();
        }
    }

    /**
     * Action 涂抹 Handle（用于存储涂抹状态）
     */
    private static final class ActionPaintHandle {
        final TimelineTrack track;
        final EntityTrack entityTrack;
        final ActorActionType actionType;
        float startTime;
        float currentEndTime;

        ActionPaintHandle(TimelineTrack track, EntityTrack entityTrack, ActorActionType actionType, float startTime) {
            this.track = track;
            this.entityTrack = entityTrack;
            this.actionType = actionType;
            this.startTime = startTime;
            this.currentEndTime = startTime; // 初始时结束时间等于开始时间
        }
    }

    // ========== Action 涂抹逻辑 ==========

    /**
     * 检查是否可以开始 Action 涂抹
     * <p>条件：
     * <ul>
     *   <li>轨道必须是 EntityTrack</li>
     *   <li>必须有 EntityTrackPayload</li>
     *   <li>当前 Brush Action 必须被 RigProfile 支持</li>
     * </ul>
     *
     * @param track TimelineTrack
     * @return 如果可以涂抹则返回 true
     */
    private boolean canStartActionPaint(TimelineTrack track) {
        if (track == null || track.getType() != TrackType.ENTITY) {
            return false;
        }

        EntityTrack entityTrack = track.getEntityTrackPayload();
        if (entityTrack == null) {
            return false;
        }

        // 检查当前 Brush Action 是否被 RigProfile 支持
        ActorActionType brushAction = ActionBrushState.get();
        if (brushAction == null) {
            return false;
        }

        // 检查 RigProfile 支持（通过 ActorRef）
        com.chronoblocks.actor.ActorRef actorRef = entityTrack.getActorRef();
        if (actorRef == null) {
            return true; // 如果没有 ActorRef，允许涂抹（让导演先创建 Clip）
        }

        // 尝试获取 Actor 和 RigProfile
        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();

        java.util.Optional<Actor> actorOpt = actorRegistry.getActor(actorRef.getActorId());
        if (actorOpt.isEmpty()) {
            return true; // 如果找不到 Actor，允许涂抹
        }

        Actor actor = actorOpt.get();
        ActorRigProfile rig = actor.getRigProfile();
        if (rig == null) {
            return true; // 如果没有 RigProfile，允许涂抹
        }

        // 检查 RigProfile 是否支持当前 Action
        return rig.supports(brushAction);
    }

    /**
     * 开始 Action 涂抹
     */
    private void startActionPaint(TimelineHitResult hit, float mouseX, float contentX) {
        TimelineTrack track = hit.track();
        if (track == null || track.getType() != TrackType.ENTITY) {
            return;
        }

        EntityTrack entityTrack = track.getEntityTrackPayload();
        if (entityTrack == null) {
            // 如果 EntityTrackPayload 不存在，创建它
            entityTrack = track.getOrCreateEntityTrackPayload();
        }

        ActorActionType actionType = ActionBrushState.get();
        if (actionType == null) {
            return;
        }

        // 计算开始时间
        float startTime = renderer.xToTime(mouseX, contentX);
        startTime = Math.max(0f, startTime);

        // 创建 Handle
        ActionPaintHandle handle = new ActionPaintHandle(track, entityTrack, actionType, startTime);
        handle.currentEndTime = startTime; // 初始时结束时间等于开始时间

        // 开始交互
        interactionManager.startInteraction(
            InteractionMode.PAINT_ACTION,
            handle,
            mouseX, ImGui.getMousePosY(),
            startTime, startTime
        );

        LOGGER.debug("[Timeline] 开始 Action 涂抹: Action={}, StartTime={:.2f}s", actionType, startTime);
    }

    /**
     * Action 涂抹拖拽（更新结束时间）
     */
    private void paintAction(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof ActionPaintHandle handle)) {
            return;
        }

        // 计算当前结束时间
        float endTime = renderer.xToTime(mouseX, contentX);
        endTime = Math.max(0f, endTime);

        // 确保结束时间不小于开始时间（最小长度为 0.1 秒）
        if (endTime < handle.startTime) {
            endTime = handle.startTime;
        }
        if (endTime - handle.startTime < 0.1f) {
            endTime = handle.startTime + 0.1f;
        }

        handle.currentEndTime = endTime;

        // TODO: 这里可以添加预览渲染（拖拽过程中显示半透明色块）
    }

    /**
     * 应用 Action 涂抹结果（鼠标释放时调用）
     */
    private void applyActionPaint(ActionPaintHandle handle) {
        EntityTrack entityTrack = handle.entityTrack;
        ActorActionType actionType = handle.actionType;
        float startTime = handle.startTime;
        float endTime = handle.currentEndTime;

        if (endTime <= startTime) {
            // 如果结束时间不大于开始时间，不创建 Clip
            LOGGER.debug("[Timeline] Action 涂抹取消: 时间段过短 ({}s -> {}s)", startTime, endTime);
            return;
        }

        // 获取现有的 ActionClips
        java.util.List<ActorActionClip> existingClips = entityTrack.getActionClips();

        // 使用 ActionClipIntervalResolver 解析区间
        java.util.List<ActorActionClip> newClips = ActionClipIntervalResolver.resolve(
            existingClips,
            startTime,
            endTime,
            actionType
        );

        // 替换 ActionClips
        entityTrack.replaceActionClips(newClips);

        // 同步到 TimelineTrack（需要重新创建 EntityTimelineClip）
        // TODO: 这里需要确保 EntityTimelineClip 与 EntityClip 同步
        // 目前暂时依赖现有的同步机制

        LOGGER.info("[Timeline] Action 涂抹完成: Action={}, Time=[{:.2f}s, {:.2f}s], Clips={}",
            actionType, startTime, endTime, newClips.size());

        // 通知数据变化
        if (callbacks != null) {
            callbacks.onDataChanged();
        }
    }
}

