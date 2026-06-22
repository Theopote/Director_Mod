package com.director.ui.timeline.menu;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.timeline.commands.RemoveAutomationKeyframeCommand;
import com.director.ui.timeline.commands.RemoveCameraPathKeyframeCommand;
import com.director.ui.timeline.commands.BatchDeleteSelectionCommand;
import com.director.ui.timeline.commands.BatchChangeCameraKeyframeKeyTypeCommand;
import com.director.ui.timeline.commands.BatchChangeCameraKeyframeSegmentTypeCommand;
import com.director.ui.timeline.commands.BatchChangeCameraKeyframeRotationInterpolationCommand;
import com.director.ui.timeline.commands.BatchChangeCameraKeyframeHandleModeCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.playback.camera.TimelineCameraController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * CameraKeyframeContextMenu v1.0
 *
 * <p>右键点击摄像机关键帧时弹出的菜单。
 * 风格参考 Blender / Mine-imator：
 * <ul>
 *   <li>✓ 关键帧类型（Standard / Corner / Hold / Smooth / Manual Bezier）</li>
 *   <li>✓ 位置插值类型（Linear / Catmull-Rom / Bezier / Step）</li>
 *   <li>✓ 旋转插值类型（Default / Linear / Step）</li>
 *   <li>✓ 控制柄（自动/自由/对齐，清除手柄）</li>
 *   <li>✓ 实用功能：跳转到关键帧位置、删除、关闭菜单</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class CameraKeyframeContextMenu extends TimelineContextMenu {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraKeyframeContextMenu.class);
    
    private final CameraKeyframe keyframe;
    private final TimelineTrack track;
    private final TimelineModel model;

    public CameraKeyframeContextMenu(
            TimelineCommandHistory commandHistory,
            CameraKeyframe keyframe,
            TimelineTrack track,
            TimelineModel model) {
        super(commandHistory);
        this.keyframe = keyframe;
        this.track = track;
        this.model = model;
        this.setPopupId("CameraKeyframeContextMenu");
        buildMenu();
    }

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    private void buildMenu() {
        addDisabledItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.KEYFRAME_AT, keyframe.getTime()));

        List<CameraKeyframe> selectedCameraKeyframes = new ArrayList<>();
        for (Object it : model.getSelectedItems()) {
            if (it instanceof CameraKeyframe ckf) {
                selectedCameraKeyframes.add(ckf);
            }
        }
        boolean isMulti = selectedCameraKeyframes.size() > 1;
        if (isMulti) {
            addDisabledItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.MULTI_SELECT, selectedCameraKeyframes.size()));
        }
        
        boolean isLocked = track.isLocked();
        boolean isActive = true;
        if (track.getType() == com.director.timeline.model.TrackType.CAMERA) {
            com.director.timeline.playback.camera.TimelineCameraController controller =
                    com.director.timeline.playback.camera.TimelineCameraController.getInstance();
            com.director.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            isActive = (activeTrack == track);
        }
        
        if (isLocked) {
            addDisabledItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.TRACK_LOCKED));
            return;
        }
        
        if (!isActive) {
            addDisabledItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.TRACK_INACTIVE));
            return;
        }
        
        addSeparator();

        TimelineContextMenu keyTypeMenu = new TimelineContextMenu(getCommandHistory());
        keyTypeMenu.setPopupId("CameraKeyframeKeyTypeMenu");
        
        addKeyTypeItem(keyTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_STANDARD), CameraKeyframe.KeyType.STANDARD, selectedCameraKeyframes);
        addKeyTypeItem(keyTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_CORNER), CameraKeyframe.KeyType.CORNER, selectedCameraKeyframes);
        addKeyTypeItem(keyTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_HOLD), CameraKeyframe.KeyType.HOLD, selectedCameraKeyframes);
        addKeyTypeItem(keyTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_SMOOTH), CameraKeyframe.KeyType.SMOOTH, selectedCameraKeyframes);
        addKeyTypeItem(keyTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_MANUAL_BEZIER), CameraKeyframe.KeyType.MANUAL_BEZIER, selectedCameraKeyframes);
        
        addSubmenu(tr(DirectorLang.Timeline.CameraKeyframeMenu.KEY_TYPE_SUBMENU), keyTypeMenu);

        TimelineContextMenu segmentTypeMenu = new TimelineContextMenu(getCommandHistory());
        segmentTypeMenu.setPopupId("CameraKeyframeSegmentTypeMenu");
        
        addSegmentTypeItem(segmentTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_DEFAULT), CameraKeyframe.SegmentPathType.DEFAULT, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_LINEAR), CameraKeyframe.SegmentPathType.LINEAR, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_CATMULL_ROM), CameraKeyframe.SegmentPathType.CATMULL_ROM, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_BEZIER), CameraKeyframe.SegmentPathType.BEZIER, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_STEP), CameraKeyframe.SegmentPathType.STEP, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_HOLD), CameraKeyframe.SegmentPathType.HOLD, selectedCameraKeyframes);
        
        addSubmenu(tr(DirectorLang.Timeline.CameraKeyframeMenu.SEGMENT_TYPE_SUBMENU), segmentTypeMenu);

        TimelineContextMenu rotationTypeMenu = new TimelineContextMenu(getCommandHistory());
        rotationTypeMenu.setPopupId("CameraKeyframeRotationTypeMenu");
        
        addRotationTypeItem(rotationTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.ROTATION_DEFAULT),
                CameraKeyframe.RotationInterpolation.DEFAULT, selectedCameraKeyframes);
        addRotationTypeItem(rotationTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.ROTATION_LINEAR),
                CameraKeyframe.RotationInterpolation.LINEAR, selectedCameraKeyframes);
        addRotationTypeItem(rotationTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.ROTATION_BEZIER),
                CameraKeyframe.RotationInterpolation.BEZIER, selectedCameraKeyframes);
        addRotationTypeItem(rotationTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.ROTATION_CATMULL_ROM),
                CameraKeyframe.RotationInterpolation.CATMULL_ROM, selectedCameraKeyframes);
        addRotationTypeItem(rotationTypeMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.ROTATION_STEP),
                CameraKeyframe.RotationInterpolation.STEP, selectedCameraKeyframes);
        
        addSubmenu(tr(DirectorLang.Timeline.CameraKeyframeMenu.ROTATION_TYPE_SUBMENU), rotationTypeMenu);

        if (keyframe.getKeyType() == CameraKeyframe.KeyType.MANUAL_BEZIER ||
            keyframe.getSegmentPathType() == CameraKeyframe.SegmentPathType.BEZIER) {
            
            TimelineContextMenu handleMenu = new TimelineContextMenu(getCommandHistory());
            handleMenu.setPopupId("CameraKeyframeHandleMenu");
            
            addHandleModeItem(handleMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.HANDLE_AUTO), CameraKeyframe.HandleMode.AUTO, selectedCameraKeyframes);
            addHandleModeItem(handleMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.HANDLE_FREE), CameraKeyframe.HandleMode.FREE, selectedCameraKeyframes);
            addHandleModeItem(handleMenu, tr(DirectorLang.Timeline.CameraKeyframeMenu.HANDLE_ALIGNED), CameraKeyframe.HandleMode.ALIGNED, selectedCameraKeyframes);
            
            handleMenu.addSeparator();
            handleMenu.addItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.CLEAR_HANDLES), () -> {
                getCommandHistory().executeCommand(
                    new com.director.ui.timeline.commands.ChangeBezierHandleCommand(
                        model, track, keyframe,
                        new org.joml.Vector3f(0, 0, 0),
                        new org.joml.Vector3f(0, 0, 0)
                    )
                );
                TimelineCameraController.getInstance().onKeyframeChanged();
                LOGGER.debug("[Timeline] 清除摄像机关键帧控制柄");
            });
            
            addSubmenu(tr(DirectorLang.Timeline.CameraKeyframeMenu.HANDLE_SUBMENU), handleMenu);
        }

        addSeparator();

        addItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.JUMP_TO_KEYFRAME), () -> {
            TimelineCameraController.getInstance().previewAt(keyframe.getTime());
            LOGGER.debug("[Timeline] 跳转到摄像机关键帧 @ {}s", keyframe.getTime());
        });

        if (isMulti) {
            addItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.DELETE_SELECTED), () -> {
                getCommandHistory().executeCommand(new BatchDeleteSelectionCommand(
                        model,
                        new ArrayList<>(selectedCameraKeyframes)
                ));
                TimelineCameraController.getInstance().onKeyframeChanged();
                LOGGER.debug("[Timeline] 批量删除摄像机关键帧: {}", selectedCameraKeyframes.size());
            });
        }

        com.director.timeline.model.camera.path.CustomPathClip ownerPathClip =
                model.findCustomPathClipForKeyframe(keyframe);
        if (ownerPathClip != null) {
            addCommandItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.DELETE_KEYFRAME),
                    new RemoveCameraPathKeyframeCommand(model, ownerPathClip, keyframe),
                    "Del");
        } else {
            addCommandItem(tr(DirectorLang.Timeline.CameraKeyframeMenu.DELETE_KEYFRAME),
                    new RemoveAutomationKeyframeCommand(model, track, keyframe),
                    "Del");
        }
    }

    private void addKeyTypeItem(TimelineContextMenu menu, String label, CameraKeyframe.KeyType type, List<CameraKeyframe> selected) {
        boolean isSelected = keyframe.getKeyType() == type;
        String displayLabel = isSelected ? "✓ " + label : label;
        menu.addItem(displayLabel, () -> {
            if (selected != null && selected.size() > 1) {
                getCommandHistory().executeCommand(new BatchChangeCameraKeyframeKeyTypeCommand(model, selected, type));
            } else {
                getCommandHistory().executeCommand(new BatchChangeCameraKeyframeKeyTypeCommand(model, java.util.List.of(keyframe), type));
            }
        });
    }

    private void addSegmentTypeItem(TimelineContextMenu menu, String label, CameraKeyframe.SegmentPathType type, List<CameraKeyframe> selected) {
        boolean isSelected = keyframe.getSegmentPathType() == type;
        String displayLabel = isSelected ? "✓ " + label : label;
        menu.addItem(displayLabel, () -> {
            if (selected != null && selected.size() > 1) {
                getCommandHistory().executeCommand(new BatchChangeCameraKeyframeSegmentTypeCommand(model, selected, type));
            } else {
                getCommandHistory().executeCommand(new BatchChangeCameraKeyframeSegmentTypeCommand(model, java.util.List.of(keyframe), type));
            }
        });
    }

    private void addRotationTypeItem(TimelineContextMenu menu, String label, CameraKeyframe.RotationInterpolation type, List<CameraKeyframe> selected) {
        boolean isSelected = keyframe.getRotationInterpolation() == type;
        String displayLabel = isSelected ? "✓ " + label : label;
        menu.addItem(displayLabel, () -> {
            if (selected != null && selected.size() > 1) {
                getCommandHistory().executeCommand(new BatchChangeCameraKeyframeRotationInterpolationCommand(model, selected, type));
            } else {
                getCommandHistory().executeCommand(new BatchChangeCameraKeyframeRotationInterpolationCommand(model, java.util.List.of(keyframe), type));
            }
        });
    }

    private void addHandleModeItem(TimelineContextMenu menu, String label, CameraKeyframe.HandleMode mode, List<CameraKeyframe> selected) {
        boolean isSelected = keyframe.getHandleMode() == mode;
        String displayLabel = isSelected ? "✓ " + label : label;
        menu.addItem(displayLabel, () -> {
            if (selected != null && selected.size() > 1) {
                getCommandHistory().executeCommand(new BatchChangeCameraKeyframeHandleModeCommand(model, selected, mode));
            } else {
                getCommandHistory().executeCommand(new BatchChangeCameraKeyframeHandleModeCommand(model, java.util.List.of(keyframe), mode));
            }
            LOGGER.debug("[Timeline] 设置摄像机关键帧控制柄模式: {}", mode);
        });
    }
}
