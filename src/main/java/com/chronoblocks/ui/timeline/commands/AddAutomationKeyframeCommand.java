package com.chronoblocks.ui.timeline.commands;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
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
