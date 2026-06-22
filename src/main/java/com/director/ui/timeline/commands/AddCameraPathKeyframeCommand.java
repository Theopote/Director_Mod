package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.playback.camera.TimelineCameraController;

public class AddCameraPathKeyframeCommand extends BaseTimelineCommand {
    private final CustomPathClip clip;
    private final CameraKeyframe keyframe;

    public AddCameraPathKeyframeCommand(TimelineModel model, CustomPathClip clip, CameraKeyframe keyframe) {
        super(model, "camera_path_keyframe_added");
        this.clip = clip;
        this.keyframe = keyframe;
    }

    @Override
    protected void doExecute() {
        if (clip != null && keyframe != null) {
            clip.addKeyframe(keyframe);
            TimelineCameraController.getInstance().onKeyframeChanged();
        }
    }

    @Override
    protected void doUndo() {
        if (clip != null && keyframe != null) {
            clip.removeKeyframe(keyframe.getId());
            TimelineCameraController.getInstance().onKeyframeChanged();
        }
    }

    @Override
    public String getDescription() {
        return keyframe != null ? "添加自定义路径关键帧: " + keyframe.getName() : "添加自定义路径关键帧";
    }
}
