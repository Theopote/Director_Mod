package com.director.ui.timeline.render;

/** Asset-local drag bounds for a non-repeating clip. */
public final class ActorKeyframeDrag {
    private ActorKeyframeDrag() {}
    public static float target(float source, float pixels, float pps, float speed, float duration, float clipDuration) {
        if (!Float.isFinite(source) || !Float.isFinite(pixels) || !Float.isFinite(pps) || pps <= 0
                || !Float.isFinite(speed) || !Float.isFinite(duration) || !Float.isFinite(clipDuration)) return source;
        double rate = Math.max(0.01f, speed);
        double limit = Math.max(0, Math.min(duration, clipDuration * rate));
        return (float) Math.max(0, Math.min(limit, source + pixels / (double) pps * rate));
    }
}
