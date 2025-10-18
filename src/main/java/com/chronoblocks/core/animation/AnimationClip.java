package com.chronoblocks.core.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AnimationClip holds a set of Tracks that animate blocks/containers.
 */
public class AnimationClip {
    private String name;
    private final List<Track> tracks = new ArrayList<>();

    public AnimationClip(String name) {
        this.name = name != null ? name : "";
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : ""; }

    public List<Track> getTracks() { return Collections.unmodifiableList(tracks); }

    public void addTrack(Track track) {
        if (track != null) tracks.add(track);
    }

    public boolean removeTrack(Track track) {
        return tracks.remove(track);
    }

    public void clearTracks() { tracks.clear(); }

    /**
     * Computes the clip duration as the max last keyframe time among all tracks.
     */
    public float computeDuration() {
        float max = 0f;
        for (Track t : tracks) {
            List<Keyframe> kfs = t.getKeyframes();
            if (!kfs.isEmpty()) {
                float last = 0f;
                for (Keyframe kf : kfs) last = Math.max(last, kf.getTime());
                max = Math.max(max, last);
            }
        }
        return max;
    }
}
