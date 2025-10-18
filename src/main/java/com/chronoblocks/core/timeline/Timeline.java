package com.chronoblocks.core.timeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Timeline - Holds multiple tracks and global playback range.
 */
public class Timeline {
    private final List<Track> tracks = new ArrayList<>();
    private double durationSeconds = 10.0; // default

    public List<Track> getTracks() { return tracks; }
    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = Math.max(0, durationSeconds); }

    public void addTrack(Track track) { tracks.add(track); }
    public void removeTrack(Track track) { tracks.remove(track); }
}
