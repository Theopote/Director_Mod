package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.clip.ClipKeyframe;


/**
 * 删除关键帧命令（支持撤销）。
 */
public class RemoveKeyframeCommand extends BaseTimelineCommand {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RemoveKeyframeCommand.class);

    private final TimelineClip clip;
    private final float time;
    private ClipKeyframe removed;

    public RemoveKeyframeCommand(TimelineModel model, TimelineClip clip, float time) {
        super(model, "keyframe_removed");
        this.clip = clip;
        this.time = time;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        ClipKeyframe target = clip.getClipKeyframes().stream()
                .filter(k -> k != null && Float.compare(k.getTime(), time) == 0)
                .findFirst().orElse(null);
        if (target == null || !clip.removeKeyframe(target.getId())) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        removed = target.snapshot();
        LOGGER.debug("[Timeline] 删除关键帧: {}s", time);
    }

    @Override
    protected void doUndo() {
        if (removed == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        if (clip.getClipKeyframes().stream().anyMatch(k -> k != null && removed.getId().equals(k.getId()))) {
            throw new IllegalStateException("Keyframe ID conflict: " + removed.getId());
        }
        clip.addKeyframe(removed);
        LOGGER.debug("[Timeline] 撤销删除关键帧: {}s", time);
    }

    @Override
    public String getDescription() {
        return String.format("删除关键帧: %ss", time);
    }
}
