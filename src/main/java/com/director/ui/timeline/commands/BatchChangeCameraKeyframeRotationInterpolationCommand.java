package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.playback.camera.TimelineCameraController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量修改摄像机关键帧旋转插值模式（RotationInterpolation）（支持一次 Undo/Redo）
 */
public class BatchChangeCameraKeyframeRotationInterpolationCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchChangeCameraKeyframeRotationInterpolationCommand.class);

    private final List<CameraKeyframe> keyframes = new ArrayList<>();
    private final List<CameraKeyframe.RotationInterpolation> oldTypes = new ArrayList<>();
    private final CameraKeyframe.RotationInterpolation newType;

    public BatchChangeCameraKeyframeRotationInterpolationCommand(TimelineModel model,
                                                                 List<CameraKeyframe> keyframes,
                                                                 CameraKeyframe.RotationInterpolation newType) {
        super(model, "camera_keyframe_rotationinterp_changed_batch");
        if (keyframes != null) {
            for (CameraKeyframe kf : keyframes) {
                if (kf == null) continue;
                this.keyframes.add(kf);
                this.oldTypes.add(kf.getRotationInterpolation());
            }
        }
        this.newType = newType;
    }

    @Override
    protected void doExecute() {
        for (CameraKeyframe kf : keyframes) {
            kf.setRotationInterpolation(newType);
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.debug("[Timeline] 批量设置摄像机关键帧旋转插值 -> {}", newType);
    }

    @Override
    protected void doUndo() {
        for (int i = 0; i < keyframes.size(); i++) {
            keyframes.get(i).setRotationInterpolation(oldTypes.get(i));
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.debug("[Timeline] 撤销批量设置摄像机关键帧旋转插值");
    }

    @Override
    public String getDescription() {
        return "设置旋转插值（批量）";
    }
}


