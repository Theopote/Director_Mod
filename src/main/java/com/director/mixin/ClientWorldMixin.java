package com.director.mixin;

import com.director.client.DirectorClientController;
import com.director.client.world.ClientWorldSkyEffects;
import com.director.client.world.ClientWorldTintEffects;
import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.time.TimeSample;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ClientWorldMixin v4.2 — 只读 {@link WorldRenderOverride}，颜色计算委托 client.world 工具类。
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

    @Inject(method = "setRainGradient(F)V", at = @At("HEAD"), cancellable = true)
    private void blockExternalRainGradientUpdate(float gradient, CallbackInfo ci) {
        DirectorClientController controller = DirectorClientController.getInstance();
        if (controller == null || !controller.isDirectorModeActive()) {
            return;
        }
        WorldRenderOverride state = WorldRenderOverride.get();
        if (state.hasWeatherOverride()) {
            return;
        }
        if (gradient > 0.0f) {
            ci.cancel();
        }
    }

    @Inject(method = "setThunderGradient(F)V", at = @At("HEAD"), cancellable = true)
    private void blockExternalThunderGradientUpdate(float gradient, CallbackInfo ci) {
        DirectorClientController controller = DirectorClientController.getInstance();
        if (controller == null || !controller.isDirectorModeActive()) {
            return;
        }
        WorldRenderOverride state = WorldRenderOverride.get();
        if (state.hasWeatherOverride()) {
            return;
        }
        if (gradient > 0.0f) {
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
        cir.setReturnValue(ClientWorldSkyEffects.computeSkyBrightness(
                sample,
                state.weatherSample(),
                state.hasWeatherOverride(),
                state.weatherFlashBrightness(),
                world,
                tickDelta
        ));
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

    @Inject(method = "getCloudsColor(F)I", at = @At("RETURN"), cancellable = true)
    private void tintClouds(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        WorldRenderOverride state = WorldRenderOverride.get();
        cir.setReturnValue(ClientWorldTintEffects.tintCloudsColor(
                cir.getReturnValue(),
                state.weatherSample(),
                state.hasWeatherOverride(),
                state.seasonSample(),
                state.hasSeasonOverride()
        ));
    }

    @Inject(method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)I", at = @At("RETURN"), cancellable = true)
    private void tintSky(Vec3d pos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        WorldRenderOverride state = WorldRenderOverride.get();
        cir.setReturnValue(ClientWorldTintEffects.tintSkyColor(
                cir.getReturnValue(),
                state.weatherSample(),
                state.hasWeatherOverride(),
                state.seasonSample(),
                state.hasSeasonOverride()
        ));
    }
}
