package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineClip;
import com.director.ui.timeline.model.clip.ClipKeyframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 拖动 Clip 内关键帧（可 Undo）
 * 
 * @author Director Team
 * @version 1.0
 */
public class MoveClipKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveClipKeyframeCommand.class);

    private final TimelineTrack track;
    private final TimelineClip clip;
    private final UUID keyframeId;
    private final float oldTime;
    private final float newTime;

    public MoveClipKeyframeCommand(TimelineModel model,
                                   TimelineTrack track,
                                   TimelineClip clip,
                                   ClipKeyframe keyframe,
                                   float oldTime,
                                   float newTime) {
        super(model, "clip_keyframe_moved");
        this.track = track;
        this.clip = clip;
        this.keyframeId = keyframe != null ? keyframe.getId() : null;
        this.oldTime = oldTime;
        this.newTime = newTime;
    }

    @Override
    protected void doExecute() {
        apply(newTime);
        LOGGER.debug("[Command] MoveClipKeyframe execute {} -> {} (clip: {}, track: {})",
                oldTime, newTime, clip != null ? clip.getLabel() : "null", 
                track != null ? track.getName() : "null");
    }

    @Override
    protected void doUndo() {
        apply(oldTime);
        LOGGER.debug("[Command] MoveClipKeyframe undo {} <- {} (clip: {}, track: {})",
                newTime, oldTime, clip != null ? clip.getLabel() : "null", 
                track != null ? track.getName() : "null");
    }

    private void apply(float t) {
        ClipKeyframe kf = findKeyframe();
        if (kf == null) {
            LOGGER.warn("[Timeline] MoveClipKeyframe: 未找到关键帧 id={}", keyframeId);
            return;
        }

        clip.moveKeyframe(kf, t);
    }

    private ClipKeyframe findKeyframe() {
        if (clip == null || keyframeId == null) {
            return null;
        }
        for (ClipKeyframe kf : clip.getClipKeyframes()) {
            if (kf.getId().equals(keyframeId)) {
                return kf;
            }
        }
        return null;
    }

    @Override
    public String getDescription() {
        return String.format("移动关键帧: %s @ %.2fs -> %.2fs", 
                clip != null ? clip.getLabel() : "未知", oldTime, newTime);
    }
}

