package com.director.actor.animation.asset;

/** Named point in an animation, used later for sound, prop and action events. */
public record AnimationMarker(float time, String name) {
    public AnimationMarker {
        if (!Float.isFinite(time) || time < 0f) throw new IllegalArgumentException("Marker time must be finite and non-negative");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Marker name must not be blank");
    }
}
