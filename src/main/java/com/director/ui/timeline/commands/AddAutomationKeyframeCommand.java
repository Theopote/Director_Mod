package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 为轨道添加自动化关键帧（摄像机、天气、标记等）。
 */
public class AddAutomationKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddAutomationKeyframeCommand.class);

    private final TimelineTrack track;
    private final TimelineSelectable keyframe;

    public AddAutomationKeyframeCommand(TimelineModel model, TimelineTrack track, TimelineSelectable keyframe) {
        super(model, "automation_keyframe_added");
        this.track = track;
        this.keyframe = keyframe;
    }

    @Override
    protected void doExecute() {
        if (track == null || keyframe == null) {
            LOGGER.warn("[Timeline] 添加关键帧失败：track 或 keyframe 为空");
            return;
        }
        
        // ✅ 修复：容器轨道和清除轨道不支持轨道级关键帧
        TrackType type = track.getType();
        if (type == TrackType.CONTAINER) {
            LOGGER.warn("[Timeline] 容器轨道不支持轨道级关键帧，请使用片段内的动画配置 (轨道: {})", track.getName());
            return;
        }
        
        track.addAutomationKeyframe(keyframe);
    }

    @Override
    protected void doUndo() {
        if (track == null || keyframe == null) {
            return;
        }
        track.removeAutomationKeyframe(keyframe.getId());
    }

    @Override
    public String getDescription() {
        return keyframe != null ? "添加关键帧: " + keyframe.getName() : "添加关键帧";
    }
}
