package com.director.timeline.playback.weather;

import java.util.ArrayList;
import java.util.List;

/** Weather effects are functions of project time, independent of rendering retries. */
public final class WeatherEffectSampler {
    private final WeatherTrackSampler sampler;
    private final long seed;
    private final double[] times, phases;
    public WeatherEffectSampler(WeatherTrackSampler sampler) {
        this.sampler = sampler;
        var keys = sampler.getKeyframes();
        seed = keys.isEmpty() ? 0 : keys.getFirst().getId().getLeastSignificantBits();
        List<Double> boundaries = new ArrayList<>(); boundaries.add(0d);
        for (var key : keys) if (key.getTime() > boundaries.getLast()) boundaries.add((double) key.getTime());
        times = boundaries.stream().mapToDouble(Double::doubleValue).toArray();
        phases = new double[times.length];
        for (int i=1;i<times.length;i++) phases[i]=phases[i-1]+integrate(times[i-1],times[i]);
    }
    public WeatherSample sample(float time) { return sampler.sample(time); }
    public long seedFor(long tick) {
        long n = seed ^ (tick * 0x9e3779b97f4a7c15L);
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }
    public boolean lightningAt(long tick) {
        if (tick < 0) return false;
        var s=sample(tick / 20f);
        return s.enabled && s.thunderIntensity>.01f && s.lightningFrequency>.01f
                && (seedFor(tick) >>> 11) * 0x1.0p-53 < Math.min(1, s.lightningFrequency/20d);
    }
    public float flash(float time) {
        if (!Float.isFinite(time) || time < 0) return 0;
        long tick=(long)Math.floor(time*20d); float flash=0;
        for(int age=0;age<=13;age++) {
            long n=tick-age;
            if(n<0 || !lightningAt(n))continue;
            var s=sample(n/20f);
            flash=Math.max(flash,(.6f+.4f*s.thunderIntensity)*Math.max(0,1-(time-n/20f)/.65f));
        }
        return flash;
    }
    public float cloudPhase(float time) {
        if (!Float.isFinite(time) || time <= 0) return 0;
        int i=times.length-1;
        while(i>0 && times[i]>time)i--;
        double remainder = i==times.length-1 ? (time-times[i])*speed(time)
                : integrate(times[i],time);
        return (float)Math.min(Float.MAX_VALUE,20*(phases[i]+remainder));
    }
    private double integrate(double start,double end) {
        double sum=0, step=(end-start)/64;
        for(int i=0;i<64;i++)sum+=speed((float)(start+(i+.5)*step));
        return sum*step;
    }
    private double speed(float time) {
        var s=sample(time);
        return Math.max(0,Math.min(4,s.cloudSpeed*(1+Math.max(0,Math.min(1,s.windStrength))*.6)));
    }
}
