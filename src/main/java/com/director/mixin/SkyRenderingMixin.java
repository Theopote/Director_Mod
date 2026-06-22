package com.director.mixin;

import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.time.TimeSample;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {

    @Inject(
        method = "updateRenderState(Lnet/minecraft/client/world/ClientWorld;FLnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/SkyRenderState;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void director$overrideSkyTime(
            ClientWorld world,
            float tickDelta,
            Vec3d pos,
            SkyRenderState state,
            CallbackInfo ci
    ) {
        WorldRenderOverride renderState = WorldRenderOverride.get();
        if (!renderState.hasTimeOverride()) {
            return;
        }
        TimeSample sample = renderState.timeSample();
        if (sample == null || sample.isUseWorldTime()) {
            return;
        }

        float skyAngle = sample.getSkyAngle();
        state.time = skyAngle;
        state.solarAngle = skyAngle * (float) (Math.PI * 2.0);

        DimensionEffects dimensionEffects = world.getDimensionEffects();
        state.sunriseAndSunsetColor = dimensionEffects.getSkyColor(state.time);
        state.isSunTransition = dimensionEffects.isSunRisingOrSetting(state.time);
    }
}
