package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.path.CameraPathClip;
import com.director.timeline.model.clip.ClipKeyframe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 批量删除当前选择（单一 Undo/Redo）
 *
 * <p>用于一次性删除多个片段/关键帧/自动化关键帧，但在历史记录里只产生一条命令，
 * 这样撤销时也只需要一步。</p>
 */
public class BatchDeleteSelectionCommand extends BaseTimelineCommand {

    private final List<Object> selectionSnapshot;
    private final List<TimelineCommand> executed = new ArrayList<>();

    public BatchDeleteSelectionCommand(TimelineModel model, List<Object> selectedItemsSnapshot) {
        super(model, "selection_deleted_batch");
        this.selectionSnapshot = selectedItemsSnapshot != null ? new ArrayList<>(selectedItemsSnapshot) : List.of();
    }

    @Override
    protected void doExecute() {
        executed.clear();
        if (model == null || selectionSnapshot.isEmpty()) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }

        try {
            deleteClips();
            deleteClipKeyframes();
            deleteMarkers();
            deletePathClips();
            deleteAutomationKeyframes();
        } catch (RuntimeException | Error failure) {
            rollbackExecute(failure);
            throw failure;
        }
        if (executed.isEmpty()) {
            setResult(TimelineCommand.Result.NO_CHANGE);
        }
    }

    private void deleteClips() {
        Set<TimelineClip> clipsToDelete = new LinkedHashSet<>();
        for (Object it : selectionSnapshot) {
            if (it instanceof TimelineClip clip) {
                clipsToDelete.add(clip);
            }
        }

        for (TimelineClip clip : clipsToDelete) {
            TimelineTrack track = model.findTrackForClip(clip);
            if (track != null && track.isLocked()) {
                continue;
            }
            if (track != null) {
                executeSubCommand(new DeleteClipCommand(model, clip, track.getClips()));
            }
        }
    }

    private void deleteClipKeyframes() {
        for (Object it : selectionSnapshot) {
            if (!(it instanceof ClipKeyframe kf)) continue;

            Owner owner = findOwnerForClipKeyframe(kf);
            if (owner == null) continue;
            if (model.findTrackForClip(owner.clip) == null) continue;
            if (owner.track != null && owner.track.isLocked()) continue;

            executeSubCommand(new RemoveClipKeyframeCommand(model, owner.track, owner.clip, kf));
        }
    }

    private void deleteMarkers() {
        for (Object it : selectionSnapshot) {
            if (it instanceof com.director.timeline.model.TimelineMarker marker) {
                executeSubCommand(new DeleteMarkerCommand(model, marker));
            }
        }
    }

    private void deletePathClips() {
        for (Object it : selectionSnapshot) {
            if (!(it instanceof CameraPathClip pathClip)) continue;

            TimelineTrack track = findOwnerTrackForPathClip(pathClip);
            if (track == null || track.isLocked()) continue;

            executeSubCommand(new DeletePathClipCommand(model, track, pathClip));
        }
    }

    private void deleteAutomationKeyframes() {
        for (Object it : selectionSnapshot) {
            if (!(it instanceof TimelineSelectable selectable)) continue;
            switch (it) {
                case TimelineClip ignored -> {
                    continue;
                }
                case com.director.timeline.model.TimelineMarker ignored -> {
                    continue;
                }
                case CameraPathClip ignored -> {
                    continue;
                }
                case com.director.timeline.model.audio.AudioEvent ignored -> {
                    continue;
                }
                default -> {
                }
            }

            TimelineTrack track = findOwnerTrackForAutomationKeyframe(selectable);
            if (track == null || track.isLocked()) continue;

            TimelineCommand cmd;
            if (selectable instanceof com.director.timeline.model.camera.CameraKeyframe cameraKeyframe) {
                com.director.timeline.model.camera.path.CustomPathClip customPathClip =
                        model.findCustomPathClipForKeyframe(cameraKeyframe);
                if (customPathClip != null) {
                    cmd = new RemoveCameraPathKeyframeCommand(model, customPathClip, cameraKeyframe);
                } else {
                    cmd = new RemoveAutomationKeyframeCommand(model, track, selectable);
                }
            } else {
                cmd = new RemoveAutomationKeyframeCommand(model, track, selectable);
            }
            executeSubCommand(cmd);
        }
    }

    private void executeSubCommand(TimelineCommand command) {
        if (command.executeResult() == TimelineCommand.Result.APPLIED) {
            executed.add(command);
        }
    }

    private void rollbackExecute(Throwable failure) {
        for (int i = executed.size() - 1; i >= 0; i--) {
            try {
                TimelineCommand.Result result = executed.get(i).undoResult();
                if (result != TimelineCommand.Result.APPLIED) {
                    throw new IllegalStateException("Failed to compensate batch deletion: "
                            + executed.get(i).getDescription() + " (" + result + ")");
                }
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    @Override
    protected void doUndo() {
        if (executed.isEmpty()) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return;
        }
        List<TimelineCommand> reversed = new ArrayList<>(executed);
        Collections.reverse(reversed);
        int restored = 0;
        try {
            for (TimelineCommand cmd : reversed) {
                TimelineCommand.Result result = cmd.undoResult();
                if (result != TimelineCommand.Result.APPLIED) {
                    throw new IllegalStateException("Batch deletion subcommand did not undo: " + cmd.getDescription());
                }
                restored++;
            }
        } catch (RuntimeException | Error failure) {
            rollbackUndo(reversed, restored, failure);
            throw failure;
        }
    }

    private void rollbackUndo(List<TimelineCommand> commands, int restored, Throwable failure) {
        for (int i = restored - 1; i >= 0; i--) {
            try {
                if (commands.get(i).executeResult() != TimelineCommand.Result.APPLIED) {
                    throw new IllegalStateException("Failed to redo compensated batch deletion");
                }
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    @Override
    public String getDescription() {
        return "删除选中（批量）";
    }

    private record Owner(TimelineTrack track, TimelineClip clip) {}

    private Owner findOwnerForClipKeyframe(ClipKeyframe keyframe) {
        if (keyframe == null || model == null) return null;
        for (TimelineTrack t : model.getTracks()) {
            if (t == null) continue;
            for (TimelineClip c : t.getClips()) {
                if (c == null) continue;
                if (c.getClipKeyframes().contains(keyframe)) {
                    return new Owner(t, c);
                }
            }
        }
        return null;
    }

    private TimelineTrack findOwnerTrackForAutomationKeyframe(TimelineSelectable keyframe) {
        if (keyframe == null || model == null) return null;
        for (TimelineTrack t : model.getTracks()) {
            if (t == null) continue;
            if (t.hasAutomationKeyframes()) {
                boolean contains = t.getAutomationKeyframes().stream()
                        .anyMatch(x -> x != null && keyframe.getId().equals(x.getId()));
                if (contains) return t;
            }
            if (keyframe instanceof com.director.timeline.model.camera.CameraKeyframe cameraKeyframe
                    && t.findCustomPathClipForKeyframe(cameraKeyframe) != null) {
                return t;
            }
        }
        return null;
    }

    private TimelineTrack findOwnerTrackForPathClip(CameraPathClip pathClip) {
        if (pathClip == null || model == null) return null;
        for (TimelineTrack t : model.getTracks()) {
            if (t == null || !t.hasCameraPathClips()) continue;
            boolean contains = t.getCameraPathClips().stream()
                    .anyMatch(x -> x != null && pathClip.getId().equals(x.getId()));
            if (contains) return t;
        }
        return null;
    }
}
