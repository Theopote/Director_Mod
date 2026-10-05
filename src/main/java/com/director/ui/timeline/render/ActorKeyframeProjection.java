package com.director.ui.timeline.render;

import com.director.actor.animation.asset.AnimationLoopMode;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Projects asset keys into a visible clip interval without walking off-screen cycles. */
public final class ActorKeyframeProjection {
    private ActorKeyframeProjection() {}

    public static List<Float> times(float key, float duration, AnimationLoopMode mode,
                                    float start, float end, float speed, float visibleStart, float visibleEnd) {
        if (!Float.isFinite(key) || !Float.isFinite(duration) || !Float.isFinite(start)
                || !Float.isFinite(end) || !Float.isFinite(speed) || !Float.isFinite(visibleStart)
                || !Float.isFinite(visibleEnd) || duration < 0 || key < 0 || key > duration) return List.of();
        double rate = Math.max(0.01f, speed);
        double lo = Math.max(start, visibleStart), hi = Math.min(end, visibleEnd);
        if (lo > hi) return List.of();
        TreeSet<Float> result = new TreeSet<>();
        if (mode == AnimationLoopMode.ONCE || duration == 0 || mode == null) {
            double time = start + key / rate;
            if (time >= lo && time <= hi) result.add((float) time);
        } else {
            // A LOOP endpoint wraps to zero and never samples the duration key itself.
            if (mode == AnimationLoopMode.LOOP && key == duration) return List.of();
            double period = mode == AnimationLoopMode.LOOP ? duration : 2.0 * duration;
            add(result, key, period, start, rate, lo, hi);
            if (mode == AnimationLoopMode.PING_PONG && key > 0 && key < duration)
                add(result, period - key, period, start, rate, lo, hi);
        }
        return new ArrayList<>(result);
    }

    /** Seek to the nearest actual occurrence, independent of zoom-out display decimation. */
    public static java.util.Optional<Float> nearest(float key,float duration,AnimationLoopMode mode,
            float start,float end,float speed,float current) {
        if(!Float.isFinite(current) || !Float.isFinite(start) || !Float.isFinite(end) || start>end) return java.util.Optional.empty();
        float anchor=Math.clamp(current,start,end);
        double period=(mode==AnimationLoopMode.PING_PONG?2.0:1.0)*duration/Math.max(.01f,speed);
        float lo=start,hi=end;
        if(mode!=null && mode!=AnimationLoopMode.ONCE && Double.isFinite(period) && period>0) {
            lo=(float)Math.max(start,anchor-period*2); hi=(float)Math.min(end,anchor+period*2);
        }
        return times(key,duration,mode,start,end,speed,lo,hi).stream()
                .min(java.util.Comparator.<Float>comparingDouble(t -> Math.abs((double)t-current)).thenComparingDouble(t -> t));
    }
    private static void add(TreeSet<Float> result, double phase, double period, float start,
                            double rate, double lo, double hi) {
        double first = Math.max(0, Math.ceil(((lo - start) * rate - phase) / period));
        double last = Math.floor(((hi - start) * rate - phase) / period);
        // Bound dense repeats at extreme zoom-out. These are display representatives, not new keys.
        double stride = Math.max(1, Math.ceil((last - first + 1) / 1024));
        for (int i = 0; i < 1024; i++) {
            double cycle = first + i * stride;
            if (cycle > last) break;
            double time = start + (phase + cycle * period) / rate;
            if (time >= lo && time <= hi) result.add((float) time);
        }
    }
}
