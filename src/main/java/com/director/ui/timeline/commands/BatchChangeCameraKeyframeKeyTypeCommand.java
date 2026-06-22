package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.playback.camera.TimelineCameraController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量修改摄像机关键帧类型（支持一次 Undo/Redo）
 */
public class BatchChangeCameraKeyframeKeyTypeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchChangeCameraKeyframeKeyTypeCommand.class);

    private final List<CameraKeyframe> keyframes = new ArrayList<>();
    private final List<CameraKeyframe.KeyType> oldTypes = new ArrayList<>();
    private final CameraKeyframe.KeyType newType;

    public BatchChangeCameraKeyframeKeyTypeCommand(TimelineModel model,
                                                  List<CameraKeyframe> keyframes,
                                                  CameraKeyframe.KeyType newType) {
        super(model, "camera_keyframe_keytype_changed_batch");
        if (keyframes != null) {
            for (CameraKeyframe kf : keyframes) {
                if (kf == null) continue;
                this.keyframes.add(kf);
                this.oldTypes.add(kf.getKeyType());
            }
        }
        this.newType = newType;
    }

    @Override
    protected void doExecute() {
        for (CameraKeyframe kf : keyframes) {
            kf.setKeyType(newType);
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.debug("[Timeline] 批量设置摄像机关键帧类型 -> {}", newType);
    }

    @Override
    protected void doUndo() {
        for (int i = 0; i < keyframes.size(); i++) {
            keyframes.get(i).setKeyType(oldTypes.get(i));
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.debug("[Timeline] 撤销批量设置摄像机关键帧类型");
    }

    @Override
    public String getDescription() {
        return "设置摄像机关键帧类型（批量）";
    }
}


