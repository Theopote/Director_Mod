package com.director.timeline.playback.block;

/** A forward playback interval. Paused property edits and scrubbing never replay bursts. */
public final class WholeContainerEffectCursor {
    private float previousTime = Float.NaN;
    private boolean previousPlaying, previousScrubbing;
    private float from, to;
    private boolean forward, starting, pendingStart;

    public void reset() { previousTime = Float.NaN; previousPlaying = previousScrubbing = forward = starting = pendingStart = false; }
    public void beginFrame(float time, boolean playing, boolean scrubbing) {
        from = previousTime; to = time;
        if (playing && (!previousPlaying || !scrubbing && time < from)) pendingStart = true;
        starting = pendingStart;
        // Include the final advancing frame if the player just reached its stop time.
        forward = Float.isFinite(from) && Float.isFinite(to) && to > from
                && (playing || previousPlaying) && !scrubbing && !previousScrubbing;
        if (forward || !playing || scrubbing) pendingStart = false;
        previousTime = time; previousPlaying = playing; previousScrubbing = scrubbing;
    }
    public boolean shouldClearEffects(boolean scrubbing) {
        return scrubbing || Float.isFinite(from) && Float.isFinite(to) && to < from;
    }
    public boolean crosses(float eventTime) {
        return forward && Float.isFinite(eventTime) && eventTime <= to
                && (eventTime > from || starting && eventTime == from);
    }
}
