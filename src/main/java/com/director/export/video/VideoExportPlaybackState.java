package com.director.export.video;

import com.director.timeline.model.TimelineModel;

/** Immutable editor playback state captured before export takes ownership of the timeline. */
final class VideoExportPlaybackState {
    private final TimelineModel model;
    private final float time;
    private final boolean playing;
    private final boolean scrubbing;

    private VideoExportPlaybackState(TimelineModel model, float time, boolean playing, boolean scrubbing) {
        this.model = model;
        this.time = time;
        this.playing = playing;
        this.scrubbing = scrubbing;
    }

    static VideoExportPlaybackState capture(TimelineModel model) {
        return model == null ? null : new VideoExportPlaybackState(
                model, model.getCurrentTime(), model.isPlaying(), model.isScrubbing());
    }

    /** Pause on the client thread before the first export frame is requested. */
    void suspend() {
        if (model == null) return;
        model.setPlaying(false);
        model.setScrubbing(false);
    }

    /** Safe to call after success, cancellation, failure or partial setup. */
    void restore() {
        if (model == null) return;
        model.setCurrentTime(time);
        model.setScrubbing(scrubbing);
        model.setPlaying(playing);
    }
}
