package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.path.CameraPathClip;
import com.director.timeline.model.camera.path.CustomPathClip;

/**
 * MovePathClipCommand - 移动 Path Clip 的命令
 * 
 * <p>用于支持撤销/重做的 Path Clip 拖动操作。
 * 
 * @author Director Team
 * @version 1.0
 */
public class MovePathClipCommand extends BaseTimelineCommand {
    
    private final TimelineTrack track;
    private final CameraPathClip clip;
    private final float oldStart;
    private final float oldEnd;
    private final float newStart;
    private final float newEnd;
    private boolean moved;
    
    public MovePathClipCommand(TimelineModel model, TimelineTrack track, CameraPathClip clip,
                               float oldStart, float oldEnd, float newStart, float newEnd) {
        super(model, "path_clip_moved");
        this.track = track;
        this.clip = clip;
        this.oldStart = oldStart;
        this.oldEnd = oldEnd;
        this.newStart = newStart;
        this.newEnd = newEnd;
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
        apply(newStart, newEnd);
    }

    private void apply(float start, float end) {
        if (Float.compare(clip.getStartTime(), start) == 0
                && Float.compare(clip.getEndTime(), end) == 0) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        if (clip instanceof CustomPathClip customPathClip) {
            customPathClip.offsetKeyframes(start - clip.getStartTime());
        }
        clip.setStartTime(start);
        clip.setEndTime(end);
        moved = true;
        
        // ⭐ 性能优化：BaseTimelineCommand.execute() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用 markDirty()，避免重复触发模型变更通知
    }
    
    @Override
    protected void doUndo() {
        if (!track.getCameraPathClips().contains(clip)) {
            throw new IllegalStateException("Path clip is missing from track: " + clip.getName());
        }
        apply(oldStart, oldEnd);
        moved = false;
        
        // ⭐ 性能优化：BaseTimelineCommand.undo() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用 markDirty()，避免重复触发模型变更通知
    }
    
    @Override
    public String getDescription() {
        return String.format("移动 Path Clip: %s (%.2fs-%.2fs -> %.2fs-%.2fs)",
                clip != null ? clip.getName() : "Path Clip",
                oldStart, oldEnd, newStart, newEnd);
    }

    @Override
    public boolean canUndo() {
        return moved;
    }
}
