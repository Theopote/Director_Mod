package com.director.ui.actor.edit;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityStateClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;

public final class ActorLegacyIntervalAttachment {
    private ActorLegacyIntervalAttachment() {}

    public static boolean supports(EntityClip clip) {
        return clip instanceof EntityLookAtClip || clip instanceof EntityStateClip || clip instanceof ActorActionClip;
    }

    public static EntitySpawnClip resolve(TimelineModel model, TimelineTrack track, EntityTimelineClip wrapper) {
        var clip = wrapper.getEntityClip();
        var payload = track.getActorTrackPayload();
        if (!supports(clip) || model.isPlaying() || model.isScrubbing() || track.getType() != TrackType.ACTOR
                || track.isLocked() || track.isMuted() || payload == null || !payload.isEnabled()
                || wrapper.getParentTrack() != track || clip.getTimelineClip() != wrapper
                || !model.getAllTracks().contains(track) || !track.getClips().contains(wrapper)
                || !payload.getClips().contains(clip)) return null;
        float start = clip.getStartTime();
        float end = clip.getEndTime();
        if (!Float.isFinite(start) || !Float.isFinite(end) || end <= start) return null;

        var spawn = payload.findSpawnClipAt(start);
        if (spawn == null || spawn.getEndTime() < end || !payload.isPresentAt(end)
                || payload.findSpawnClipAt(end) != spawn || spawn.getTimelineClip() == null
                || !track.getClips().contains(spawn.getTimelineClip())) return null;
        for (var candidate : payload.getClips()) {
            if (candidate instanceof EntitySpawnClip other && other != spawn && !other.isMuted()
                    && other.getStartTime() <= end && other.getEndTime() >= start) return null;
        }
        return spawn;
    }

    public static EntityTimelineClip attach(TimelineModel model, TimelineTrack track, EntityTimelineClip wrapper) {
        var spawn = resolve(model, track, wrapper);
        if (spawn == null) return null;
        var clip = wrapper.getEntityClip();
        var payload = track.getActorTrackPayload();
        var parent = spawn.getTimelineClip();
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Attach actor interval",
                () -> {
                    if (track.getActorTrackPayload() != payload || !track.getClips().contains(parent)
                            || !contains(spawn, clip) || track.getClips().contains(wrapper))
                        throw new IllegalStateException("Actor interval attachment changed");
                    clip.setTimelineClip(wrapper);
                    if (!track.addClip(wrapper)) {
                        clip.setTimelineClip(null);
                        throw new IllegalStateException("Cannot restore legacy actor interval");
                    }
                    payload.addClip(clip);
                    remove(spawn, clip);
                    if (model.isSelected(parent)) model.selectItem(wrapper, false);
                },
                () -> {
                    if (track.getActorTrackPayload() != payload || resolve(model, track, wrapper) != spawn
                            || contains(spawn, clip)) throw new IllegalStateException("Actor interval attachment changed");
                    track.removeClip(wrapper);
                    payload.removeClip(clip.getId());
                    clip.setTimelineClip(null);
                    add(spawn, clip);
                    if (model.isSelected(wrapper)) model.selectItem(parent, false);
                }));
        return parent;
    }

    private static boolean contains(EntitySpawnClip spawn, EntityClip clip) {
        return spawn.getLookTargets().contains(clip) || spawn.getStateIntervals().contains(clip)
                || spawn.getActionIntervals().contains(clip);
    }

    private static void add(EntitySpawnClip spawn, EntityClip clip) {
        switch (clip) {
            case EntityLookAtClip look -> spawn.addLookTarget(look);
            case EntityStateClip state -> spawn.addStateInterval(state);
            case ActorActionClip action -> spawn.addActionInterval(action);
            default -> throw new IllegalArgumentException("Unsupported actor interval");
        }
    }

    private static void remove(EntitySpawnClip spawn, EntityClip clip) {
        switch (clip) {
            case EntityLookAtClip look -> spawn.removeLookTarget(look);
            case EntityStateClip state -> spawn.removeStateInterval(state);
            case ActorActionClip action -> spawn.removeActionInterval(action);
            default -> throw new IllegalArgumentException("Unsupported actor interval");
        }
    }
}
