package com.chronoblocks.core.animation;

/**
 * PlaybackController - Controls play/pause/seek over the active Timeline.
 */
public class PlaybackController {
    private double playheadSeconds = 0.0;
    private boolean playing = false;
    private double speed = 1.0; // playback speed multiplier

    public void play() { playing = true; }
    public void pause() { playing = false; }
    public void stop() { playing = false; playheadSeconds = 0.0; }

    public void seek(double timeSeconds) { playheadSeconds = Math.max(0, timeSeconds); }
    public void setSpeed(double speed) { this.speed = Math.max(0, speed); }

    public void tick(double deltaSeconds) {
        if (playing) {
            playheadSeconds += deltaSeconds * speed;
        }
    }

    public double getPlayheadSeconds() { return playheadSeconds; }
    public boolean isPlaying() { return playing; }
}
