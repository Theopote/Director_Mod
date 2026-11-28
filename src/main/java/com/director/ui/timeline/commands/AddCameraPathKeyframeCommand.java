package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.playback.camera.TimelineCameraController;

public class AddCameraPathKeyframeCommand extends BaseTimelineCommand {
    private final CustomPathClip clip;
    private final CameraKeyframe keyframe;
    private CameraKeyframe previous;
    private boolean applied;

    public AddCameraPathKeyframeCommand(TimelineModel model, CustomPathClip clip, CameraKeyframe keyframe) {
        super(model, "camera_path_keyframe_added");
        this.clip = clip;
        this.keyframe = keyframe;
    }

    @Override
    protected void doExecute() {
        if (clip == null || keyframe == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        previous = clip.getKeyframes().stream()
                .filter(k -> k != null && keyframe.getId().equals(k.getId()))
                .findFirst().orElse(null);
        if (previous == keyframe) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        clip.addKeyframe(keyframe);
        applied = true;
        TimelineCameraController.getInstance().onKeyframeChanged();
    }

    @Override
    protected void doUndo() {
        if (!applied) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        clip.removeKeyframe(keyframe.getId());
        if (previous != null) {
            clip.addKeyframe(previous);
        }
        applied = false;
        TimelineCameraController.getInstance().onKeyframeChanged();
    }

    @Override
    public String getDescription() {
        return keyframe != null ? "添加自定义路径关键帧: " + keyframe.getName() : "添加自定义路径关键帧";
    }

    @Override
    public boolean canUndo() {
        return applied;
    }
}
