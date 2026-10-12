package com.director.core.animation;

/** Forward threshold crossings, including frame skips; rewinding rearms the next crossing. */
final class PerBlockEffectTrigger {
    private PerBlockEffectTrigger() {}
    static boolean crossed(float previous, float current, float threshold) {
        if (Float.isNaN(previous))
            return current >= threshold && current <= threshold + (threshold == 0 ? 0.05f : 0.0001f);
        if (current < previous) return threshold == 0 && current == 0;
        return previous < threshold && current >= threshold;
    }
}
