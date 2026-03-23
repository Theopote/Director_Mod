package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.camera.path.CameraPathClip;
/**
 * DeletePathClipCommand - 删除 Path Clip 的命令
 * 
 * <p>用于支持撤销/重做的 Path Clip 删除操作。
 * 
 * @author Director Team
 * @version 1.0
 */
public class DeletePathClipCommand extends BaseTimelineCommand {
    
    private final TimelineTrack track;
    private final CameraPathClip clip;
    
    public DeletePathClipCommand(TimelineModel model, TimelineTrack track, CameraPathClip clip) {
        super(model, "path_clip_deleted");
        this.track = track;
        this.clip = clip;
    }
    
    @Override
    protected void doExecute() {
        if (track == null || clip == null) {
            return;
        }
        
        track.removeCameraPathClip(clip);
        
        // ⭐ 性能优化：BaseTimelineCommand.execute() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用 markDirty()，避免重复触发模型变更通知
    }
    
    @Override
    protected void doUndo() {
        if (track == null || clip == null) {
            return;
        }
        
        track.addCameraPathClip(clip);
        
        // ⭐ 性能优化：BaseTimelineCommand.undo() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用 markDirty()，避免重复触发模型变更通知
    }
    
    @Override
    public String getDescription() {
        return String.format("删除 Path Clip: %s",
                clip != null ? clip.getName() : "Path Clip");
    }
}
