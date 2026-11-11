package com.director.actor.animation.asset;

/** Time control points stay normalized; progress control points allow anticipation and overshoot. */
public record BezierEase(float x1, float y1, float x2, float y2) {
    public static final BezierEase DEFAULT = new BezierEase(1f / 3f, 0f, 2f / 3f, 1f);

    public BezierEase {
        if (!unit(x1) || !unit(x2) || !Float.isFinite(y1) || !Float.isFinite(y2))
            throw new IllegalArgumentException("Bézier X must be in [0, 1] and Y must be finite");
    }

    private static boolean unit(float value) {
        return Float.isFinite(value) && value >= 0f && value <= 1f;
    }

    public float map(float time) {
        if (time <= 0f || time >= 1f) return time;
        double low = 0, high = 1;
        for (int i = 0; i < 20; i++) {
            double middle = (low + high) * .5;
            if (coordinate(middle, x1, x2) < time) low = middle;
            else high = middle;
        }
        return (float) coordinate((low + high) * .5, y1, y2);
    }

    private static double coordinate(double t, float a, float b) {
        double u = 1 - t;
        return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t;
    }
}
