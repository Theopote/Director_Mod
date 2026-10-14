package com.director.timeline.playback.weather;
/** Seed is scoped to particles owned by Director, leaving other emitters untouched. */
public final class WeatherParticleSeed {
    private static final ThreadLocal<Long> seed = new ThreadLocal<>();
    public static Long current() { return seed.get(); }
    public static void set(long value) { seed.set(value); }
    public static void clear() { seed.remove(); }
}
