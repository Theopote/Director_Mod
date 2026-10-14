package com.director.mixin;
import com.director.timeline.playback.weather.WeatherParticleSeed;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Particle.class)
public abstract class WeatherParticleMixin {
    @Shadow @Final protected Random random;
    @Inject(method= "<init>*",at=@At("RETURN"))
    private void director$seedOwnedWeather(CallbackInfo ci) {
        Long seed=WeatherParticleSeed.current(); if(seed!=null)random.setSeed(seed);
    }
}
