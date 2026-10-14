package com.director.season;

import net.minecraft.util.math.BlockPos;

/** Stable multiscale value noise, anchored to world X/Z rather than camera or scan order. */
public final class NoiseField {
    private static final long SNOW_SEED = 12345L, ICE_SEED = 67890L;
    private NoiseField() {}
    private static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static double lattice(int x,int z,long seed) {
        long hash=mix(seed ^ mix((long)x*0x9e3779b97f4a7c15L) ^ mix((long)z*0xd1b54a32d192ed03L));
        return (hash>>>11)*0x1.0p-53;
    }
    private static double fade(double t) { return t*t*t*(t*(t*6-15)+10); }
    private static double valueNoise(double x,double z,double scale,long seed) {
        x/=scale;z/=scale;int ix=(int)Math.floor(x),iz=(int)Math.floor(z);
        double u=fade(x-ix),v=fade(z-iz);
        double a=lattice(ix,iz,seed),b=lattice(ix+1,iz,seed),c=lattice(ix,iz+1,seed),d=lattice(ix+1,iz+1,seed);
        return (a+(b-a)*u)*(1-v)+(c+(d-c)*u)*v;
    }
    private static double field(double x,double z,long seed,double scale) {
        return .62*valueNoise(x,z,scale,seed)+.28*valueNoise(x,z,scale*.32,seed+1)+.10*valueNoise(x,z,scale*.1,seed+2);
    }
    /** Approximate CDF spreads the central noise distribution across the coverage slider range. */
    private static float rank(double value) {
        double x=(value-.5)/(.155*Math.sqrt(2));
        double sign=x<0?-1:1;x=Math.abs(x);
        double t=1/(1+.3275911*x);
        double erf=1-(((((1.061405429*t-1.453152027)*t)+1.421413741)*t-.284496736)*t+.254829592)*t*Math.exp(-x*x);
        return (float)Math.max(0,Math.min(1,.5*(1+sign*erf)));
    }
    public static float noise2D(float x,float z,long seed) {
        return Float.isFinite(x)&&Float.isFinite(z)?rank(field(x,z,seed,32)): .5f;
    }
    /** Lower values are covered first. Snow islands contain medium and small edge detail. */
    public static float snowCoverage(BlockPos pos) { return rank(field(pos.getX(),pos.getZ(),SNOW_SEED,32)); }
    /** Ice has broader sheets with finer irregular edges. */
    public static float iceCoverage(BlockPos pos) { return rank(field(pos.getX(),pos.getZ(),ICE_SEED,44)); }
    /** Coherent drift heights in [-1,1], independent of the coverage field. */
    public static float snowThickness(BlockPos pos) {
        return (float)(2*field(pos.getX(),pos.getZ(),SNOW_SEED+100,24)-1);
    }
    public static boolean shouldCover(BlockPos pos,float coverage,float noise) {
        return Float.isFinite(coverage)&&Float.isFinite(noise)&&noise<Math.max(0,Math.min(1,coverage));
    }
    /** Thin edges build up continuously toward a patch interior, then quantize to vanilla layers. */
    static int snowLayers(int thickness,float drift,float threshold,float noise,boolean leaf) {
        if(thickness<=0 || noise>=threshold)return 0;
        float edge=Math.max(0,Math.min(1,(threshold-noise)/.12f));
        edge=edge*edge*(3-2*edge);
        int layers=Math.max(1,Math.min(8,Math.round((1+(thickness-1)*edge)*(1+.35f*drift))));
        return leaf?1:layers;
    }
}
