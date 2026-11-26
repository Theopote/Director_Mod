package com.director.ui.actor.lookat;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;

public final class ActorLookIntervalEditing {
    private ActorLookIntervalEditing() {}

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
