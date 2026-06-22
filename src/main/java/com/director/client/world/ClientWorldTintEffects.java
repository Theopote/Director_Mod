package com.director.client.world;

import com.director.timeline.playback.season.SeasonSample;
import com.director.timeline.playback.weather.WeatherSample;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 世界渲染颜色 tint（天空/云/雾/生物群系），从各 ClientWorld 相关 Mixin 抽离。
 */
public final class ClientWorldTintEffects {

    private ClientWorldTintEffects() {
    }

    public static int tintCloudsColor(
            int originalArgb,
            WeatherSample weatherSample,
            boolean weatherOverrideActive,
            SeasonSample seasonSample,
            boolean seasonOverrideActive
    ) {
        int a = (originalArgb >> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF;
        }

        float rf = ((originalArgb >> 16) & 0xFF) / 255f;
        float gf = ((originalArgb >> 8) & 0xFF) / 255f;
        float bf = (originalArgb & 0xFF) / 255f;

        if (weatherOverrideActive && weatherSample != null && weatherSample.enabled) {
            float coverage = clamp01(weatherSample.cloudCoverage);
            if (coverage > 0.01f) {
                float cloudBrightness = MathHelper.clamp(weatherSample.cloudBrightness, 0.3f, 1.2f);
                rf *= cloudBrightness;
                gf *= cloudBrightness;
                bf *= cloudBrightness;

                if (weatherSample.rainStrength > 0.01f) {
                    float rainDarkness = weatherSample.rainStrength * 0.3f;
                    rf *= (1.0f - rainDarkness);
                    gf *= (1.0f - rainDarkness);
                    bf *= (1.0f - rainDarkness);
                }
            }
        }

        if (seasonOverrideActive && seasonSample != null) {
            rf = clamp01(rf * (1f + clampTint(seasonSample.lightTint.x)));
            gf = clamp01(gf * (1f + clampTint(seasonSample.lightTint.y)));
            bf = clamp01(bf * (1f + clampTint(seasonSample.lightTint.z)));
        }

        return packArgb(a, rf, gf, bf);
    }

    public static int tintSkyColor(
            int originalRgb,
            WeatherSample weatherSample,
            boolean weatherOverrideActive,
            SeasonSample seasonSample,
            boolean seasonOverrideActive
    ) {
        float rf = ((originalRgb >> 16) & 0xFF) / 255f;
        float gf = ((originalRgb >> 8) & 0xFF) / 255f;
        float bf = (originalRgb & 0xFF) / 255f;

        if (weatherOverrideActive && weatherSample != null && weatherSample.enabled) {
            float factor = Math.max(0f, 1f - weatherSample.skyDarkness);
            rf *= factor;
            gf *= factor;
            bf *= factor;

            if (weatherSample.thunderIntensity > 0.01f) {
                float thunderFactor = 1f - (weatherSample.thunderIntensity * 0.6f);
                rf *= thunderFactor;
                gf *= thunderFactor;
                bf *= thunderFactor;
            }
        }

        if (seasonOverrideActive && seasonSample != null) {
            rf = clamp01(rf * (1f + clampTint(seasonSample.skyTint.x)));
            gf = clamp01(gf * (1f + clampTint(seasonSample.skyTint.y)));
            bf = clamp01(bf * (1f + clampTint(seasonSample.skyTint.z)));

            rf = applyAtmosphericFogTint(rf, seasonSample.fogTint.x);
            gf = applyAtmosphericFogTint(gf, seasonSample.fogTint.y);
            bf = applyAtmosphericFogTint(bf, seasonSample.fogTint.z);
        }

        return packArgb(0xFF, rf, gf, bf);
    }

    /**
     * 季节雾色 tint；无覆盖时返回 {@code null}（调用方保留原版返回值）。
     */
    public static Vector4f tintFogColor(
            Vector4f original,
            SeasonSample seasonSample,
            boolean seasonOverrideActive,
            float skyR,
            float skyG,
            float skyB
    ) {
        if (original == null || !seasonOverrideActive || seasonSample == null) {
            return null;
        }

        float r = clamp01(original.x * (1f + clampTint(seasonSample.fogTint.x)));
        float g = clamp01(original.y * (1f + clampTint(seasonSample.fogTint.y)));
        float b = clamp01(original.z * (1f + clampTint(seasonSample.fogTint.z)));

        float blendToSky = fogSkyBlendAmount(seasonSample);
        r = lerp(r, skyR, blendToSky);
        g = lerp(g, skyG, blendToSky);
        b = lerp(b, skyB, blendToSky);

        return new Vector4f(r, g, b, original.w);
    }

    /**
     * 将打包的 RGB/ARGB 颜色解为 [0,1] 分量（用于雾色与天空混合）。
     */
    public static float[] unpackRgb01(int packedColor) {
        return new float[] {
                ((packedColor >> 16) & 0xFF) / 255f,
                ((packedColor >> 8) & 0xFF) / 255f,
                (packedColor & 0xFF) / 255f
        };
    }

    /**
     * 生物群系草地/树叶颜色 season tint。
     */
    public static int tintBiomeColor(int color, Vector3f tint) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int a = (color >> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF;
        }

        float rf = clamp01((r / 255f) * (1f + clampTint(tint.x)));
        float gf = clamp01((g / 255f) * (1f + clampTint(tint.y)));
        float bf = clamp01((b / 255f) * (1f + clampTint(tint.z)));

        return packArgb(a, rf, gf, bf);
    }

    private static float fogSkyBlendAmount(SeasonSample sample) {
        float strength = Math.max(
                Math.max(Math.abs(sample.fogTint.x), Math.abs(sample.fogTint.y)),
                Math.abs(sample.fogTint.z));
        return Math.min(0.35f, 0.12f + strength * 0.18f);
    }

    private static int packArgb(int alpha, float r, float g, float b) {
        return (alpha << 24)
                | ((int) (clamp01(r) * 255) << 16)
                | ((int) (clamp01(g) * 255) << 8)
                | (int) (clamp01(b) * 255);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float clampTint(float v) {
        return Math.max(-0.99f, Math.min(3f, v));
    }

    private static float applyAtmosphericFogTint(float base, float fogTint) {
        float tint = clampTint(fogTint);
        if (Math.abs(tint) < 0.0001f) {
            return base;
        }
        float fogged = clamp01(base * (1f + tint));
        return clamp01(lerp(base, fogged, 0.55f));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
