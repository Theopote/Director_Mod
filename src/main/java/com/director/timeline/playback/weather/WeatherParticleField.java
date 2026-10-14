package com.director.timeline.playback.weather;

import java.util.ArrayList;
import java.util.List;

/** Reconstructible weather particles in a camera-local volume; no wall-clock state. */
public final class WeatherParticleField {
    public static final int LIMIT = 12000;
    public enum Kind { RAIN, SNOW, SPLASH, CLOUD }
    public record Point(long id, Kind kind, double x, double y, double z, float age, long seed) {}
    public static List<Point> sample(WeatherEffectSampler effects, float time) {
        if (effects == null || !Float.isFinite(time) || time < 0) return List.of();
        var result=new ArrayList<Point>(); long tick=(long)Math.floor(time*20d);
        for(long cell=tick;cell>=Math.max(0,tick-39) && result.size()<LIMIT;cell--) {
            float age=time-cell/20f; var s=effects.sample(cell/20f);
            if (!s.enabled) continue;
            add(result,effects,cell,0,Kind.SNOW,Math.round(300*s.snowIntensity),age,2,s);
            add(result,effects,cell,1,Kind.RAIN,Math.round(50*s.rainIntensity),age,.25f,s);
            add(result,effects,cell,2,Kind.SPLASH,Math.round(6*s.wetness),age,.25f,s);
            var kind=s.specialWeatherType;
            int count=switch(kind) { case SANDSTORM -> 15; case BLIZZARD -> 28;
                case FOGBURST -> 4; case HEATWAVE -> 2; default -> 0; };
            add(result,effects,cell,3,kind==com.director.timeline.model.weather.WeatherKeyframe.SpecialWeatherType.BLIZZARD
                    ? Kind.SNOW : Kind.CLOUD,Math.round(count*s.specialWeatherIntensity),age,1,s);
        }
        return List.copyOf(result);
    }
    private static void add(List<Point> out,WeatherEffectSampler effects,long cell,int channel,Kind kind,
            int count,float age,float life,WeatherSample s) {
        if(age<0 || age>=life)return;
        double angle=Math.toRadians(s.windDirectionDegrees);
        double windX=Math.cos(angle)*s.windStrength*age*12;
        double windZ=Math.sin(angle)*s.windStrength*age*12;
        for(int i=0;i<count && out.size()<LIMIT;i++) {
            long seed=effects.seedFor(cell ^ ((long)channel<<48) ^ ((long)i<<32));
            double x=(fraction(seed,0)-.5)*64,z=(fraction(seed,1)-.5)*64;
            double y=kind==Kind.SNOW ? 10+fraction(seed,2)*35-age*(9+fraction(seed,3)*9)
                    : kind==Kind.CLOUD ? 2+fraction(seed,2)*10+age : .08;
            if(kind==Kind.SNOW || kind==Kind.CLOUD) {
                x+=windX; z+=windZ;
            }
            out.add(new Point(seed,kind,x,y,z,age,seed));
        }
    }
    private static double fraction(long seed,int stream) {
        long n=seed+stream*0x9e3779b97f4a7c15L;
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        return ((n^(n>>>31))>>>11)*0x1.0p-53;
    }
}
