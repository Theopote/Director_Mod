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
    /** Apply one delta to the whole selection, preserving every spacing. */
    public static float groupDelta(java.util.Collection<Float> times, float pixels, float pps,
                                   float speed, float duration, float clipDuration) {
        if (times==null || times.isEmpty() || !Float.isFinite(pixels) || !Float.isFinite(pps) || pps<=0
                || !Float.isFinite(speed) || !Float.isFinite(duration) || !Float.isFinite(clipDuration)) return 0;
        double rate=Math.max(.01f,speed), limit=Math.max(0,Math.min(duration,clipDuration*rate));
        double lower=Double.NEGATIVE_INFINITY, upper=Double.POSITIVE_INFINITY;
        for (Float time:times) {
            if(time==null || !Float.isFinite(time) || time<0 || time>limit) return 0;
            lower=Math.max(lower,-time); upper=Math.min(upper,limit-time);
        }
        return (float)Math.max(lower,Math.min(upper,pixels/(double)pps*rate));
    }

}
