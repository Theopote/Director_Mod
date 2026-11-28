package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.playback.camera.TimelineCameraController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量修改摄像机关键帧位置插值模式（SegmentPathType）（支持一次 Undo/Redo）
 */
public class BatchChangeCameraKeyframeSegmentTypeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchChangeCameraKeyframeSegmentTypeCommand.class);

    private final List<CameraKeyframe> keyframes = new ArrayList<>();
    private final List<CameraKeyframe.SegmentPathType> oldTypes = new ArrayList<>();
    private final List<Integer> changed = new ArrayList<>();
    private final CameraKeyframe.SegmentPathType newType;

    public BatchChangeCameraKeyframeSegmentTypeCommand(TimelineModel model,
                                                       List<CameraKeyframe> keyframes,
                                                       CameraKeyframe.SegmentPathType newType) {
        super(model, "camera_keyframe_segmenttype_changed_batch");
        if (keyframes != null) {
            for (CameraKeyframe kf : keyframes) {
                if (kf == null) continue;
                this.keyframes.add(kf);
                this.oldTypes.add(kf.getSegmentPathType());
            }
        }
        this.newType = newType;
    }

    @Override
    protected void doExecute() {
        if (newType == null || keyframes.isEmpty()) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        try {
            for (int i = 0; i < keyframes.size(); i++) {
                CameraKeyframe keyframe = keyframes.get(i);
                if (keyframe.getSegmentPathType() == newType) continue;
                keyframe.setSegmentPathType(newType);
                changed.add(i);
            }
        } catch (RuntimeException | Error e) {
            restoreChanged();
            throw e;
        }
        if (changed.isEmpty()) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.debug("[Timeline] 批量设置摄像机关键帧位置插值 -> {}", newType);
    }

    @Override
    protected void doUndo() {
        restoreChanged();
        changed.clear();
        TimelineCameraController.getInstance().onKeyframeChanged();
        LOGGER.debug("[Timeline] 撤销批量设置摄像机关键帧位置插值");
    }

    private void restoreChanged() {
        for (int i = changed.size() - 1; i >= 0; i--) {
            int index = changed.get(i);
            keyframes.get(index).setSegmentPathType(oldTypes.get(index));
        }
    }

    @Override
    public String getDescription() {
        return "设置位置插值（批量）";
    }

    @Override
    public boolean canUndo() {
        return !changed.isEmpty();
    }
}


