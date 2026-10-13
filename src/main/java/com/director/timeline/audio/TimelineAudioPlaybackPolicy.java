package com.director.timeline.audio;

import com.director.timeline.model.*;
import java.util.List;

/** Shared track audibility rules for live playback and offline export. */
public final class TimelineAudioPlaybackPolicy {
    private TimelineAudioPlaybackPolicy() {}
    public static boolean isAudible(List<TimelineTrack> tracks, TimelineTrack track) {
        if (track == null || track.getType() != TrackType.AUDIO || !track.isVisible() || track.isMuted()) return false;
        boolean solo = tracks.stream().anyMatch(t -> t != null && t.getType() == TrackType.AUDIO
                && t.isVisible() && !t.isMuted() && t.isSolo());
        return !solo || track.isSolo();
    }
}
