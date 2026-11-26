package com.director.ui.actor.edit;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.ActorActionClip;
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
        var wrapper = spawn.getTimelineClip();
        var track = wrapper == null ? null : wrapper.getParentTrack();
        return model != null && !model.isPlaying() && !model.isScrubbing()
                && track != null && !track.isLocked() && !track.isMuted() && !spawn.isMuted()
                && model.getAllTracks().contains(track) && track.getClips().contains(wrapper)
                && track.getActorTrackPayload() != null && track.getActorTrackPayload().getClips().contains(spawn)
                && switch (target) {
                    case EntityLookAtClip look -> spawn.getLookTargets().contains(look);
                    case EntityStateClip state -> spawn.getStateIntervals().contains(state);
                    case ActorActionClip action -> spawn.getActionIntervals().contains(action);
                    default -> false;
                };
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
        Runnable restore;
        Runnable remove;
        if (target instanceof EntityLookAtClip look) {
            restore = () -> spawn.addLookTarget(look);
            remove = () -> spawn.removeLookTarget(look);
        } else if (target instanceof EntityStateClip state) {
            restore = () -> spawn.addStateInterval(state);
            remove = () -> spawn.removeStateInterval(state);
        } else if (target instanceof ActorActionClip action) {
            restore = () -> spawn.addActionInterval(action);
            remove = () -> spawn.removeActionInterval(action);
        } else {
            return false;
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Remove actor interval", restore, remove));
        return true;
    }

    private static void setRange(EntitySpawnClip spawn, EntityClip target, float start, float end) {
        switch (target) {
            case EntityLookAtClip look -> spawn.editLookTarget(look, clip -> clip.setTimeRange(start, end));
            case EntityStateClip state -> spawn.editStateInterval(state, clip -> clip.setTimeRange(start, end));
            case ActorActionClip action -> spawn.editActionInterval(action, clip -> clip.setTimeRange(start, end));
            default -> throw new IllegalArgumentException("Unsupported actor interval");
        }
    }
}
