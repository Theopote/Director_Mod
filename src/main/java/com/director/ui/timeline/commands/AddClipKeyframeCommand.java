package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.clip.ClipKeyframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 向 Clip 中添加关键帧（可 Undo）
 * 
 * @author Director Team
 * @version 1.0
 */
public class AddClipKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddClipKeyframeCommand.class);

    private final TimelineTrack track;
    private final TimelineClip clip;
    private final ClipKeyframe keyframe;

    public AddClipKeyframeCommand(TimelineModel model,
                                  TimelineTrack track,
                                  TimelineClip clip,
                                  ClipKeyframe keyframe) {
        super(model, "clip_keyframe_added");
        this.track = track;
        this.clip = clip;
        this.keyframe = keyframe;
    }

    @Override
    protected void doExecute() {
        if (clip == null || keyframe == null) {
            LOGGER.warn("[Timeline] 添加 Clip 关键帧失败：clip 或 keyframe 为 null");
            return;
        }
        clip.addKeyframe(keyframe);
        LOGGER.debug("[Command] AddClipKeyframe @ {}s (clip: {}, track: {})",
                keyframe.getTime(), clip.getLabel(), track != null ? track.getName() : "null");
    }

    @Override
    protected void doUndo() {
        if (clip == null || keyframe == null) {
            return;
        }
        clip.removeKeyframe(keyframe.getId());
        LOGGER.debug("[Command] Undo AddClipKeyframe @ {}s (clip: {}, track: {})",
                keyframe.getTime(), clip.getLabel(), track != null ? track.getName() : "null");
    }

    @Override
    public String getDescription() {
        return String.format("添加关键帧: %s @ %.2fs", 
                clip != null ? clip.getLabel() : "未知", 
                keyframe != null ? keyframe.getTime() : 0f);
    }
}

