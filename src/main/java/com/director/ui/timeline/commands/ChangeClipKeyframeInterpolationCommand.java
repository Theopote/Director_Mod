package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.clip.ClipKeyframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 修改 Clip 关键帧的插值方式（线性 / 平滑 / 阶梯）
 * 
 * @author Director Team
 * @version 1.0
 */
public class ChangeClipKeyframeInterpolationCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeClipKeyframeInterpolationCommand.class);

    private final TimelineTrack track;
    private final TimelineClip clip;
    private final UUID keyframeId;
    private final TimelineInterpolation oldInterpolation;
    private final TimelineInterpolation newInterpolation;

    public ChangeClipKeyframeInterpolationCommand(TimelineModel model,
                                                  TimelineTrack track,
                                                  TimelineClip clip,
                                                  ClipKeyframe keyframe,
                                                  TimelineInterpolation newInterpolation) {
        super(model, "clip_keyframe_interpolation_changed");
        this.track = track;
        this.clip = clip;
        this.keyframeId = keyframe != null ? keyframe.getId() : null;
        this.oldInterpolation = keyframe != null ? keyframe.getInterpolation() : TimelineInterpolation.LINEAR;
        this.newInterpolation = newInterpolation;
    }

    @Override
    protected void doExecute() {
        apply(newInterpolation);
        LOGGER.debug("[Command] ChangeClipKeyframeInterpolation {} -> {} (clip: {}, track: {})",
                oldInterpolation != null ? oldInterpolation.getDisplayName() : "null",
                newInterpolation != null ? newInterpolation.getDisplayName() : "null",
                clip != null ? clip.getLabel() : "null", 
                track != null ? track.getName() : "null");
    }

    @Override
    protected void doUndo() {
        apply(oldInterpolation);
        LOGGER.debug("[Command] Undo ChangeClipKeyframeInterpolation {} <- {} (clip: {}, track: {})",
                newInterpolation != null ? newInterpolation.getDisplayName() : "null",
                oldInterpolation != null ? oldInterpolation.getDisplayName() : "null",
                clip != null ? clip.getLabel() : "null", 
                track != null ? track.getName() : "null");
    }

    private void apply(TimelineInterpolation interpolation) {
        ClipKeyframe kf = findKeyframe();
        if (kf == null) {
            LOGGER.warn("[Timeline] ChangeClipKeyframeInterpolation: 未找到关键帧 id={}", keyframeId);
            return;
        }

        kf.setInterpolation(interpolation);
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
        return String.format("修改插值: %s -> %s", 
                oldInterpolation != null ? oldInterpolation.getDisplayName() : "未知",
                newInterpolation != null ? newInterpolation.getDisplayName() : "未知");
    }
}

