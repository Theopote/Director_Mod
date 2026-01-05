package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController;
import com.chronoblocks.ui.timeline.playback.weather.WeatherSample;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

    @ModifyVariable(
        method = "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLnet/minecraft/util/math/Vec3d;F)V",
        at = @At("HEAD"),
        index = 5,
        argsOnly = true
    )
    private float chronoblocks$scaleCloudPhase(float cloudPhase) {
        TimelineWeatherController controller = TimelineWeatherController.getInstance();
        if (!controller.hasOverride()) {
            return cloudPhase;
        }
        WeatherSample sample = controller.getCurrentSample();
        if (sample == null || !sample.enabled) {
            return cloudPhase;
        }

        float speed = MathHelper.clamp(sample.cloudSpeed, 0f, 4f);
        float windBoost = MathHelper.clamp(sample.windStrength, 0f, 1f) * 0.6f;
        speed = MathHelper.clamp(speed * (1f + windBoost), 0f, 4f);
        return cloudPhase * speed;
    }
}
