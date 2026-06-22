package com.director.timeline.audio;

import com.director.timeline.model.TimelineTrack;

/**
 * 音频轨道播放采样器生命周期端口（由 UI 层 {@code TimelineAudioController} 实现）。
 */
public interface TimelineTrackPlaybackListener {

    TimelineTrackPlaybackListener NOOP = new TimelineTrackPlaybackListener() {
        @Override
        public void onTrackAdded(TimelineTrack track) {
        }

        @Override
        public void onTrackRemoved(TimelineTrack track) {
        }
    };

    void onTrackAdded(TimelineTrack track);

    void onTrackRemoved(TimelineTrack track);
}
