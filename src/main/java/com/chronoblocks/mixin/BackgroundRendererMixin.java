package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController;
import com.chronoblocks.ui.timeline.playback.weather.WeatherSample;
import com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BackgroundRendererMixin v4.0
 * 
 * <p>拦截 BackgroundRenderer.getFogColor() 方法，应用季节系统和天气系统的雾效果。
 * 
 * <p>这是更稳定的注入方式，直接修改 getFogColor() 的返回值，不依赖于 WorldRenderer.render() 的局部变量。
 * 
 * <p>设计：
 * <ul>
 *   <li>季节系统：颜色 tint 是相对偏移值，使用公式：finalColor = originalColor * (1.0 + tint)</li>
 *   <li>天气系统：直接覆盖雾颜色和密度（优先级高于季节系统）</li>
 * </ul>
 * 
 * <p>例如：tint = 0.1 表示增加 10%，tint = -0.1 表示减少 10%
 * 
 * <p>注意：天空颜色在 {@link ClientWorldMixin} 中处理
 * 
 * @author ChronoBlocks Team
 * @version 4.0 - 添加天气系统支持
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    
    /**
     * 拦截雾颜色获取，应用季节系统的雾颜色偏移
     * 
     * <p>方法签名（Minecraft 1.21.4）：
     * <p>getFogColor(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness) -> Vector4f
     */
    @Inject(
        method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void chronoblocks$modifyFogColor(
            Camera camera,
            float tickDelta,
            ClientWorld world,
            int clampedViewDistance,
            float skyDarkness,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        Vector4f original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        
        float r = original.x;
        float g = original.y;
        float b = original.z;
        float a = original.w;
        
        // ⭐ 天气系统：优先级最高，直接覆盖雾颜色和密度
        TimelineWeatherController weatherController = TimelineWeatherController.getInstance();
        if (weatherController.hasOverride()) {
            WeatherSample weatherSample = weatherController.getCurrentSample();
            if (weatherSample != null && weatherSample.enabled && weatherSample.fogDensity > 0.01f) {
                // 使用天气系统的雾颜色
                r = weatherSample.fogColor.x; // Use fogColor from WeatherSample
                g = weatherSample.fogColor.y;
                b = weatherSample.fogColor.z;
                
                // 应用雾暗度
                float fogDarkness = weatherSample.fogDarkness; // Use fogDarkness from WeatherSample
                float darkFactor = Math.max(0f, 1f - fogDarkness);
                r *= darkFactor;
                g *= darkFactor;
                b *= darkFactor;
                
                // 雾密度影响 alpha（透明度）
                a = Math.max(0f, Math.min(1f, weatherSample.fogDensity));
                cir.setReturnValue(new Vector4f(r, g, b, a));
                return; // 天气系统覆盖，不再应用季节系统
            }
        }
        
        // ⭐ 季节系统：应用雾颜色偏移（如果天气系统没有覆盖）
        TimelineSeasonController seasonController = TimelineSeasonController.getInstance();
        SeasonSample seasonSample = seasonController.getCurrentSample();
        if (seasonController.hasOverride() && seasonSample != null) {
            // tint: 相对偏移（-0.99 ~ 3），限制范围避免负值或过度增强
            r = clamp01(original.x * (1f + clampTint(seasonSample.fogTint.x)));
            g = clamp01(original.y * (1f + clampTint(seasonSample.fogTint.y)));
            b = clamp01(original.z * (1f + clampTint(seasonSample.fogTint.z)));
        }
        
        cir.setReturnValue(new Vector4f(r, g, b, a));
    }
    
    /**
     * 将值限制在 [0, 1] 范围内
     */
    @Unique
    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
    
    /**
     * 限制 tint 范围，避免负值导致颜色突变或过度增强
     * <p>tint ∈ [-0.99, +3]：允许最多 -99% 到 +300% 的偏移
     */
    @Unique
    private static float clampTint(float v) {
        return Math.max(-0.99f, Math.min(3f, v));
    }
}

