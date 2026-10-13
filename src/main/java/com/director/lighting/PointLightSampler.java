package com.director.lighting;

import java.util.List;

/** Pure, thread-safe sampling of an immutable snapshot. Preserves vanilla sky light. */
public final class PointLightSampler {
    private PointLightSampler() {}
    public static double level(List<PointLight> lights, double x, double y, double z) {
        double level = 0;
        for (PointLight light : lights) {
            if (!light.enabled() || light.brightness() == 0) continue;
            double dx=x-light.x(), dy=y-light.y(), dz=z-light.z();
            double distanceSquared=dx*dx+dy*dy+dz*dz;
            if (distanceSquared >= light.range()*light.range()) continue;
            double contribution=light.brightness()*Math.pow(1-Math.sqrt(distanceSquared)/light.range(), light.falloff());
            level=Math.max(level, contribution);
        }
        return level;
    }
    public static int packed(List<PointLight> lights, int vanilla, double x, double y, double z) {
        int block=(int)Math.round(level(lights,x,y,z)*16);
        return (vanilla & 0xffff0000) | Math.max(vanilla & 0xffff, Math.min(240, block));
    }
}
