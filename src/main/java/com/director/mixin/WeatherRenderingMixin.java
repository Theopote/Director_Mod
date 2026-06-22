package com.director.mixin;

import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.weather.WeatherSample;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WeatherRenderingMixin
 * <p>
 * 拦截原版 {@code WeatherRendering.getPrecipitationAt()} 方法，
 * 使天气轨道的降水效果不受生物群系限制。
 * <p>
 * 原版行为：每列方块根据生物群系返回 NONE / RAIN / SNOW，
 * 导致沙漠、恶地等生物群系中无法渲染雨线。
 * <p>
 * 修改后：当天气轨道有降水覆盖时，强制返回对应降水类型，
 * 确保导演模式下雨/雪在所有生物群系中可见。
 */
@Mixin(WeatherRendering.class)
public abstract class WeatherRenderingMixin {

    /**
     * 拦截 {@code getPrecipitationAt} 的返回值。
     * <p>
     * 当天气轨道设定了下雨/下雪时，忽略生物群系类型，
     * 强制返回 RAIN 或 SNOW，确保全屏降水。
     */
    @Inject(method = "getPrecipitationAt", at = @At("RETURN"), cancellable = true)
    private void overridePrecipitationForWeatherTrack(
            World world, BlockPos pos,
            CallbackInfoReturnable<Biome.Precipitation> cir) {

        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasWeatherOverride()) {
            return;
        }

        WeatherSample sample = state.weatherSample();
        if (sample == null || !sample.enabled) {
            return;
        }

        // 天气轨道有雨 → 强制 RAIN（覆盖 NONE / SNOW）
        if (sample.rainIntensity > 0.01f) {
            cir.setReturnValue(Biome.Precipitation.RAIN);
            return;
        }

        // 天气轨道只有雪（无雨）→ 强制 SNOW（覆盖 NONE）
        // 注意：雪粒子由 WeatherParticleController 自定义处理，
        // 这里设 SNOW 是为了让原版渲染雪纹理条
        if (sample.snowIntensity > 0.01f) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
            return;
        }

        // 天气轨道设定晴天 → 强制 NONE（禁止原版随机降水）
        cir.setReturnValue(Biome.Precipitation.NONE);
    }
}
