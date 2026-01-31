package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController;
import com.chronoblocks.ui.timeline.playback.time.TimeSample;
import com.chronoblocks.ui.timeline.playback.time.TimelineWorldController;
import com.chronoblocks.ui.timeline.playback.weather.WeatherSample;
import com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ClientWorldMixin v4.0
 * 
 * <p>在 ChronoBlocks 导演模式下实现选择性冻结：
 * <ul>
 *   <li>暂停世界时间流逝 (tickTime) - 仅导演模式</li>
 *   <li>停止随机方块显示效果 (doRandomBlockDisplayTicks)</li>
 *   <li>应用季节系统的天空颜色偏移</li>
 * </ul>
 * 
 * <p>注意：时间轨道系统的时间冻结由服务器端 GameRule 控制，客户端不负责冻结逻辑。
 * 
 * @author ChronoBlocks Team
 * @version 4.0 - 添加天气系统支持
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    /**
     * Director 模式：冻结客户端时间
     */
    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void onTickTime(CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.isChronoBlocksModeActive()) {
            System.out.println("[ChronoBlocks][TIME] onTickTime: cancelled (chrono mode active)");
            ci.cancel();
        }
    }

    /**
     * Director 模式：不生成随机粒子 / 特效
     */
    @Inject(method = "doRandomBlockDisplayTicks", at = @At("HEAD"), cancellable = true)
    private void stopDisplayTicks(int cx, int cy, int cz, CallbackInfo ci) {
        ChronoBlocksClientController c = ChronoBlocksClientController.getInstance();
        if (c != null && c.isChronoBlocksModeActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "setTime(JJZ)V", at = @At("HEAD"), cancellable = true)
    private void blockExternalTimeUpdate(long time, long timeOfDay, boolean doDaylightCycle, CallbackInfo ci) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        boolean internal = controller.isInternalTimeUpdate();
        if (!internal) {
            System.out.println("[ChronoBlocks][TIME] blockExternalTimeUpdate: blocking external setTime -> time=" + time + " timeOfDay=" + timeOfDay);
            ci.cancel();
        } else {
            System.out.println("[ChronoBlocks][TIME] blockExternalTimeUpdate: allowed internal setTime -> time=" + time + " timeOfDay=" + timeOfDay);
        }
    }

    @Inject(method = "getSkyBrightness(F)F", at = @At("HEAD"), cancellable = true)
    private void overrideSkyBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        TimeSample sample = controller.current();
        if (sample == null) {
            return;
        }

        // ⭐ 直接使用 TimeSample.getSkyLight() 而不是通过 skyAngle 计算
        // skyLight 已经在 TimeSample 中根据太阳高度角计算好了
        float baseBrightness = sample.getSkyLight(); // 0.0-1.0，0=午夜，1=正午
        
        ClientWorld world = (ClientWorld) (Object) this;
        
        // ✅ 修复：当天气轨道启用时，禁止原版天气影响天空亮度
        // 原版天气的雨/雷暴梯度已经被 WorldMixin 覆盖，这里使用轨道系统的值
        TimelineWeatherController weatherController = TimelineWeatherController.getInstance();
        if (weatherController.hasOverride()) {
            WeatherSample weatherSample = weatherController.getCurrentSample();
            if (weatherSample != null && weatherSample.enabled) {
                // 使用轨道系统的雨/雷暴强度影响天空亮度
                float rainGradient = weatherSample.rainStrength;
                float thunderGradient = weatherSample.thunderStrength;
                baseBrightness *= 1.0f - rainGradient * 5.0f / 16.0f;
                baseBrightness *= 1.0f - thunderGradient * 5.0f / 16.0f;
            } else {
                // 天气轨道启用但 sample 无效，禁止原版天气影响
                // 不应用任何雨/雷暴暗度
            }
        } else {
            // 天气轨道未启用，使用原版天气
            baseBrightness *= 1.0f - world.getRainGradient(tickDelta) * 5.0f / 16.0f;
            baseBrightness *= 1.0f - world.getThunderGradient(tickDelta) * 5.0f / 16.0f;
        }
        baseBrightness = baseBrightness * 0.8f + 0.2f;
        
        // ⭐ 天气系统：应用云量影响天空亮度（云越多，天空越暗）
        if (weatherController.hasOverride()) {
            WeatherSample weatherSample = weatherController.getCurrentSample();
            if (weatherSample != null && weatherSample.enabled) {
                // 云量影响：云越多，天空越暗（最多降低50%亮度）
                if (weatherSample.cloudCoverage > 0.01f) {
                    float cloudDarkness = weatherSample.cloudCoverage * 0.5f;
                    baseBrightness *= (1.0f - cloudDarkness);
                }
                
                // ⭐ v1.1 架构：闪电闪白效果（通过 WeatherEventController 控制）
                // ✅ 修复：使用乘数而不是直接加法，让闪白更明显
                float flashFactor = com.chronoblocks.ui.timeline.playback.weather.WeatherEventController.getInstance().getCurrentFlashBrightness();
                if (flashFactor > 0.01f) {
                    // 闪白时：brightness = base * (1.0 - stormDark) + flashFactor（瞬时增益）
                    // 使用混合方式：mix(skyColor, WHITE, flashFactor)
                    baseBrightness = baseBrightness * (1.0f - flashFactor * 0.3f) + flashFactor * 0.7f;
                    baseBrightness = MathHelper.clamp(baseBrightness, 0f, 1f);
                }
            }
        }

        cir.setReturnValue(baseBrightness);
    }

    @Inject(method = "getStarBrightness(F)F", at = @At("HEAD"), cancellable = true)
    private void overrideStarBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        TimeSample sample = controller.current();
        if (sample == null) {
            return;
        }

        // ⭐ 直接使用 TimeSample.getSkyLight() 计算星星亮度
        // 星星亮度与天空亮度反相关：亮度越低（夜晚），星星越亮
        float skyLight = sample.getSkyLight(); // 0.0-1.0
        float starBrightness = 1.0f - skyLight;
        starBrightness = starBrightness * starBrightness * 0.5f; // 二次函数增强对比
        
        cir.setReturnValue(starBrightness);
    }

    // NOTE: Removed direct injectors into `getSkyAngle`/`getSkyAngleRadians`.
    // These methods may not exist under some mappings and can cause Mixin
    // InvalidInjectionException. We rely on `WorldMixin.getTimeOfDay()` and
    // renderer-side overrides for sky angle/rotation instead.

    /**
     * Weather + Time：云颜色控制
     * 
     * <p>方法签名（Minecraft 1.21）：getCloudsColor(float tickProgress) -> int (ARGB)
     * 
     * <p>根据提供的 ClientWorld 源码，该方法返回 int (ARGB 格式)，而不是 Vec3d
     */
    @Inject(
        method = "getCloudsColor(F)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private void tintClouds(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        int originalColor = cir.getReturnValue();

        // 从 ARGB int 提取 RGB 分量
        int r = (originalColor >> 16) & 0xFF;
        int g = (originalColor >> 8) & 0xFF;
        int b = originalColor & 0xFF;
        int a = (originalColor >> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF; // 确保 alpha 通道有效
        }

        // 转换为 float [0-1]
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        // ⭐ 时间系统：首先应用基于 skyLight 的亮度调整
        // 这确保了云会随时间变亮或变暗，就像Minecraft原生一样
        TimelineWorldController timeController = TimelineWorldController.getInstance();
        if (timeController.hasOverride()) {
            TimeSample timeSample = timeController.current();
            if (timeSample != null) {
                // 根据 skyLight 调整云的亮度
                // skyLight: 0.0 = 午夜（很暗）, 1.0 = 正午（最亮）
                float skyLight = timeSample.getSkyLight();
                
                // 使用非线性曲线：白天保持相对不变，夜晚明显变暗
                // 这与Minecraft的daylight cycle一致
                float cloudBrightness = skyLight * skyLight * 0.8f + 0.2f;
                rf *= cloudBrightness;
                gf *= cloudBrightness;
                bf *= cloudBrightness;
                
                // 夜晚给云添加蓝调，而不是完全变黑
                if (skyLight < 0.3f) {
                    bf += (0.3f - skyLight) * 0.15f;
                }
            }
        }

        // ⭐ 天气系统：应用在时间系统之后
        TimelineWeatherController weatherController = TimelineWeatherController.getInstance();
        if (weatherController.hasOverride()) {
            WeatherSample weatherSample = weatherController.getCurrentSample();
            if (weatherSample != null && weatherSample.enabled) {
                float coverage = clamp01(weatherSample.cloudCoverage);
                if (coverage > 0.01f) {
                    // 应用云亮度（1.0 = Minecraft 默认）
                    float cloudBrightness = MathHelper.clamp(weatherSample.cloudBrightness, 0.3f, 1.2f);
                    rf *= cloudBrightness;
                    gf *= cloudBrightness;
                    bf *= cloudBrightness;
                    
                    // 雨天使云变暗
                    if (weatherSample.rainStrength > 0.01f) {
                        float rainDarkness = weatherSample.rainStrength * 0.3f;
                        rf *= (1.0f - rainDarkness);
                        gf *= (1.0f - rainDarkness);
                        bf *= (1.0f - rainDarkness);
                    }
                }
            }
        }

        // 限制范围并转换回 ARGB int
        rf = clamp01(rf);
        gf = clamp01(gf);
        bf = clamp01(bf);

        int newColor = (a << 24)
                | ((int) (rf * 255) << 16)
                | ((int) (gf * 255) << 8)
                | (int) (bf * 255);

        cir.setReturnValue(newColor);
    }

    /**
     * Season + Weather：天空颜色和暗度
     * 
     * <p>优先级：Weather Track > Season Track > Minecraft 原版
     */

    @Inject(
        method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private void tintSky(Vec3d pos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        int color = cir.getReturnValue();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // 确保 Alpha 通道为 0xFF（天空颜色应该完全不透明）
        int a = (color >> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF;
        }
        
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;
        
        // ⭐ 时间系统：首先应用基于 skyLight 的亮度调整（优先级高于天气/季节）
        TimelineWorldController timeController = TimelineWorldController.getInstance();
        if (timeController.hasOverride()) {
            TimeSample timeSample = timeController.current();
            if (timeSample != null) {
                // 根据 skyLight 调整天空颜色
                // skyLight: 0.0 = 午夜（很暗）, 0.5 = 傍晚/早晨（中等）, 1.0 = 正午（最亮）
                float skyLight = timeSample.getSkyLight();
                
                // 应用非线性的亮度曲线以匹配 Minecraft 的视觉感受
                // 夜晚应该很暗，白天应该很亮
                float lightFactor = skyLight * skyLight; // 二次函数使过渡更平滑
                rf *= lightFactor;
                gf *= lightFactor;
                bf *= lightFactor;
                
                // 夜晚添加蓝调（真实的夜空是蓝色的，不是完全黑色）
                if (skyLight < 0.3f) {
                    // 夜晚：添加一点蓝色调
                    bf += (0.3f - skyLight) * 0.2f; // 最多加 20% 蓝色
                }
                
                // 日出/日落时添加橙黄色调
                if (skyLight > 0.2f && skyLight < 0.4f) {
                    // 早晨（0.2-0.4）：添加红/橙色调
                    float sunriseFactor = (0.4f - skyLight) / 0.2f; // 0 到 1
                    rf += sunriseFactor * 0.3f; // 最多加 30% 红色
                    gf += sunriseFactor * 0.15f; // 最多加 15% 绿色
                } else if (skyLight > 0.6f && skyLight < 0.8f) {
                    // 傍晚（0.6-0.8）：添加红/橙色调
                    float sunsetFactor = (0.8f - skyLight) / 0.2f; // 0 到 1
                    rf += sunsetFactor * 0.3f; // 最多加 30% 红色
                    gf += sunsetFactor * 0.15f; // 最多加 15% 绿色
                }
            }
        }
        
        // ⭐ 天气系统：优先级最高，应用天空暗度和雷暴暗度
        TimelineWeatherController weatherController = TimelineWeatherController.getInstance();
        if (weatherController.hasOverride()) {
            WeatherSample weatherSample = weatherController.getCurrentSample();
            if (weatherSample != null && weatherSample.enabled) {
                // 应用天空暗度
                float dark = weatherSample.skyDarkness; // Use skyDarkness from WeatherSample
                float factor = Math.max(0f, 1f - dark);
                rf *= factor;
                gf *= factor;
                bf *= factor;
                
                // 雷暴会进一步降低亮度
                if (weatherSample.thunderIntensity > 0.01f) {
                    float thunderFactor = 1f - (weatherSample.thunderIntensity * 0.6f);
                    rf *= thunderFactor;
                    gf *= thunderFactor;
                    bf *= thunderFactor;
                }
                
                // 天气系统覆盖，不再应用季节系统
                cir.setReturnValue((0xFF << 24)
                        | ((int) (clamp01(rf) * 255) << 16)
                        | ((int) (clamp01(gf) * 255) << 8)
                        | (int) (clamp01(bf) * 255));
                return;
            }
        }
        
        // ⭐ 季节系统：应用天空 tint 偏移（如果天气系统没有覆盖）
        TimelineSeasonController seasonController = TimelineSeasonController.getInstance();
        if (seasonController.hasOverride()) {
            SeasonSample seasonSample = seasonController.getCurrentSample();
            if (seasonSample != null) {
                // 应用 tint 偏移：finalColor = originalColor * (1.0 + tint)
                rf = clamp01(rf * (1f + clampTint(seasonSample.skyTint.x)));
                gf = clamp01(gf * (1f + clampTint(seasonSample.skyTint.y)));
                bf = clamp01(bf * (1f + clampTint(seasonSample.skyTint.z)));
            }
        }
        
        // 重新打包为 ARGB 格式，确保 alpha = 0xFF
        cir.setReturnValue((0xFF << 24)
                | ((int) (clamp01(rf) * 255) << 16)
                | ((int) (clamp01(gf) * 255) << 8)
                | (int) (clamp01(bf) * 255));
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
    
    // 注意：天气相关的方法（getRainGradient, getThunderGradient, isRaining, isThundering）
    // 已移至 WorldMixin，因为这些方法在 World 基类中定义，而不是 ClientWorld 中。
}
