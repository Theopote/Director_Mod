package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.clip.ClipKeyframe;
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
    private ClipKeyframe snapshot;
    private boolean removed;

    public RemoveClipKeyframeCommand(TimelineModel model,
                                     TimelineTrack track,
                                     TimelineClip clip,
                                     ClipKeyframe keyframe) {
        super(model, "clip_keyframe_removed");
        this.track = track;
        this.clip = clip;
        this.keyframeId = keyframe != null ? keyframe.getId() : null;
        this.snapshot = keyframe != null ? keyframe.snapshot() : null;
    }

    @Override
    protected void doExecute() {
        if (clip == null || snapshot == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        ClipKeyframe existing = clip.getClipKeyframes().stream()
                .filter(k -> k != null && keyframeId.equals(k.getId()))
                .findFirst().orElse(null);
        if (existing == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        snapshot = existing.snapshot();
        if (!clip.removeKeyframe(keyframeId)) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        removed = true;
        LOGGER.debug("[Command] RemoveClipKeyframe id={} (clip: {}, track: {})",
                keyframeId, clip.getLabel(), track != null ? track.getName() : "null");
    }

    @Override
    protected void doUndo() {
        if (!removed || snapshot == null) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        if (clip.getClipKeyframes().stream().anyMatch(k -> k != null && keyframeId.equals(k.getId()))) {
            throw new IllegalStateException("Keyframe ID conflict: " + keyframeId);
        }
        clip.addKeyframe(snapshot);
        removed = false;
        LOGGER.debug("[Command] Undo RemoveClipKeyframe id={} @ {}s (clip: {}, track: {})",
                keyframeId, snapshot.getTime(), clip.getLabel(), track != null ? track.getName() : "null");
    }

    @Override
    public String getDescription() {
        return String.format("删除关键帧: %s @ %.2fs",
                clip != null ? clip.getLabel() : "未知",
                snapshot != null ? snapshot.getTime() : 0f);
    }

    @Override
    public boolean canUndo() {
        return removed;
    }
}
