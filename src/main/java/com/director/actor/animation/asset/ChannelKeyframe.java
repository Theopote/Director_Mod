package com.director.actor.animation.asset;

import java.util.Objects;

/** One value on a typed animation channel. */
public record ChannelKeyframe<T>(float time, T value, InterpolationMode interpolation) {
    public ChannelKeyframe {
        if (!Float.isFinite(time) || time < 0f) throw new IllegalArgumentException("Keyframe time must be finite and non-negative");
        Objects.requireNonNull(value, "value");
        interpolation = interpolation == null ? InterpolationMode.SMOOTH : interpolation;
    }
}
