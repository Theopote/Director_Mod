package com.director.core.animation;

import com.director.core.container.AnimationMode;

/** One clip-local clock for sequence visibility, individual transforms and audio. */
public final class PerBlockAnimationTiming {
    private PerBlockAnimationTiming() {}
    public record Window(float start, float end) {}
    public record Layout(float start, float end, float delay, float duration, float sequenceSpan) {
        public Window window(float normalized) {
            float n = Float.isFinite(normalized) ? Math.clamp(normalized, 0, 1) : 0;
            float blockStart = Math.min(end, start + delay + n * sequenceSpan);
            return new Window(blockStart, Math.min(end, blockStart + duration));
        }
        public float threshold(float elapsed) {
            return sequenceSpan > 0 ? elapsed / sequenceSpan
                    : elapsed >= 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
    }
    public static Layout layout(ClipAnimationConfig config, float start, float end) {
        end = Math.max(start, end);
        float length = end - start;
        float delay = Math.max(0, config.getDelay()) + Math.max(0, config.getSequenceParameters().getGlobalDelay());
        delay = Math.min(length, delay);
        float available = length - delay;
        float duration = config.getMode() == AnimationMode.INSTANT ? 0 : Math.min(config.getDuration(), available);
        return new Layout(start, end, delay, duration, Math.max(0, available - duration));
    }
}
