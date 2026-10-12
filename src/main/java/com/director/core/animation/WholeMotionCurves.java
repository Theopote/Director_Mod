package com.director.core.animation;

/** Normalized damped spring response and ballistic hops with a restitution coefficient. */
public final class WholeMotionCurves {
    private WholeMotionCurves() {}
    public static float spring(float phase, int cycles, float damping) {
        double t = Math.clamp(phase, 0, 1);
        double w = 2 * Math.PI * cycles;
        double response = 1 - Math.exp(-damping * t) * (Math.cos(w * t) + damping / w * Math.sin(w * t));
        // Blend the last small residual away with zero terminal velocity.
        double settle = Math.clamp((t - 0.85) / 0.15, 0, 1);
        settle = settle * settle * settle * (settle * (settle * 6 - 15) + 10);
        return (float) (response * (1 - settle) + settle);
    }
    public static float arrival(float phase) {
        float fall = Math.clamp(phase / 0.4f, 0, 1);
        return fall * fall; // constant acceleration into the first contact
    }
    public static float bounce(float phase, int cycles, float damping) {
        if (phase <= 0.4f || phase >= 1) return 0;
        double restitution = Math.exp(-damping / 10);
        double sum = 0;
        for (int i = 0; i < cycles; i++) sum += Math.pow(restitution, i);
        double elapsed = (phase - 0.4) / 0.6;
        double start = 0;
        for (int i = 0; i < cycles; i++) {
            double duration = Math.pow(restitution, i) / sum;
            if (elapsed <= start + duration) {
                double u = Math.clamp((elapsed - start) / duration, 0, 1);
                double hop = 4 * u * (1 - u) * Math.pow(restitution, 2 * i);
                if (i == cycles - 1 && u > 0.8) {
                    double end = (u - 0.8) / 0.2;
                    hop *= 1 - end * end * (3 - 2 * end);
                }
                return (float) hop;
            }
            start += duration;
        }
        return 0;
    }
}
