package com.director.mixin.accessor;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Particle.class)
public interface WeatherParticleAccessor {
    @Accessor("lastX") void director$lastX(double value);
    @Accessor("lastY") void director$lastY(double value);
    @Accessor("lastZ") void director$lastZ(double value);
    @Accessor("age") void director$age(int value);
}
