package com.chronoblocks.ui.timeline.commands;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 删除自动化关键帧命令（摄像机关键帧、天气关键帧等）。
 * 
 * <p>支持撤销/重做操作。
 */
public class RemoveAutomationKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveAutomationKeyframeCommand.class);

    private final TimelineTrack track;
    private final UUID keyframeId;
    private TimelineSelectable removedKeyframe;

    public RemoveAutomationKeyframeCommand(TimelineModel model, TimelineTrack track, UUID keyframeId) {
        super(model, "automation_keyframe_removed");
        this.track = track;
        this.keyframeId = keyframeId;
    }

    /**
     * 从 TimelineSelectable 创建命令（更方便的构造方法）
     */
    public RemoveAutomationKeyframeCommand(TimelineModel model, TimelineTrack track, TimelineSelectable keyframe) {
        super(model, "automation_keyframe_removed");
        this.track = track;
        this.keyframeId = keyframe != null ? keyframe.getId() : null;
    }

    @Override
    protected void doExecute() {
        if (track == null || keyframeId == null) {
            LOGGER.warn("[Timeline] 删除自动化关键帧失败：track 或 keyframeId 为空");
            return;
        }

        // 查找要删除的关键帧
        removedKeyframe = track.getAutomationKeyframes().stream()
                .filter(k -> k != null && keyframeId.equals(k.getId()))
                .findFirst()
                .orElse(null);

        if (removedKeyframe == null) {
            LOGGER.warn("[Timeline] 删除自动化关键帧失败：找不到ID为 {} 的关键帧", keyframeId);
            return;
        }

        track.removeAutomationKeyframe(keyframeId);
        LOGGER.debug("[Timeline] 删除自动化关键帧: {}", removedKeyframe.getName());
    }

    @Override
    protected void doUndo() {
        if (track == null || removedKeyframe == null) {
            return;
        }
        track.addAutomationKeyframe(removedKeyframe);
        LOGGER.debug("[Timeline] 恢复自动化关键帧: {}", removedKeyframe.getName());
    }

    @Override
    public String getDescription() {
        return removedKeyframe != null 
            ? "删除关键帧: " + removedKeyframe.getName() 
            : "删除自动化关键帧";
    }

    @Override
    public boolean canUndo() {
        return removedKeyframe != null;
    }
}

