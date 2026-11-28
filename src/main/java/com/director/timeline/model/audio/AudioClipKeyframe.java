package com.director.timeline.model.audio;

import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.clip.ClipKeyframe;

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

    private AudioClipKeyframe(java.util.UUID id, float time, TimelineInterpolation interpolation, float volume) {
        super(id, time, interpolation);
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

    @Override
    public AudioClipKeyframe snapshot() {
        return new AudioClipKeyframe(getId(), getTime(), getInterpolation(), volume);
    }

    @Override
    public boolean hasSameStateAs(ClipKeyframe other) {
        return super.hasSameStateAs(other)
                && Float.compare(volume, ((AudioClipKeyframe) other).volume) == 0;
    }
}
