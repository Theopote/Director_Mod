package com.director.mixin;

import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.season.SeasonSample;
import net.minecraft.client.render.LightmapTextureManager;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * LightmapTextureManagerMixin v1.1
 *
 * <p>功能：</p>
 * <ul>
 *   <li>将季节系统的"光照颜色趋势"应用到 lightmap 颜色</li>
 *   <li>✅ v1.1：闪电闪白时提升 lightmap 亮度（MC 闪电视觉效果的核心通道）</li>
 * </ul>
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
    private Vector3f director$applyLightTintToLightColor(Vector3f original) {
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
    private Vector3f director$applyLightTintToSkyLight(Vector3f original) {
        return applySeasonLightTint(original, true, true);
    }

    @Unique
    private static Vector3f applySeasonLightTint(Vector3f original, boolean applyLightTint, boolean applySkyTint) {
        if (original == null) {
            return null;
        }

        Vector3f result = new Vector3f(original);

        // ✅ v1.1：闪电闪白 — 提升 lightmap 亮度
        // MC 的闪电视觉效果主要通过 lightmap 实现，仅修改 getSkyBrightness 不够明显
        float flash = WorldRenderOverride.get().weatherFlashBrightness();
        if (flash > 0.01f) {
            // 闪白时将光照颜色混合向白色，flash=1.0 时完全白
            result.set(
                clamp01(result.x + (1.0f - result.x) * flash),
                clamp01(result.y + (1.0f - result.y) * flash),
                clamp01(result.z + (1.0f - result.z) * flash)
            );
        }

        // 季节光照色调
        WorldRenderOverride state = WorldRenderOverride.get();
        if (state.hasSeasonOverride()) {
            SeasonSample sample = state.seasonSample();
            if (sample != null) {
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
            }
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
