package com.director.ui.actor.edit;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.ActorPoseClip;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.EntityStateClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;

public final class ActorOwnedIntervalEditing {
    private ActorOwnedIntervalEditing() {}

    public enum DragMode { MOVE, START, END }

    public record Range(float start, float end) {}

    public static Range dragRange(Range original, Range bounds, DragMode mode, float delta) {
        return dragRange(original, bounds, mode, delta, 0.01f);
    }

    public static Range dragRange(Range original, Range bounds, DragMode mode, float delta, float minimum) {
        float length = original.end - original.start;
        float minLength = Math.min(length, minimum);
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

    public static boolean editable(TimelineModel model, EntitySpawnClip spawn, EntityClip target) {
        return editableSpawn(model, spawn) && contains(spawn, target);
    }

    public static boolean contains(EntitySpawnClip spawn, EntityClip target) {
        return switch (target) {
            case EntityLookAtClip look -> spawn.getLookTargets().contains(look);
            case EntityStateClip state -> spawn.getStateIntervals().contains(state);
            case ActorActionClip action -> spawn.getActionIntervals().contains(action);
            case ActorPoseClip pose -> spawn.getPoseIntervals().contains(pose);
            case null, default -> false;
        };
    }

    private static boolean editableSpawn(TimelineModel model, EntitySpawnClip spawn) {
        var wrapper = spawn.getTimelineClip();
        var track = wrapper == null ? null : wrapper.getParentTrack();
        return model != null && !model.isPlaying() && !model.isScrubbing()
                && track != null && !track.isLocked() && !track.isMuted() && !spawn.isMuted()
                && model.getAllTracks().contains(track) && track.getClips().contains(wrapper)
                && track.getActorTrackPayload() != null && track.getActorTrackPayload().getClips().contains(spawn);
    }

    public static boolean canPaste(TimelineModel model, EntitySpawnClip spawn, EntityClip clipboard) {
        if (!editableSpawn(model, spawn) || clipboard == null) return false;
        var payload = spawn.getTimelineClip().getParentTrack().getActorTrackPayload();
        float start = model.getCurrentTime();
        float end = start + clipboard.getLength();
        if (!Float.isFinite(end) || end <= start || end > spawn.getEndTime()
                || payload.findSpawnClipAt(start) != spawn || !payload.isPresentAt(start) || payload.getActorRef() == null) return false;

        var actor = com.director.actor.ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
        if (actor == null) return false;
        if (clipboard instanceof ActorActionClip action
                && (!com.director.actor.ActorCapabilities.of(actor).supports(action.getActionType())
                    || payload.hasActionOverlap(spawn, null, start, end))) return false;
        if (clipboard instanceof ActorPoseClip && !com.director.actor.ActorCapabilities.of(actor).humanoidPose()) return false;

        return clipboard instanceof EntityLookAtClip || clipboard instanceof EntityStateClip
                || clipboard instanceof ActorPoseClip || clipboard instanceof ActorActionClip;
    }

    public static EntityClip paste(TimelineModel model, EntitySpawnClip spawn, EntityClip clipboard) {
        if (!canPaste(model, spawn, clipboard)) return null;
        float start = model.getCurrentTime();
        float end = start + clipboard.getLength();
        var copy = com.director.timeline.model.TimelineClipCopies.copyEntityClip(clipboard);
        copy.setTimeRange(start, end);
        var change = intervalChange(spawn, copy);
        if (change == null) return null;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Paste actor interval", change.remove, change.add));
        return copy;
    }

    public static boolean resize(TimelineModel model, EntitySpawnClip spawn, EntityClip target,
                                 float start, float end) {
        if (!editable(model, spawn, target) || !Float.isFinite(start) || !Float.isFinite(end)
                || start < spawn.getStartTime() || end > spawn.getEndTime() || end <= start) return false;
        if (target instanceof ActorActionClip && (end - start < .1f - 1e-5f
                || spawn.getTimelineClip().getParentTrack().getActorTrackPayload().hasActionOverlap(spawn, target, start, end)))
            return false;
        float beforeStart = target.getStartTime();
        float beforeEnd = target.getEndTime();
        if (start == beforeStart && end == beforeEnd) return true;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Change actor interval",
                () -> setRange(spawn, target, beforeStart, beforeEnd),
                () -> setRange(spawn, target, start, end)));
        return true;
    }

    public static boolean remove(TimelineModel model, EntitySpawnClip spawn, EntityClip target) {
        if (!editable(model, spawn, target)) return false;
        var change = intervalChange(spawn, target);
        if (change == null) return false;
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Remove actor interval", change.add, change.remove));
        return true;
    }

    private record IntervalChange(Runnable add, Runnable remove) {}

    private static IntervalChange intervalChange(EntitySpawnClip spawn, EntityClip target) {
        return switch (target) {
            case EntityLookAtClip look -> new IntervalChange(() -> spawn.addLookTarget(look), () -> spawn.removeLookTarget(look));
            case EntityStateClip state -> new IntervalChange(() -> spawn.addStateInterval(state), () -> spawn.removeStateInterval(state));
            case ActorActionClip action -> new IntervalChange(() -> spawn.addActionInterval(action), () -> spawn.removeActionInterval(action));
            case ActorPoseClip pose -> new IntervalChange(() -> spawn.addPoseInterval(pose), () -> spawn.removePoseInterval(pose));
            case null, default -> null;
        };
    }

    private static void setRange(EntitySpawnClip spawn, EntityClip target, float start, float end) {
        switch (target) {
            case EntityLookAtClip look -> spawn.editLookTarget(look, clip -> clip.setTimeRange(start, end));
            case EntityStateClip state -> spawn.editStateInterval(state, clip -> clip.setTimeRange(start, end));
            case ActorActionClip action -> spawn.editActionInterval(action, clip -> clip.setTimeRange(start, end));
            case ActorPoseClip pose -> spawn.editPoseInterval(pose, clip -> clip.setTimeRange(start, end));
            default -> throw new IllegalArgumentException("Unsupported actor interval");
        }
    }
}
