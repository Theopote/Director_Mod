package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController;
import net.minecraft.client.render.LightmapTextureManager;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * LightmapTextureManagerMixin v1.0
 *
 * <p>将季节系统的“光照颜色趋势”应用到 lightmap 颜色，确保全局光照可见变化。</p>
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

    /**
     * vector3f: light color in lightmap UBO.
     */
    @ModifyVariable(
        method = "update(F)V",
        at = @At("STORE"),
        ordinal = 0,
        require = 0
    )
    private Vector3f chronoblocks$applyLightTintToLightColor(Vector3f original) {
        return applySeasonLightTint(original, true, false);
    }

    /**
     * vector3f2: sky light color in lightmap UBO.
     */
    @ModifyVariable(
        method = "update(F)V",
        at = @At("STORE"),
        ordinal = 1,
        require = 0
    )
    private Vector3f chronoblocks$applyLightTintToSkyLight(Vector3f original) {
        return applySeasonLightTint(original, true, true);
    }

    @Unique
    private static Vector3f applySeasonLightTint(Vector3f original, boolean applyLightTint, boolean applySkyTint) {
        if (original == null) {
            return null;
        }
        TimelineSeasonController controller = TimelineSeasonController.getInstance();
        if (!controller.hasOverride()) {
            return original;
        }
        SeasonSample sample = controller.getCurrentSample();
        if (sample == null) {
            return original;
        }

        Vector3f result = new Vector3f(original);
        if (applyLightTint) {
            result.set(
                clamp01(result.x * (1f + clampTint(sample.lightTint.x))),
                clamp01(result.y * (1f + clampTint(sample.lightTint.y))),
                clamp01(result.z * (1f + clampTint(sample.lightTint.z)))
            );
        }
        if (applySkyTint) {
            result.set(
                clamp01(result.x * (1f + clampTint(sample.skyTint.x))),
                clamp01(result.y * (1f + clampTint(sample.skyTint.y))),
                clamp01(result.z * (1f + clampTint(sample.skyTint.z)))
            );
        }
        return result;
    }

    @Unique
    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Unique
    private static float clampTint(float v) {
        return Math.max(-0.99f, Math.min(3f, v));
    }
}
