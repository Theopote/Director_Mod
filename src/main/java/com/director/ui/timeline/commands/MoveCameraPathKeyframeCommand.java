package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.playback.camera.TimelineCameraController;

public class MoveCameraPathKeyframeCommand extends BaseTimelineCommand {
    private final CustomPathClip clip;
    private final CameraKeyframe keyframe;
    private final float oldTime;
    private final float newTime;

    public MoveCameraPathKeyframeCommand(TimelineModel model, CustomPathClip clip, CameraKeyframe keyframe,
                                         float oldTime, float newTime) {
        super(model, "camera_path_keyframe_moved");
        this.clip = clip;
        this.keyframe = keyframe;
        this.oldTime = oldTime;
        this.newTime = newTime;
    }

    @Override
    protected void doExecute() {
        apply(newTime);
    }

    @Override
    protected void doUndo() {
        apply(oldTime);
    }

    private void apply(float time) {
        if (clip != null && keyframe != null) {
            keyframe.setTime(time);
            clip.onKeyframeChanged();
            TimelineCameraController.getInstance().onKeyframeChanged();
        }
    }

    @Override
    public String getDescription() {
        return "移动自定义路径关键帧";
    }
}
