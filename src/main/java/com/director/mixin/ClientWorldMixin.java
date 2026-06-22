package com.director.mixin;

import com.director.client.DirectorClientController;
import com.director.client.world.ClientWorldSkyEffects;
import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.season.SeasonSample;
import com.director.timeline.playback.time.TimeSample;
import com.director.timeline.playback.weather.WeatherSample;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ClientWorldMixin v4.1
 *
 * <p>渲染覆盖状态只读 {@link WorldRenderOverride}，不依赖 playback Controller 单例。
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void onTickTime(CallbackInfo ci) {
        DirectorClientController controller = DirectorClientController.getInstance();
        if (controller != null && controller.isDirectorModeActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "doRandomBlockDisplayTicks", at = @At("HEAD"), cancellable = true)
    private void stopDisplayTicks(int cx, int cy, int cz, CallbackInfo ci) {
        DirectorClientController c = DirectorClientController.getInstance();
        if (c != null && c.isDirectorModeActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "setTime(JJZ)V", at = @At("HEAD"), cancellable = true)
    private void blockExternalTimeUpdate(long time, long timeOfDay, boolean doDaylightCycle, CallbackInfo ci) {
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasTimeOverride()) {
            return;
        }
        if (!state.isInternalTimeUpdate()) {
            ci.cancel();
        }
    }

    @Inject(method = "getSkyBrightness(F)F", at = @At("HEAD"), cancellable = true)
    private void overrideSkyBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasTimeOverride()) {
            return;
        }
        TimeSample sample = state.timeSample();
        if (sample == null || sample.isUseWorldTime()) {
            return;
        }

        ClientWorld world = (ClientWorld) (Object) this;
        WeatherSample weatherSample = state.weatherSample();
        float brightness = ClientWorldSkyEffects.computeSkyBrightness(
                sample,
                weatherSample,
                state.hasWeatherOverride(),
                state.weatherFlashBrightness(),
                world,
                tickDelta
        );
        cir.setReturnValue(brightness);
    }

    @Inject(method = "getStarBrightness(F)F", at = @At("HEAD"), cancellable = true)
    private void overrideStarBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasTimeOverride()) {
            return;
        }
        TimeSample sample = state.timeSample();
        if (sample == null || sample.isUseWorldTime()) {
            return;
        }
        cir.setReturnValue(ClientWorldSkyEffects.computeStarBrightness(sample));
    }

    @Inject(
        method = "getCloudsColor(F)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private void tintClouds(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        int originalColor = cir.getReturnValue();
        int r = (originalColor >> 16) & 0xFF;
        int g = (originalColor >> 8) & 0xFF;
        int b = originalColor & 0xFF;
        int a = (originalColor >> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF;
        }

        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        WorldRenderOverride state = WorldRenderOverride.get();
        if (state.hasWeatherOverride()) {
            WeatherSample weatherSample = state.weatherSample();
            if (weatherSample != null && weatherSample.enabled) {
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
        }

        if (state.hasSeasonOverride()) {
            SeasonSample seasonSample = state.seasonSample();
            if (seasonSample != null) {
                rf = clamp01(rf * (1f + clampTint(seasonSample.lightTint.x)));
                gf = clamp01(gf * (1f + clampTint(seasonSample.lightTint.y)));
                bf = clamp01(bf * (1f + clampTint(seasonSample.lightTint.z)));
            }
        }

        rf = clamp01(rf);
        gf = clamp01(gf);
        bf = clamp01(bf);

        cir.setReturnValue((a << 24)
                | ((int) (rf * 255) << 16)
                | ((int) (gf * 255) << 8)
                | (int) (bf * 255));
    }

    @Inject(
        method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private void tintSky(Vec3d pos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        int color = cir.getReturnValue();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        WorldRenderOverride state = WorldRenderOverride.get();
        if (state.hasWeatherOverride()) {
            WeatherSample weatherSample = state.weatherSample();
            if (weatherSample != null && weatherSample.enabled) {
                float dark = weatherSample.skyDarkness;
                float factor = Math.max(0f, 1f - dark);
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
        }

        if (state.hasSeasonOverride()) {
            SeasonSample seasonSample = state.seasonSample();
            if (seasonSample != null) {
                rf = clamp01(rf * (1f + clampTint(seasonSample.skyTint.x)));
                gf = clamp01(gf * (1f + clampTint(seasonSample.skyTint.y)));
                bf = clamp01(bf * (1f + clampTint(seasonSample.skyTint.z)));

                rf = applyAtmosphericFogTint(rf, seasonSample.fogTint.x);
                gf = applyAtmosphericFogTint(gf, seasonSample.fogTint.y);
                bf = applyAtmosphericFogTint(bf, seasonSample.fogTint.z);
            }
        }

        cir.setReturnValue((0xFF << 24)
                | ((int) (clamp01(rf) * 255) << 16)
                | ((int) (clamp01(gf) * 255) << 8)
                | (int) (clamp01(bf) * 255));
    }

    @Unique
    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Unique
    private static float clampTint(float v) {
        return Math.max(-0.99f, Math.min(3f, v));
    }

    @Unique
    private static float applyAtmosphericFogTint(float base, float fogTint) {
        float tint = clampTint(fogTint);
        if (Math.abs(tint) < 0.0001f) {
            return base;
        }
        float fogged = clamp01(base * (1f + tint));
        return clamp01(lerp(base, fogged, 0.55f));
    }

    @Unique
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
