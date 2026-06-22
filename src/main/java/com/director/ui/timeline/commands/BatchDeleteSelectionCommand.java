package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.path.CameraPathClip;
import com.director.timeline.model.clip.ClipKeyframe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
            return;
        }

        // 1) Clip 优先删除（避免后续找不到 ClipKeyframe owner）
        Set<TimelineClip> clipsToDelete = new HashSet<>();
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
                TimelineCommand cmd = new DeleteClipCommand(model, clip, track.getClips());
                cmd.execute();
                executed.add(cmd);
            }
        }

        // 2) ClipKeyframe 删除（跨 clip）
        for (Object it : selectionSnapshot) {
            if (!(it instanceof ClipKeyframe kf)) continue;

            // 如果该 keyframe 所属 clip 已删，跳过
            Owner owner = findOwnerForClipKeyframe(kf);
            if (owner == null) continue;
            if (clipsToDelete.contains(owner.clip)) continue;
            if (owner.track != null && owner.track.isLocked()) continue;

            TimelineCommand cmd = new RemoveClipKeyframeCommand(model, owner.track, owner.clip, kf);
            cmd.execute();
            executed.add(cmd);
        }

        // 3) 标记删除（全局标记，不属于轨道）
        for (Object it : selectionSnapshot) {
            if (it instanceof com.director.timeline.model.TimelineMarker marker) {
                TimelineCommand cmd = new DeleteMarkerCommand(model, marker);
                cmd.execute();
                executed.add(cmd);
            }
        }

        // 4) Path Clip 删除（摄像机轨道级）
        for (Object it : selectionSnapshot) {
            if (!(it instanceof CameraPathClip pathClip)) continue;
            
            TimelineTrack track = findOwnerTrackForPathClip(pathClip);
            if (track == null) continue;
            if (track.isLocked()) continue;
            
            TimelineCommand cmd = new DeletePathClipCommand(model, track, pathClip);
            cmd.execute();
            executed.add(cmd);
        }

        // 5) 自动化关键帧删除（轨道级）
        for (Object it : selectionSnapshot) {
            if (!(it instanceof TimelineSelectable selectable)) continue;
            switch (it) {
                case TimelineClip ignored -> {
                    continue;
                }
                // 跳过标记（已在步骤3处理）
                case com.director.timeline.model.TimelineMarker ignored -> {
                    continue;
                }
                // 跳过 Path Clip（已在步骤4处理）
                case CameraPathClip ignored -> {
                    continue;
                }
                // 与现有删除逻辑一致：音频事件点默认不通过工具栏/Del 批量删除
                case com.director.timeline.model.audio.AudioEvent ignored -> {
                    continue;
                }
                default -> {
                }
            }

            TimelineTrack track = findOwnerTrackForAutomationKeyframe(selectable);
            if (track == null) continue;
            if (track.isLocked()) continue;

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
            cmd.execute();
            executed.add(cmd);
        }
    }

    @Override
    protected void doUndo() {
        if (executed.isEmpty()) return;
        // 逆序撤销，恢复依赖关系
        List<TimelineCommand> reversed = new ArrayList<>(executed);
        Collections.reverse(reversed);
        for (TimelineCommand cmd : reversed) {
            cmd.undo();
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
    
    /**
     * 查找包含指定 Path Clip 的轨道
     */
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


