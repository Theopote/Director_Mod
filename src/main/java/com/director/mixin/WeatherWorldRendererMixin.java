package com.director.mixin;
import com.director.timeline.playback.weather.WeatherParticleController;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Update weather after camera and world sampling, immediately before world rendering. */
@Mixin(WorldRenderer.class)
public abstract class WeatherWorldRendererMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void director$sampleWeatherParticles(CallbackInfo ci) {
        WeatherParticleController.getInstance().tick();
    }
}
