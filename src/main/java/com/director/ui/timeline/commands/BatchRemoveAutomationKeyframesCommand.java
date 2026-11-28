package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 批量删除自动化关键帧（支持一次 Undo/Redo）。
 */
public class BatchRemoveAutomationKeyframesCommand extends BaseTimelineCommand {
    public record Entry(TimelineTrack track, TimelineSelectable keyframe) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> removed = new ArrayList<>();

    public BatchRemoveAutomationKeyframesCommand(TimelineModel model, List<Entry> entries) {
        super(model, "automation_keyframes_removed_batch");
        if (entries != null) {
            for (Entry e : entries) {
                if (e != null && e.track() != null && e.keyframe() != null) {
                    this.entries.add(new Entry(Objects.requireNonNull(e.track()), Objects.requireNonNull(e.keyframe())));
                }
            }
        }
    }

    @Override
    protected void doExecute() {
        removed.clear();
        try {
            for (Entry e : entries) {
                if (e.track().removeAutomationKeyframe(e.keyframe().getId())) {
                    removed.add(e);
                }
            }
        } catch (RuntimeException | Error failure) {
            rollbackExecute(failure);
            throw failure;
        }
        if (removed.isEmpty()) {
            setResult(TimelineCommand.Result.NO_CHANGE);
        }
    }

    @Override
    protected void doUndo() {
        if (removed.isEmpty()) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        List<Entry> restored = new ArrayList<>();
        try {
            for (Entry e : removed) {
                boolean exists = e.track().getAutomationKeyframes().stream()
                        .anyMatch(k -> k != null && e.keyframe().getId().equals(k.getId()));
                if (exists) {
                    TimelineSelectable existing = e.track().getAutomationKeyframes().stream()
                            .filter(k -> k != null && e.keyframe().getId().equals(k.getId()))
                            .findFirst().orElseThrow();
                    if (existing != e.keyframe()) {
                        throw new IllegalStateException("Automation keyframe ID conflict: " + e.keyframe().getId());
                    }
                } else {
                    e.track().addAutomationKeyframe(e.keyframe());
                    restored.add(e);
                }
            }
        } catch (RuntimeException | Error failure) {
            for (Entry e : restored) {
                try {
                    e.track().removeAutomationKeyframe(e.keyframe().getId());
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private void rollbackExecute(Throwable failure) {
        for (int i = removed.size() - 1; i >= 0; i--) {
            Entry e = removed.get(i);
            try {
                e.track().addAutomationKeyframe(e.keyframe());
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    @Override
    public String getDescription() {
        return "删除自动化关键帧（批量）";
    }
}
