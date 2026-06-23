package com.director.mixin;

import com.director.client.DirectorClientController;
import com.director.client.RenderTimeState;
import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.time.TimeSample;
import com.director.timeline.playback.weather.WeatherSample;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WorldMixin v1.1 — 天气/时间覆盖只读 {@link WorldRenderOverride}。
 */
@Mixin(World.class)
public abstract class WorldMixin {

    private static boolean director$shouldSuppressVanillaWeather(WorldRenderOverride state) {
        if (state.hasWeatherTrackManaged()) {
            return true;
        }
        DirectorClientController controller = DirectorClientController.getInstance();
        return controller != null && controller.isDirectorModeActive();
    }

    @Inject(method = "getTimeOfDay()J", at = @At("HEAD"), cancellable = true)
    private void overrideTimeOfDay(CallbackInfoReturnable<Long> cir) {
        if (!((Object) this instanceof ClientWorld)) {
            return;
        }
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasTimeOverride()) {
            return;
        }
        TimeSample sample = state.timeSample();
        if (sample == null) {
            return;
        }

        long tod = sample.getWorldTimeTicks() % 24000L;
        if (tod < 0) {
            tod += 24000L;
        }
        cir.setReturnValue(tod);
    }

    @Inject(method = "getSkyAngleRadians(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private void overrideSkyAngleRadians(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientWorld)) {
            return;
        }
        float overridden = RenderTimeState.getSkyAngleRadians();
        if (!Float.isNaN(overridden)) {
            cir.setReturnValue(overridden);
            return;
        }

        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasTimeOverride()) {
            return;
        }
        TimeSample sample = state.timeSample();
        if (sample == null) {
            return;
        }
        cir.setReturnValue(sample.getSkyAngle() * (float) (Math.PI * 2.0));
    }

    @Inject(method = "setRainGradient(F)V", at = @At("HEAD"), cancellable = true)
    private void blockExternalRainGradientUpdate(float gradient, CallbackInfo ci) {
        if (!((Object) this instanceof ClientWorld)) {
            return;
        }
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
        if (!((Object) this instanceof ClientWorld)) {
            return;
        }
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

    @Inject(method = "getRainGradient(F)F", at = @At("RETURN"), cancellable = true)
    private void overrideRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientWorld)) {
            return;
        }
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!director$shouldSuppressVanillaWeather(state)) {
            return;
        }

        WeatherSample sample = state.weatherSample();
        if (sample == null || !sample.enabled) {
            cir.setReturnValue(0.0f);
            return;
        }

        if (sample.rainIntensity > 0.01f) {
            cir.setReturnValue(sample.rainIntensity);
        } else {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(method = "getThunderGradient(F)F", at = @At("RETURN"), cancellable = true)
    private void overrideThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientWorld)) {
            return;
        }
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!director$shouldSuppressVanillaWeather(state)) {
            return;
        }

        WeatherSample sample = state.weatherSample();
        if (sample == null || !sample.enabled) {
            cir.setReturnValue(0.0f);
            return;
        }

        if (sample.thunderIntensity > 0.01f) {
            cir.setReturnValue(sample.thunderIntensity);
        } else {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(method = "isRaining()Z", at = @At("RETURN"), cancellable = true)
    private void overrideIsRaining(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ClientWorld)) {
            return;
        }
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!director$shouldSuppressVanillaWeather(state)) {
            return;
        }

        WeatherSample sample = state.weatherSample();
        if (sample == null || !sample.enabled) {
            cir.setReturnValue(false);
            return;
        }

        if (sample.rainIntensity > 0.01f) {
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isThundering()Z", at = @At("RETURN"), cancellable = true)
    private void overrideIsThundering(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ClientWorld)) {
            return;
        }
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!director$shouldSuppressVanillaWeather(state)) {
            return;
        }

        WeatherSample sample = state.weatherSample();
        if (sample == null || !sample.enabled) {
            cir.setReturnValue(false);
            return;
        }

        if (sample.thunderStrength > 0.01f) {
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }
}
