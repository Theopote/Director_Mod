package com.chronoblocks.ui.timeline.menu;

import com.chronoblocks.ui.timeline.commands.RemoveAutomationKeyframeCommand;
import com.chronoblocks.ui.timeline.commands.BatchRemoveAutomationKeyframesCommand;
import com.chronoblocks.ui.timeline.commands.BatchChangeCameraKeyframeKeyTypeCommand;
import com.chronoblocks.ui.timeline.commands.BatchChangeCameraKeyframeSegmentTypeCommand;
import com.chronoblocks.ui.timeline.commands.BatchChangeCameraKeyframeRotationInterpolationCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
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
 * @author ChronoBlocks Team
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

    private void buildMenu() {
        // 标题：显示关键帧信息
        addDisabledItem(String.format("摄像机关键帧 @ %.3fs", keyframe.getTime()));

        // 多选：当前选择里是否包含多个 CameraKeyframe
        List<CameraKeyframe> selectedCameraKeyframes = new ArrayList<>();
        for (Object it : model.getSelectedItems()) {
            if (it instanceof CameraKeyframe ckf) {
                selectedCameraKeyframes.add(ckf);
            }
        }
        boolean isMulti = selectedCameraKeyframes.size() > 1;
        if (isMulti) {
            addDisabledItem(String.format("多选：%d 个关键帧", selectedCameraKeyframes.size()));
        }
        
        // ⭐ v5.0：检查轨道状态，如果锁定或未激活，禁用所有操作
        boolean isLocked = track.isLocked();
        boolean isActive = true;
        if (track.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA) {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            isActive = (activeTrack == track);
        }
        
        if (isLocked) {
            addDisabledItem("轨道已锁定，无法操作");
            return; // 锁定状态下不显示任何操作菜单
        }
        
        if (!isActive) {
            addDisabledItem("轨道未激活，无法操作");
            return; // 未激活状态下不显示任何操作菜单
        }
        
        addSeparator();

        // 关键帧类型菜单
        TimelineContextMenu keyTypeMenu = new TimelineContextMenu(getCommandHistory());
        keyTypeMenu.setPopupId("CameraKeyframeKeyTypeMenu");
        
        addKeyTypeItem(keyTypeMenu, "Standard（普通）", CameraKeyframe.KeyType.STANDARD, selectedCameraKeyframes);
        addKeyTypeItem(keyTypeMenu, "Corner（锐角）", CameraKeyframe.KeyType.CORNER, selectedCameraKeyframes);
        addKeyTypeItem(keyTypeMenu, "Hold（保持）", CameraKeyframe.KeyType.HOLD, selectedCameraKeyframes);
        addKeyTypeItem(keyTypeMenu, "Smooth（平滑）", CameraKeyframe.KeyType.SMOOTH, selectedCameraKeyframes);
        addKeyTypeItem(keyTypeMenu, "Manual Bezier（手动贝塞尔）", CameraKeyframe.KeyType.MANUAL_BEZIER, selectedCameraKeyframes);
        
        addSubmenu("关键帧类型", keyTypeMenu);

        // 位置插值模式菜单
        TimelineContextMenu segmentTypeMenu = new TimelineContextMenu(getCommandHistory());
        segmentTypeMenu.setPopupId("CameraKeyframeSegmentTypeMenu");
        
        addSegmentTypeItem(segmentTypeMenu, "Default（轨道默认）", CameraKeyframe.SegmentPathType.DEFAULT, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, "Linear（线性）", CameraKeyframe.SegmentPathType.LINEAR, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, "Catmull-Rom（样条）", CameraKeyframe.SegmentPathType.CATMULL_ROM, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, "Bezier（贝塞尔）", CameraKeyframe.SegmentPathType.BEZIER, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, "Step（无插值）", CameraKeyframe.SegmentPathType.STEP, selectedCameraKeyframes);
        addSegmentTypeItem(segmentTypeMenu, "Hold（保持）", CameraKeyframe.SegmentPathType.HOLD, selectedCameraKeyframes);
        
        addSubmenu("位置插值模式", segmentTypeMenu);

        // 旋转插值模式菜单
        TimelineContextMenu rotationTypeMenu = new TimelineContextMenu(getCommandHistory());
        rotationTypeMenu.setPopupId("CameraKeyframeRotationTypeMenu");
        
        addRotationTypeItem(rotationTypeMenu, "Default（跟随位置曲线）",
                CameraKeyframe.RotationInterpolation.DEFAULT, selectedCameraKeyframes);
        addRotationTypeItem(rotationTypeMenu, "Linear（Slerp）",
                CameraKeyframe.RotationInterpolation.LINEAR, selectedCameraKeyframes);
        addRotationTypeItem(rotationTypeMenu, "Bezier（Slerp）",
                CameraKeyframe.RotationInterpolation.BEZIER, selectedCameraKeyframes);
        addRotationTypeItem(rotationTypeMenu, "Catmull-Rom（Slerp）",
                CameraKeyframe.RotationInterpolation.CATMULL_ROM, selectedCameraKeyframes);
        addRotationTypeItem(rotationTypeMenu, "Step（保持）",
                CameraKeyframe.RotationInterpolation.STEP, selectedCameraKeyframes);
        
        addSubmenu("旋转插值模式", rotationTypeMenu);

        // Bezier 控制柄菜单（仅在需要时显示）
        if (keyframe.getKeyType() == CameraKeyframe.KeyType.MANUAL_BEZIER ||
            keyframe.getSegmentPathType() == CameraKeyframe.SegmentPathType.BEZIER) {
            
            TimelineContextMenu handleMenu = new TimelineContextMenu(getCommandHistory());
            handleMenu.setPopupId("CameraKeyframeHandleMenu");
            
            addHandleModeItem(handleMenu, "自动 (Auto)", CameraKeyframe.HandleMode.AUTO);
            addHandleModeItem(handleMenu, "自由 (Free)", CameraKeyframe.HandleMode.FREE);
            addHandleModeItem(handleMenu, "对齐 (Aligned)", CameraKeyframe.HandleMode.ALIGNED);
            
            handleMenu.addSeparator();
            handleMenu.addItem("清除控制柄", () -> {
                // ⭐ 修复：使用命令模式，支持 Undo/Redo
                getCommandHistory().executeCommand(
                    new com.chronoblocks.ui.timeline.commands.ChangeBezierHandleCommand(
                        model, track, keyframe,
                        new org.joml.Vector3f(0, 0, 0),  // 新的 In 控制柄
                        new org.joml.Vector3f(0, 0, 0)   // 新的 Out 控制柄
                    )
                );
                TimelineCameraController.getInstance().onKeyframeChanged();
                LOGGER.debug("[Timeline] 清除摄像机关键帧控制柄");
            });
            
            addSubmenu("Bezier 控制柄", handleMenu);
        }

        addSeparator();

        // 实用功能
        addItem("跳转到此关键帧", () -> {
            TimelineCameraController.getInstance().previewAt(keyframe.getTime());
            LOGGER.debug("[Timeline] 跳转到摄像机关键帧 @ {}s", keyframe.getTime());
        });

        if (isMulti) {
            addItem("删除选中", () -> {
                List<BatchRemoveAutomationKeyframesCommand.Entry> entries = new ArrayList<>();
                for (CameraKeyframe ckf : selectedCameraKeyframes) {
                    entries.add(new BatchRemoveAutomationKeyframesCommand.Entry(track, ckf));
                }
                getCommandHistory().executeCommand(new BatchRemoveAutomationKeyframesCommand(model, entries));
                TimelineCameraController.getInstance().onKeyframeChanged();
                LOGGER.debug("[Timeline] 批量删除摄像机关键帧: {}", selectedCameraKeyframes.size());
            });
        }

        addCommandItem("删除关键帧",
                new RemoveAutomationKeyframeCommand(model, track, keyframe),
                "Del");
    }

    // ------------------------------------------------
    // Helper: 添加菜单项
    // ------------------------------------------------

    private void addKeyTypeItem(TimelineContextMenu menu, String label, CameraKeyframe.KeyType type, List<CameraKeyframe> selected) {
        boolean isSelected = keyframe.getKeyType() == type;
        // 使用 radio 样式显示选中状态（通过 ImGui.menuItem 的 selected 参数）
        // 注意：我们需要在 label 中添加选中标记，因为 TimelineContextMenu 当前不支持 radio 参数
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

    private void addHandleModeItem(TimelineContextMenu menu, String label, CameraKeyframe.HandleMode mode) {
        boolean isSelected = keyframe.getHandleMode() == mode;
        String displayLabel = isSelected ? "✓ " + label : label;
        menu.addItem(displayLabel, () -> {
            keyframe.setHandleMode(mode);
            TimelineCameraController.getInstance().onKeyframeChanged();
            LOGGER.debug("[Timeline] 设置摄像机关键帧控制柄模式: {}", mode);
        });
    }
}

