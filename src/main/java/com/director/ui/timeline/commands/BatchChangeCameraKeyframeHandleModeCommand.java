package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.playback.camera.TimelineCameraController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量修改摄像机关键帧 Bezier 控制柄模式（支持一次 Undo/Redo）。
 */
public class BatchChangeCameraKeyframeHandleModeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchChangeCameraKeyframeHandleModeCommand.class);

    private final List<CameraKeyframe> keyframes = new ArrayList<>();
    private final List<CameraKeyframe.HandleMode> oldModes = new ArrayList<>();
    private final CameraKeyframe.HandleMode newMode;

    public BatchChangeCameraKeyframeHandleModeCommand(TimelineModel model,
                                                      List<CameraKeyframe> keyframes,
                                                      CameraKeyframe.HandleMode newMode) {
        super(model, "camera_keyframe_handle_mode_changed_batch");
        if (keyframes != null) {
            for (CameraKeyframe kf : keyframes) {
                if (kf == null) {
                    continue;
                }
                this.keyframes.add(kf);
                this.oldModes.add(kf.getHandleMode());
            }
        }
        this.newMode = newMode;
    }

    @Override
    protected void doExecute() {
        for (CameraKeyframe keyframe : keyframes) {
            keyframe.setHandleMode(newMode);
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.debug("[Timeline] 批量设置摄像机关键帧控制柄模式 -> {}", newMode);
    }

    @Override
    protected void doUndo() {
        for (int i = 0; i < keyframes.size(); i++) {
            keyframes.get(i).setHandleMode(oldModes.get(i));
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.debug("[Timeline] 撤销批量设置摄像机关键帧控制柄模式");
    }

    @Override
    public String getDescription() {
        return "设置控制柄模式（批量）";
    }
}
