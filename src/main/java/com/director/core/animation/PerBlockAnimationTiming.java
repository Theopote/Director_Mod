package com.director.core.animation;

import com.director.core.container.AnimationMode;
import com.director.core.animation.sequence.BlockSequenceEntry;

/** Independent clocks for sequence starts and the action of each block. */
public final class PerBlockAnimationTiming {
    private PerBlockAnimationTiming() {}
    public record Window(float start, float end) {}
    public record Layout(float start, float end, float delay, float duration, float sequenceSpan) {
        public Window window(float normalized) {
            float n = Float.isFinite(normalized) ? Math.clamp(normalized, 0, 1) : 0;
            float blockStart = start + delay + n * sequenceSpan;
            return new Window(blockStart, blockStart + duration);
        }
        public float threshold(float elapsed) {
            return sequenceSpan > 0 ? elapsed / sequenceSpan
                    : elapsed >= 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
    }
    public static Layout layout(ClipAnimationConfig config, float start, float end, BlockSequenceEntry entry) {
        return layout(config, start, end);
    }
    public static Layout layout(ClipAnimationConfig config, float start, float end) {
        float length = Math.max(0, end - start);
        float delay = Math.max(0, config.getDelay()) + Math.max(0, config.getSequenceParameters().getGlobalDelay());
        float duration = config.getMode() == AnimationMode.INSTANT ? 0 : config.getDuration();
        return new Layout(start, Math.max(start, end), delay, duration, length);
    }
    /** Upper bound for the tail; the clip itself continues to describe only the trigger span. */
    public static float completionLimit(ClipAnimationConfig config, float start, float end) {
        if (config.isWholeContainer()) return end;
        var timing = layout(config, start, end);
        boolean simultaneous = config.getSequenceParameters().getConcurrencyMode()
                == com.director.core.animation.sequence.ConcurrencyMode.ALL_AT_ONCE;
        return (simultaneous ? start : end) + timing.delay() + timing.duration();
    }
}
