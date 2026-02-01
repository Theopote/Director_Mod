package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.time.TimeSample;
import com.chronoblocks.ui.timeline.playback.time.TimelineWorldController;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SkyRenderingMixin v1.0
 *
 * <p>确保自定义太阳高度角能够驱动天空太阳位置（renderCelestialBodies 使用 state.time）。</p>
 */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {

    @Inject(
        method = "updateRenderState(Lnet/minecraft/client/world/ClientWorld;FLnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/SkyRenderState;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void chronoblocks$overrideSkyTime(
            ClientWorld world,
            float tickDelta,
            Vec3d pos,
            SkyRenderState state,
            CallbackInfo ci
    ) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        TimeSample sample = controller.current();
        if (sample == null || sample.isUseWorldTime()) {
            return;
        }

        // 仅在自定义太阳模式下覆盖天空时间与太阳角度
        float skyAngle = sample.getSkyAngle();
        state.time = skyAngle;
        state.solarAngle = skyAngle * (float) (Math.PI * 2.0);

        DimensionEffects dimensionEffects = world.getDimensionEffects();
        state.sunriseAndSunsetColor = dimensionEffects.getSkyColor(state.time);
        state.isSunTransition = dimensionEffects.isSunRisingOrSetting(state.time);
    }
}
