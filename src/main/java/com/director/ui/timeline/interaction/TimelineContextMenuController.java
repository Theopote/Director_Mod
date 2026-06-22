package com.director.ui.timeline.interaction;

import com.director.ui.timeline.menu.TimelineContextMenu;
import com.director.ui.timeline.menu.TimelineContextMenuFactory;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineMarker;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineSelectable;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.camera.CameraKeyframe;
import com.director.ui.timeline.model.camera.path.CameraPathClip;
import com.director.timeline.playback.camera.TimelineCameraController;
import com.director.timeline.playback.season.TimelineSeasonController;
import com.director.timeline.playback.time.TimelineWorldController;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiPopupFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间轴右键上下文菜单的打开与状态管理。
 */
public final class TimelineContextMenuController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineContextMenuController.class);

    public interface SelectionSync {
        void syncClipSelection(TimelineClip clip);

        void syncPropertiesSelection(Object target);
    }

    private final TimelineModel model;
    private TimelineContextMenuFactory menuFactory;
    private TimelineContextMenu activeContextMenu;
    private final SelectionSync selectionSync;

    public TimelineContextMenuController(TimelineModel model, SelectionSync selectionSync) {
        this.model = model;
        this.selectionSync = selectionSync;
    }

    public void setMenuFactory(TimelineContextMenuFactory menuFactory) {
        this.menuFactory = menuFactory;
    }

    public TimelineContextMenu getActiveContextMenu() {
        return activeContextMenu;
    }

    public void updateMenuState() {
        if (activeContextMenu != null) {
            String popupId = activeContextMenu.getPopupId();
            if (popupId != null && !ImGui.isPopupOpen(popupId, ImGuiPopupFlags.None)) {
                activeContextMenu = null;
                LOGGER.debug("[Timeline] 菜单已关闭，清理引用");
            }
        }
    }

    public void openForHit(TimelineHitResult hit) {
        if (menuFactory == null) {
            LOGGER.warn("[Timeline] menuFactory 为 null，无法打开上下文菜单");
            return;
        }

        if (hit == null) {
            LOGGER.warn("[Timeline] hit 为 null，无法打开上下文菜单");
            return;
        }

        if (!prepareSelectionForMenu(hit)) {
            return;
        }

        LOGGER.info("[Timeline] 尝试打开上下文菜单，hit.type = {}, track = {}, clip = {}",
                hit.type(), hit.track(), hit.clip());

        activeContextMenu = menuFactory.buildContextMenu(hit);
        if (activeContextMenu != null) {
            activeContextMenu.open();
            LOGGER.info("[Timeline] 成功打开上下文菜单 {}", hit.type());
        } else {
            LOGGER.warn("[Timeline] buildContextMenu 返回 null，hit.type() = {}", hit.type());
        }
    }

    /**
     * @return false if menu open should be aborted (e.g. locked/inactive track)
     */
    private boolean prepareSelectionForMenu(TimelineHitResult hit) {
        switch (hit.type()) {
            case CLIP -> {
                TimelineClip clip = hit.clip();
                if (clip != null) {
                    if (!model.isSelected(clip)) {
                        model.setSelectedClip(clip);
                    }
                    selectionSync.syncClipSelection(clip);
                }
            }
            case KEYFRAME -> {
                var clipKeyframe = hit.clipKeyframe();
                if (clipKeyframe != null) {
                    ImGuiIO io = ImGui.getIO();
                    boolean addToSelection = io.getKeyCtrl();
                    if (!model.isSelected(clipKeyframe)) {
                        model.selectItem(clipKeyframe, addToSelection);
                    }
                    selectionSync.syncPropertiesSelection(clipKeyframe);
                    LOGGER.debug("[Timeline] 右键选择 ClipKeyframe @ {}s", clipKeyframe.getTime());
                } else {
                    TimelineClip clip = hit.clip();
                    if (clip != null) {
                        if (!model.isSelected(clip)) {
                            model.setSelectedClip(clip);
                        }
                        selectionSync.syncClipSelection(clip);
                    }
                }
            }
            case AUTOMATION_KEYFRAME -> {
                TimelineSelectable keyframe = hit.automationKeyframe();
                TimelineTrack track = hit.track();

                if (keyframe != null && track != null) {
                    if (track.isLocked()) {
                        LOGGER.debug("[Timeline] 轨道已锁定，禁止右键操作关键帧 (轨道: {})", track.getName());
                        return false;
                    }

                    if (track.getType() == TrackType.CAMERA) {
                        TimelineTrack activeTrack = TimelineCameraController.getInstance().getActiveCameraTrack();
                        if (activeTrack != track) {
                            LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止右键操作关键帧 (轨道: {})", track.getName());
                            return false;
                        }
                    }

                    if (track.getType() == TrackType.TIME) {
                        TimelineTrack activeTrack = TimelineWorldController.getInstance().getActiveTimeTrack();
                        if (activeTrack != track) {
                            LOGGER.debug("[Timeline] 时间轨道未激活，禁止右键操作关键帧 (轨道: {})", track.getName());
                            return false;
                        }
                    }

                    if (track.getType() == TrackType.SEASON) {
                        TimelineTrack activeTrack = TimelineSeasonController.getInstance().getActiveSeasonTrack();
                        if (activeTrack != track) {
                            LOGGER.debug("[Timeline] 季节轨道未激活，禁止右键操作关键帧 (轨道: {})", track.getName());
                            return false;
                        }
                    }

                    ImGuiIO io = ImGui.getIO();
                    boolean addToSelection = io.getKeyCtrl();
                    if (!model.isSelected(keyframe)) {
                        model.selectItem(keyframe, addToSelection);
                    }
                    selectionSync.syncPropertiesSelection(keyframe);
                    if (keyframe instanceof CameraKeyframe ck) {
                        TimelineCameraController.getInstance().previewAt(ck.getTime());
                        LOGGER.debug("[Timeline] 右键摄像机关键帧 @ {}s，准备弹出关键帧菜单", ck.getTime());
                    }
                }
            }
            case MARKER -> {
                TimelineMarker marker = (TimelineMarker) hit.automationKeyframe();
                if (marker != null) {
                    if (!model.isSelected(marker)) {
                        model.selectItem(marker, false);
                    }
                    selectionSync.syncPropertiesSelection(marker);
                }
            }
            case PATH_CLIP -> {
                PathClipHit pathClipHit = hit.pathClipHit();
                if (pathClipHit != null) {
                    CameraPathClip clip = pathClipHit.clip();
                    TimelineTrack track = pathClipHit.track();

                    if (clip != null && track != null) {
                        if (track.isLocked()) {
                            LOGGER.debug("[Timeline] 轨道已锁定，禁止右键操作 Path Clip (轨道: {})", track.getName());
                            return false;
                        }

                        if (track.getType() == TrackType.CAMERA) {
                            TimelineTrack activeTrack = TimelineCameraController.getInstance().getActiveCameraTrack();
                            if (activeTrack != track) {
                                LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止右键操作 Path Clip (轨道: {})", track.getName());
                                return false;
                            }
                        }

                        if (!model.isSelected(clip)) {
                            model.selectItem(clip, false);
                        }
                        selectionSync.syncPropertiesSelection(clip);
                    }
                }
            }
            case EMPTY, NONE, TRACK_HEADER -> {
                // 空白区域右键：保持现有选择
            }
        }
        return true;
    }
}
