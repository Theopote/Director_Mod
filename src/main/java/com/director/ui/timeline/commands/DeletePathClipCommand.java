package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.path.CameraPathClip;

/**
 * DeletePathClipCommand - 删除 Path Clip 的命令
 *
 * <p>用于支持撤销/重做的 Path Clip 删除操作。</p>
 *
 * @author Director Team
 * @version 1.0
 */
public class DeletePathClipCommand extends BaseTimelineCommand {
    private final TimelineTrack track;
    private final CameraPathClip clip;
    private boolean removed;

    public DeletePathClipCommand(TimelineModel model, TimelineTrack track, CameraPathClip clip) {
        super(model, "path_clip_deleted");
        this.track = track;
        this.clip = clip;
    }

    @Override
    protected void doExecute() {
        if (track == null || clip == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        if (!track.getCameraPathClips().contains(clip)) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        track.removeCameraPathClip(clip);
        removed = true;
    }

    @Override
    protected void doUndo() {
        if (!removed) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        if (track.getCameraPathClips().contains(clip)) {
            throw new IllegalStateException("Path clip is already present: " + clip.getName());
        }
        track.addCameraPathClip(clip);
        removed = false;
    }

    @Override
    public String getDescription() {
        return String.format("删除 Path Clip: %s",
                clip != null ? clip.getName() : "Path Clip");
    }

    @Override
    public boolean canUndo() {
        return removed;
    }
}
