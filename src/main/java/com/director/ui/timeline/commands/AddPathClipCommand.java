package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.camera.path.CameraPathClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AddPathClipCommand - 添加 Path Clip 的命令
 * 
 * <p>用于支持撤销/重做的 Path Clip 创建操作。
 * 
 * @author Director Team
 * @version 1.0
 */
public class AddPathClipCommand extends BaseTimelineCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddPathClipCommand.class);
    
    private final TimelineTrack track;
    private final CameraPathClip clip;
    private boolean added;
    
    public AddPathClipCommand(TimelineModel model, TimelineTrack track, CameraPathClip clip) {
        super(model, "path_clip_added");
        this.track = track;
        this.clip = clip;
    }
    
    @Override
    protected void doExecute() {
        added = false;
        if (track == null || clip == null) {
            return;
        }

        if (track.hasCameraPathOverlap(clip.getStartTime(), clip.getEndTime(), null)) {
            LOGGER.warn("[AddPathClipCommand] 跳过重叠 Path Clip: {} @ {}s-{}s",
                    clip.getName(), String.format("%.2f", clip.getStartTime()), String.format("%.2f", clip.getEndTime()));
            return;
        }
        
        track.addCameraPathClip(clip);
        added = true;
        
        // ⭐ 性能优化：BaseTimelineCommand.execute() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用 markDirty()，避免重复触发模型变更通知
    }
    
    @Override
    protected void doUndo() {
        if (track == null || clip == null || !added) {
            return;
        }
        
        track.removeCameraPathClip(clip);
        
        // ⭐ 性能优化：BaseTimelineCommand.undo() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用 markDirty()，避免重复触发模型变更通知
    }
    
    @Override
    public String getDescription() {
        return String.format("添加 Path Clip: %s @ %.2fs-%.2fs",
                clip != null ? clip.getName() : "Path Clip",
                clip != null ? clip.getStartTime() : 0f,
                clip != null ? clip.getEndTime() : 0f);
    }
}
