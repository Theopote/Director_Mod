package com.director.export.video;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityActionClip;
import com.director.timeline.playback.entity.AnimationEventClock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Offline collection of actor animation {@code PLAY_SOUND} cues for video audio export.
 * Preview still plays through the realtime sound manager; this path feeds {@link ScheduledAudioMixer}.
 */
public final class ActorAnimationSoundExport {
    public record Cue(float timeSec, String soundId, float volume, float pitch) {}

    private ActorAnimationSoundExport() {}

    public static List<Cue> collect(TimelineModel model, float startTimeSec, float endTimeSec) {
        if (model == null || !Float.isFinite(startTimeSec) || !Float.isFinite(endTimeSec) || endTimeSec < startTimeSec) {
            return List.of();
        }
        List<Cue> result = new ArrayList<>();
        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.isMuted() || track.getType() != TrackType.ACTOR) continue;
            var payload = track.getActorTrackPayload();
            if (payload == null) continue;
            for (var clip : payload.getClips()) {
                if (clip == null || clip.isMuted()) continue;
                if (clip instanceof EntityAnimationClip animation) {
                    appendPlaySounds(payload, animation, startTimeSec, endTimeSec, result);
                } else if (clip instanceof EntitySpawnClip spawn) {
                    var bone = spawn.getBoneAnimation();
                    if (bone != null && !bone.isMuted()) {
                        appendPlaySounds(payload, bone, startTimeSec, endTimeSec, result);
                    }
                }
            }
        }
        result.sort(Comparator.comparingDouble(Cue::timeSec));
        return List.copyOf(result);
    }

    private static void appendPlaySounds(com.director.timeline.model.entity.ActorTrack track,
                                         EntityAnimationClip clip,
                                         float from, float to, List<Cue> out) {
        if (clip == null || !clip.hasEvents()) return;
        var clock = AnimationEventClock.projection(track, clip, from, to);
        for (var event : clip.getEvents()) {
            if (event == null || event.getAction() == null) continue;
            if (event.getAction().type() != EntityActionClip.ActionType.PLAY_SOUND) continue;
            var params = event.getAction().params();
            String soundId = params.get("soundId") instanceof String text ? text : "";
            if (soundId.isBlank()) continue;
            float volume = number(params.get("volume"), .8f);
            float pitch = number(params.get("pitch"), 1f);
            for (float at : clock.times(event)) {
                if (at >= from && at <= to) out.add(new Cue(at, soundId, volume, pitch));
            }
        }
    }

    private static float number(Object raw, float fallback) {
        return raw instanceof Number n && Float.isFinite(n.floatValue()) ? n.floatValue() : fallback;
    }
}
