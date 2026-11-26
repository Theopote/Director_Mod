package com.director.ui.actor.lookat;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;

public final class ActorLookIntervalEditing {
    private ActorLookIntervalEditing() {}

    public enum DragMode { MOVE, START, END }

    public record Range(float start, float end) {}

    public static Range dragRange(Range original, Range bounds, DragMode mode, float delta) {
        float length = original.end - original.start;
        float minLength = Math.min(length, 0.01f);
        return switch (mode) {
            case MOVE -> {
                float shift = Math.clamp(delta, bounds.start - original.start, bounds.end - original.end);
                yield new Range(Math.max(bounds.start, original.start + shift),
                        Math.min(bounds.end, original.end + shift));
            }
            case START -> new Range(Math.clamp(original.start + delta, bounds.start,
                    Math.max(original.start, original.end - minLength)), original.end);
            case END -> new Range(original.start, Math.clamp(original.end + delta,
                    Math.min(original.end, original.start + minLength), bounds.end));
        };
    }

    public static boolean editable(TimelineModel model, EntitySpawnClip spawn, EntityLookAtClip target) {
        var wrapper = spawn.getTimelineClip();
        var track = wrapper == null ? null : wrapper.getParentTrack();
        return model != null && !model.isPlaying() && !model.isScrubbing()
                && track != null && !track.isLocked() && !track.isMuted() && !spawn.isMuted()
                && model.getAllTracks().contains(track) && track.getClips().contains(wrapper)
                && track.getActorTrackPayload() != null && track.getActorTrackPayload().getClips().contains(spawn)
                && spawn.getLookTargets().contains(target);
    }

    public static boolean resize(TimelineModel model, EntitySpawnClip spawn, EntityLookAtClip target,
                                 float start, float end) {
        if (!editable(model, spawn, target) || !Float.isFinite(start) || !Float.isFinite(end)
                || start < spawn.getStartTime() || end > spawn.getEndTime() || end <= start) return false;
        float beforeStart = target.getStartTime();
        float beforeEnd = target.getEndTime();
        if (start == beforeStart && end == beforeEnd) return true;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Change actor look-at interval",
                () -> spawn.editLookTarget(target, clip -> clip.setTimeRange(beforeStart, beforeEnd)),
                () -> spawn.editLookTarget(target, clip -> clip.setTimeRange(start, end))));
        return true;
    }

    public static boolean remove(TimelineModel model, EntitySpawnClip spawn, EntityLookAtClip target) {
        if (!editable(model, spawn, target)) return false;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Remove actor look-at interval",
                () -> spawn.addLookTarget(target), () -> spawn.removeLookTarget(target)));
        return true;
    }
}
