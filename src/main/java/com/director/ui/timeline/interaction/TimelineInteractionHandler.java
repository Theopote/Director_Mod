package com.director.ui.timeline.interaction;

import com.director.core.DirectorServices;
import com.director.ui.timeline.interaction.TimelineDragHandles.ActionPaintHandle;
import com.director.ui.timeline.interaction.TimelineDragHandles.AutomationKeyframeHandle;
import com.director.ui.timeline.interaction.TimelineDragHandles.ClipHandle;
import com.director.ui.timeline.interaction.TimelineDragHandles.MarkerHandle;
import com.director.ui.timeline.interaction.TimelineDragHandles.PathClipHandle;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.director.ui.timeline.interaction.TimelineTrackHitTest.TrackControlHitResult;
import com.director.ui.timeline.menu.TimelineContextMenu;
import com.director.ui.timeline.menu.TimelineContextMenuFactory;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineSelectable;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.camera.path.CameraPathClip;
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.selection.TimelineKeyframeMultiSelection;
import com.director.ui.timeline.render.TimelineRenderer;
import com.director.ui.timeline.render.TimelineTrackControlHit;
import com.director.ui.timeline.snapping.TimelineSnapping;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImDrawList;
import imgui.flag.ImGuiMouseButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Director TimelineInteractionHandler锛圫napping + Renderer v2�?
 *
 * <p>鑱岃矗锛氬皢 ImGui 杈撳叆杞崲涓哄�?TimelineModel 鐨勪慨鏀癸紝鍚屾椂缁熶竴浣跨�?
 * TimelineRenderer 鐨勫潗鏍囩郴缁熶�?TimelineSnapping 鐨勭鍚稿伐鍏枫�?/p>
 */
public class TimelineInteractionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineInteractionHandler.class);

    private static final float DOUBLE_CLICK_THRESHOLD = 0.25f;
    
    private final TimelineModel model;
    private final TimelineInteractionManager interactionManager;
    @SuppressWarnings("unused")
    private final TimelineCommandHistory commandHistory;
    private final TimelineRenderer renderer;
    
    private InteractionCallbacks callbacks;

    private final TimelineSnapGuideState snapGuideState = new TimelineSnapGuideState();
    private final TimelineMarqueeSelector marqueeSelector;
    private final TimelinePlayheadMarkerController playheadMarkerController;
    private final TimelineActionPaintHandler actionPaintHandler;
    private final TimelineContextMenuController contextMenuController;
    private final TimelineClipDragHandler clipDragHandler;
    private final TimelineKeyframeDragHandler keyframeDragHandler;
    private final TimelinePathClipDragHandler pathClipDragHandler;

    private float lastClickTime = 0f;
    private float lastClickX = 0f;
    private float lastClickY = 0f;

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
        this.marqueeSelector = new TimelineMarqueeSelector(model, renderer, interactionManager,
                new TimelineMarqueeSelector.SelectionSync() {
                    @Override
                    public void onSelectionCleared() {
                        DirectorServices.get().uiSelection().clearSelection();
                    }

                    @Override
                    public void onMarqueeSelectionApplied(List<Object> selected, Object lastSelected) {
                        syncPropertiesSelection(lastSelected);
                        if (callbacks != null) {
                            callbacks.onDataChanged();
                        }
                    }
                });
        this.playheadMarkerController = new TimelinePlayheadMarkerController(
                model, interactionManager, renderer, commandHistory,
                new TimelinePlayheadMarkerController.SnapGuide() {
                    @Override
                    public void update(TimelineSnapping.Result snap) {
                        snapGuideState.update(snap);
                    }

                    @Override
                    public void clear() {
                        snapGuideState.clear();
                    }
                },
                () -> {
                    if (callbacks != null) {
                        callbacks.onDataChanged();
                    }
                });
        this.actionPaintHandler = new TimelineActionPaintHandler(
                interactionManager, renderer,
                () -> {
                    if (callbacks != null) {
                        callbacks.onDataChanged();
                    }
                });
        this.contextMenuController = new TimelineContextMenuController(model,
                new TimelineContextMenuController.SelectionSync() {
                    @Override
                    public void syncClipSelection(TimelineClip clip) {
                        TimelineInteractionHandler.this.syncClipSelection(clip);
                    }

                    @Override
                    public void syncPropertiesSelection(Object target) {
                        TimelineInteractionHandler.this.syncPropertiesSelection(target);
                    }
                });

        this.clipDragHandler = new TimelineClipDragHandler(
                model, interactionManager, commandHistory, renderer, snapGuideState,
                new TimelineClipDragHandler.Callbacks() {
                    @Override
                    public void onDataChanged() {
                        if (callbacks != null) {
                            callbacks.onDataChanged();
                        }
                    }

                    @Override
                    public void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime) {
                        if (callbacks != null) {
                            callbacks.onClipInteractionFinished(clip, originalStartTime, originalEndTime);
                        }
                    }

                    @Override
                    public void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
                        if (callbacks != null) {
                            callbacks.onKeyframeDoubleClick(clip, mouseX, contentX);
                        }
                    }
                },
                this::syncClipSelection);

        this.keyframeDragHandler = new TimelineKeyframeDragHandler(
                model, interactionManager, commandHistory, renderer, snapGuideState,
                new TimelineKeyframeDragHandler.Callbacks() {
                    @Override
                    public void onDataChanged() {
                        if (callbacks != null) {
                            callbacks.onDataChanged();
                        }
                    }

                    @Override
                    public void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
                        if (callbacks != null) {
                            callbacks.onKeyframeDoubleClick(clip, mouseX, contentX);
                        }
                    }
                },
                this::syncPropertiesSelection);

        this.pathClipDragHandler = new TimelinePathClipDragHandler(
                model, interactionManager, commandHistory, renderer, snapGuideState,
                () -> {
                    if (callbacks != null) {
                        callbacks.onDataChanged();
                    }
                },
                this::syncPropertiesSelection);
    }
    
    public void setCallbacks(InteractionCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void setMenuFactory(TimelineContextMenuFactory menuFactory) {
        contextMenuController.setMenuFactory(menuFactory);
    }

    public TimelineContextMenu getActiveContextMenu() {
        return contextMenuController.getActiveContextMenu();
    }

    // ========== 吸附参考线查询（供 Renderer 使用）==========
    public boolean isSnapGuideActive() {
        return snapGuideState.isActive();
    }

    public float getSnapGuideTime() {
        return snapGuideState.getTime();
    }

    public boolean isSnapGuideToGrid() {
        return snapGuideState.isToGrid();
    }

    public boolean isSnapGuideToClip() {
        return snapGuideState.isToClip();
    }

    public boolean isSnapGuideToKeyframe() {
        return snapGuideState.isToKeyframe();
    }

    private void updateSnapGuide(TimelineSnapping.Result snap) {
        snapGuideState.update(snap);
    }

    private void clearSnapGuide() {
        snapGuideState.clear();
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
        DirectorServices.get().uiSelection().setCurrentSelection(track);
        
        LOGGER.debug("[TimelineInteractionHandler] 外部选中 Track: {}", track.getName());
    }
    
    /**
     * ⭐ 修复：检查并清理已关闭的菜单
     * 应该在每帧渲染时调用，确保菜单状态正确
     */
    public void updateMenuState() {
        contextMenuController.updateMenuState();
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
        return keyframeDragHandler.getDraggedAutomationKeyframeTime(keyframe);
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
    public float getDraggedMarkerTime(com.director.ui.timeline.model.TimelineMarker marker) {
        return playheadMarkerController.getDraggedMarkerTime(marker);
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
        return keyframeDragHandler.getDraggedClipKeyframeTime(clip, keyframeTime);
    }
    
    /**
     * ⭐ 获取正在拖动中的 Path Clip 的临时开始时间（用于渲染系统）
     */
    public float getDraggedPathClipStartTime(CameraPathClip clip) {
        return pathClipDragHandler.getDraggedPathClipStartTime(clip);
    }

    public float getDraggedPathClipEndTime(CameraPathClip clip) {
        return pathClipDragHandler.getDraggedPathClipEndTime(clip);
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
        Object active = interactionManager.getActiveObject();
        if ((mode == InteractionMode.DRAG_CLIP
                || mode == InteractionMode.RESIZE_CLIP_LEFT
                || mode == InteractionMode.RESIZE_CLIP_RIGHT)
                && active instanceof ClipHandle handle) {
            clipDragHandler.cancelSilently(handle);
        } else if ((mode == InteractionMode.DRAG_PATH_CLIP
                || mode == InteractionMode.RESIZE_PATH_CLIP_LEFT
                || mode == InteractionMode.RESIZE_PATH_CLIP_RIGHT)
                && active instanceof PathClipHandle handle) {
            pathClipDragHandler.cancelSilently(handle);
        }
        if (interactionManager.isInteracting(InteractionMode.DRAG_AUTOMATION_KEYFRAME)) {
            if (active instanceof AutomationKeyframeHandle handle
                    && handle.keyframe instanceof com.director.ui.timeline.model.audio.AudioEvent
                    && handle.currentTrack != handle.originalTrack) {
                handle.currentTrack = handle.originalTrack;
            }
        }
        clearSnapGuide();
        // 清理音频实时预览（避免右键取消交互后预览残留）
        com.director.ui.timeline.audio.TimelineAudioService.getInstance().endAllClipTimeRangePreviews();
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
        marqueeSelector.update(contentX, trackAreaTop, scrollY);
        
        // ⭐ v5.0：显示轨道控制按钮的 tooltip
        renderTrackControlTooltips(trackControlHit);
        
        // 注意：容器拖放处理现在在 TimelinePanel 中直接处理（�?invisibleButton 之后�?
        // 这样可以确保在正确的窗口上下文中设置拖放目标
    }

    /**
     * 渲染框选矩形（在 TimelinePanel 里调用，确保在正确的 drawList/clipRect 下绘制）
     */
    public void renderMarqueeOverlay(ImDrawList drawList) {
        marqueeSelector.renderOverlay(drawList);
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
            DirectorServices.get().uiSelection().clearSelection();
            return;
        }
        if (count == 1) {
            DirectorServices.get().uiSelection()
                    .setCurrentSelection(resolveSelectionTarget(lastFocused));
            return;
        }

        List<com.director.ui.timeline.model.clip.ClipKeyframe> clipKeyframes = new ArrayList<>();
        List<TimelineSelectable> automationKeyframes = new ArrayList<>();

        for (Object it : model.getSelectedItems()) {
            if (it instanceof com.director.ui.timeline.model.clip.ClipKeyframe ckf) {
                clipKeyframes.add(ckf);
            } else if (it instanceof TimelineSelectable selectable && !(it instanceof TimelineClip)) {
                automationKeyframes.add(selectable);
            }
        }

        if (!clipKeyframes.isEmpty() || !automationKeyframes.isEmpty()) {
            DirectorServices.get().uiSelection()
                    .setCurrentSelection(new TimelineKeyframeMultiSelection(clipKeyframes, automationKeyframes));
        } else {
            // 多选但不是关键帧（例如多选片段）：保持 lastFocused
            DirectorServices.get().uiSelection()
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
        DirectorServices.get().uiSelection()
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
                com.director.timeline.playback.camera.TimelineCameraController controller =
                        com.director.timeline.playback.camera.TimelineCameraController.getInstance();
                boolean isActive = controller.getActiveCameraTrack() == track;
                tooltip = isActive ? "当前激活的摄像机轨道（点击切换到其他轨道）" : "设为当前激活的摄像机轨道";
            }
            case ACTIVE_TIME -> {
                com.director.timeline.playback.time.TimelineWorldController controller =
                        com.director.timeline.playback.time.TimelineWorldController.getInstance();
                boolean isActive = controller.getActiveTimeTrack() == track;
                tooltip = isActive ? "当前激活的时间轨道（点击切换到其他轨道）" : "设为当前激活的时间轨道";
            }
            case ACTIVE_SEASON -> {
                com.director.timeline.playback.season.TimelineSeasonController controller =
                        com.director.timeline.playback.season.TimelineSeasonController.getInstance();
                boolean isActive = controller.getActiveSeasonTrack() == track;
                tooltip = isActive ? "当前激活的季节轨道（点击切换到其他轨道）" : "设为当前激活的季节轨道";
            }
            case ACTIVE_WEATHER -> {
                com.director.timeline.playback.weather.TimelineWeatherController controller =
                        com.director.timeline.playback.weather.TimelineWeatherController.getInstance();
                boolean isActive = controller.getActiveWeatherTrack() == track;
                tooltip = isActive ? "当前激活的天气轨道（点击切换到其他轨道）" : "设为当前激活的天气轨道";
            }
            case ADD_KEYFRAME -> {
                com.director.timeline.model.TrackType trackType = track.getType();
                if (trackType == com.director.timeline.model.TrackType.CONTAINER) {
                    tooltip = "容器轨道不支持轨道级关键帧，请使用片段内的动画配置";
                } else if (trackType == com.director.timeline.model.TrackType.CAMERA) {
                    tooltip = "添加摄像机关键帧（需位于自定义路径片段内）";
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
                    com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                            .setActiveCameraTrack(track);
            case ACTIVE_TIME -> // ⭐ v3.5：设置激活时间轨道
                    com.director.timeline.playback.time.TimelineWorldController.getInstance()
                            .setActiveTimeTrack(track);
            case ACTIVE_SEASON -> // ⭐ v1.1：设置激活季节轨道
                    com.director.timeline.playback.season.TimelineSeasonController.getInstance()
                            .setActiveSeasonTrack(track);
            case ACTIVE_WEATHER -> // ⭐ v1.0：设置激活天气轨道
                    com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
                            .bindActiveTrack(track);
                }
                // 控件点击不再向下传递，不影�?Clip / Keyframe 选择
                return;
            }

            // 1) 记录 double-click 信息
            float now = (float) ImGui.getTime();
            boolean isDoubleClick = (now - lastClickTime) <= DOUBLE_CLICK_THRESHOLD
                    && Math.hypot(mouseX - lastClickX, mouseY - lastClickY) < 5.0f;

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
                    com.director.ui.timeline.model.TimelineMarker marker = 
                        (com.director.ui.timeline.model.TimelineMarker) hit.automationKeyframe();
                    if (marker != null) {
                        model.selectItem(marker, false);
                        syncPropertiesSelection(marker);
                    }
                }
                case EMPTY -> marqueeSelector.startCandidate(mouseX, mouseY);
                default -> { /* NONE / TRACK_HEADER 在这里忽�?*/ }
            }
        }

        // 右键打开上下文菜单（如果不是点在轨道头控件上�?
        // 使用 rightClickedOnCanvas 参数，因�?invisibleButton 会拦截右键事�?
        // 右键：统一使用 ImGui 的点击检测
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            if (interactionManager.isInteracting()) {
                endInteractionIfNeeded();
                return;
            }
            
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

    private void updateMouseCursor(TimelineHitResult hit, float contentX, float mouseX) {
        if (interactionManager.isInteracting()) {
            return;
        }
        clipDragHandler.updateResizeCursor(hit, contentX, mouseX);
        pathClipDragHandler.updateResizeCursor(hit, contentX, mouseX);
    }

    private void onClipClicked(TimelineHitResult hit, float mouseX, float contentX, boolean isDoubleClick) {
        clipDragHandler.onClipClicked(hit, mouseX, contentX, isDoubleClick);
    }

    private void onKeyframeClicked(TimelineHitResult hit, float mouseX, float contentX, boolean isDoubleClick) {
        keyframeDragHandler.onKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
    }

    private void onAutomationKeyframeClicked(TimelineHitResult hit, float mouseX, float contentX, boolean isDoubleClick) {
        keyframeDragHandler.onAutomationKeyframeClicked(hit, mouseX, contentX, isDoubleClick);
    }

    private void onPathClipClicked(TimelineHitResult hit, float mouseX, float contentX, boolean isDoubleClick) {
        pathClipDragHandler.onPathClipClicked(hit, mouseX, contentX, isDoubleClick);
    }

    private void openContextMenuForHit(TimelineHitResult hit) {
        contextMenuController.openForHit(hit);
    }

    private void endInteractionIfNeeded() {
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode == InteractionMode.NONE) {
            return;
        }
        Object obj = interactionManager.getActiveObject();
        if (!clipDragHandler.finishInteraction(mode, obj)
                && !keyframeDragHandler.finishInteraction(mode, obj)
                && !pathClipDragHandler.finishInteraction(mode, obj)) {
            if (mode == InteractionMode.PAINT_ACTION && obj instanceof ActionPaintHandle handle) {
                actionPaintHandler.applyActionPaint(handle);
            } else if (mode == InteractionMode.DRAG_MARKER && obj instanceof MarkerHandle handle) {
                playheadMarkerController.finishMarkerDragOnRelease(handle);
        } else if (mode == InteractionMode.DRAG_PLAYHEAD) {
                playheadMarkerController.finishPlayheadDrag();
            }
        }
        clearSnapGuide();
        interactionManager.endInteraction();
    }

    private void handleDrag(TimelineHitResult hit,
                            float contentX,
                            float trackAreaTop,
                            float contentW,
                            float viewportH,
                            float scrollY) {
        boolean isLeftMouseDown = ImGui.isMouseDown(ImGuiMouseButton.Left);
        boolean isRightMouseDown = ImGui.isMouseDown(ImGuiMouseButton.Right);
        if (!isLeftMouseDown && !isRightMouseDown || !interactionManager.isInteracting()) {
            return;
        }
        if (isRightMouseDown && !interactionManager.isInteracting(InteractionMode.DRAG_MARKER)) {
            return;
        }
        InteractionMode mode = interactionManager.getCurrentMode();
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        switch (mode) {
            case DRAG_CLIP -> clipDragHandler.dragClip(mouseX, contentX, mouseY, trackAreaTop);
            case RESIZE_CLIP_LEFT -> clipDragHandler.resizeClipLeft(mouseX, contentX);
            case RESIZE_CLIP_RIGHT -> clipDragHandler.resizeClipRight(mouseX, contentX);
            case DRAG_KEYFRAME -> keyframeDragHandler.dragKeyframe(mouseX, contentX);
            case DRAG_AUTOMATION_KEYFRAME -> keyframeDragHandler.dragAutomationKeyframe(mouseX, contentX, mouseY, trackAreaTop, scrollY);
            case DRAG_MARKER -> playheadMarkerController.dragMarker(mouseX, contentX);
            case DRAG_PLAYHEAD -> playheadMarkerController.dragPlayhead(mouseX, contentX);
            case PAINT_ACTION -> actionPaintHandler.paintAction(mouseX, contentX);
            case DRAG_PATH_CLIP -> pathClipDragHandler.dragPathClip(mouseX, contentX);
            case RESIZE_PATH_CLIP_LEFT -> pathClipDragHandler.resizePathClipLeft(mouseX, contentX);
            case RESIZE_PATH_CLIP_RIGHT -> pathClipDragHandler.resizePathClipRight(mouseX, contentX);
            default -> { }
        }
    }

    public void startPlayheadDrag(float mouseX, float contentX) {
        playheadMarkerController.startPlayheadDrag(mouseX, contentX);
    }

    public void clickTimeRuler(float mouseX, float contentX) {
        playheadMarkerController.clickTimeRuler(mouseX, contentX);
    }

    public void startMarkerDrag(com.director.ui.timeline.model.TimelineMarker marker, float mouseX, float contentX) {
        playheadMarkerController.startMarkerDrag(marker, mouseX, contentX);
    }

    public void updateMarkerDrag(float mouseX, float contentX) {
        playheadMarkerController.updateMarkerDrag(mouseX, contentX);
    }

    public void endMarkerDrag() {
        playheadMarkerController.endMarkerDrag();
    }

    public boolean isDraggingMarker() {
        return playheadMarkerController.isDraggingMarker();
    }
}
