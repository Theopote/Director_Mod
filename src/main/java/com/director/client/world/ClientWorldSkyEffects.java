package com.director.client.world;

import com.director.timeline.playback.time.TimeSample;
import com.director.timeline.playback.weather.WeatherSample;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/**
 * 天空亮度等计算（从 Mixin 抽离，便于单测与维护）。
 */
public final class ClientWorldSkyEffects {

    private ClientWorldSkyEffects() {
    }

    public static float computeSkyBrightness(
            TimeSample timeSample,
            WeatherSample weatherSample,
            boolean weatherOverrideActive,
            float weatherFlashBrightness,
            ClientWorld world,
            float tickDelta
    ) {
        float f = timeSample.getSkyAngle();
        float g = 1.0f - (MathHelper.cos(f * (float) (Math.PI * 2)) * 2.0f + 0.2f);
        g = MathHelper.clamp(g, 0.0f, 1.0f);
        g = 1.0f - g;
        float baseBrightness = g;

        if (weatherOverrideActive && weatherSample != null && weatherSample.enabled) {
            float rainGradient = weatherSample.rainStrength;
            float thunderGradient = weatherSample.thunderStrength;
            baseBrightness *= 1.0f - rainGradient * 5.0f / 16.0f;
            baseBrightness *= 1.0f - thunderGradient * 5.0f / 16.0f;
        } else {
            baseBrightness *= 1.0f - world.getRainGradient(tickDelta) * 5.0f / 16.0f;
            baseBrightness *= 1.0f - world.getThunderGradient(tickDelta) * 5.0f / 16.0f;
        }
        baseBrightness = baseBrightness * 0.8f + 0.2f;

        if (weatherOverrideActive && weatherSample != null && weatherSample.enabled) {
            if (weatherSample.cloudCoverage > 0.01f) {
                float cloudDarkness = weatherSample.cloudCoverage * 0.5f;
                baseBrightness *= (1.0f - cloudDarkness);
            }

            if (weatherFlashBrightness > 0.01f) {
                baseBrightness = baseBrightness * (1.0f - weatherFlashBrightness * 0.3f)
                        + weatherFlashBrightness * 0.7f;
                baseBrightness = MathHelper.clamp(baseBrightness, 0f, 1f);
            }
        }

        return baseBrightness;
    }

    public static float computeStarBrightness(TimeSample timeSample) {
        float skyLight = timeSample.getSkyLight();
        float starBrightness = 1.0f - skyLight;
        return starBrightness * starBrightness * 0.5f;
    }
}
