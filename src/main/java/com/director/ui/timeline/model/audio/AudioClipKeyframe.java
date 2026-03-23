package com.director.ui.timeline.model.audio;

import com.director.ui.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.clip.ClipKeyframe;

/**
 * AudioClipKeyframe
 *
 * <p>Represents volume automation keyframes inside an audio clip.</p>
 */
public class AudioClipKeyframe extends ClipKeyframe {
    private float volume = 1.0f;

    public AudioClipKeyframe(float time) {
        super(time);
    }

    public AudioClipKeyframe(float time, TimelineInterpolation interpolation, float volume) {
        super(time, interpolation);
        setVolume(volume);
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    @Override
    public String getType() {
        return "AudioClipKeyframe";
    }

    @Override
    public String getName() {
        return String.format("Audio Keyframe @ %.3fs", getTime());
    }

    @Override
    public AudioClipKeyframe copy() {
        return new AudioClipKeyframe(getTime(), getInterpolation(), volume);
    }
}
