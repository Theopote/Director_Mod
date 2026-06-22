package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineClip;
import com.director.ui.timeline.model.clip.ClipKeyframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 从 Clip 中删除关键帧（可 Undo）
 * 
 * @author Director Team
 * @version 1.0
 */
public class RemoveClipKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveClipKeyframeCommand.class);

    private final TimelineTrack track;
    private final TimelineClip clip;
    private final UUID keyframeId;
    private ClipKeyframe snapshot; // 用于 Undo

    public RemoveClipKeyframeCommand(TimelineModel model,
                                     TimelineTrack track,
                                     TimelineClip clip,
                                     ClipKeyframe keyframe) {
        super(model, "clip_keyframe_removed");
        this.track = track;
        this.clip = clip;
        this.keyframeId = keyframe != null ? keyframe.getId() : null;
        // 浅拷贝足够（只包含时间 & 插值）
        if (keyframe != null) {
            this.snapshot = keyframe.copy();
        }
    }

    @Override
    protected void doExecute() {
        if (clip == null || keyframeId == null) {
            LOGGER.warn("[Timeline] 删除 Clip 关键帧失败：clip 或 keyframeId 为 null");
            return;
        }
        clip.removeKeyframe(keyframeId);
        LOGGER.debug("[Command] RemoveClipKeyframe id={} (clip: {}, track: {})",
                keyframeId, clip.getLabel(), track != null ? track.getName() : "null");
    }

    @Override
    protected void doUndo() {
        if (clip == null || snapshot == null) {
            return;
        }
        clip.addKeyframe(snapshot);
        LOGGER.debug("[Command] Undo RemoveClipKeyframe id={} @ {}s (clip: {}, track: {})",
                keyframeId, snapshot.getTime(), clip.getLabel(), track != null ? track.getName() : "null");
    }

    @Override
    public String getDescription() {
        return String.format("删除关键帧: %s @ %.2fs", 
                clip != null ? clip.getLabel() : "未知", 
                snapshot != null ? snapshot.getTime() : 0f);
    }
}

