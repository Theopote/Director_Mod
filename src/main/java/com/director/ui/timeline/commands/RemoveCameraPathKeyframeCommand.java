package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.playback.camera.TimelineCameraController;

public class RemoveCameraPathKeyframeCommand extends BaseTimelineCommand {
    private final CustomPathClip clip;
    private final CameraKeyframe keyframe;
    private boolean removed;

    public RemoveCameraPathKeyframeCommand(TimelineModel model, CustomPathClip clip, CameraKeyframe keyframe) {
        super(model, "camera_path_keyframe_removed");
        this.clip = clip;
        this.keyframe = keyframe;
    }

    @Override
    protected void doExecute() {
        if (clip == null || keyframe == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        removed = clip.removeKeyframe(keyframe.getId());
        if (!removed) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        TimelineCameraController.getInstance().onKeyframeChanged();
    }

    @Override
    protected void doUndo() {
        if (!removed) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        if (clip.getKeyframes().stream().anyMatch(k -> k != null && keyframe.getId().equals(k.getId()))) {
            throw new IllegalStateException("Camera path keyframe ID conflict: " + keyframe.getId());
        }
        clip.addKeyframe(keyframe);
        removed = false;
        TimelineCameraController.getInstance().onKeyframeChanged();
    }

    @Override
    public String getDescription() {
        return keyframe != null ? "删除自定义路径关键帧: " + keyframe.getName() : "删除自定义路径关键帧";
    }

    @Override
    public boolean canUndo() {
        return removed;
    }
}
