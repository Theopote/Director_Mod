package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.time.TimeSample;
import com.chronoblocks.ui.timeline.playback.time.TimelineWorldController;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
// ⚠️ 以下导入在找到正确的方法名并启用 Mixin 时保留
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * WorldTimeMixin v1.1
 * 
 * <p>使用"单一真相源"理念，通过拦截 World.getTimeOfDay()
 * 来控制世界时间，从而间接影响天空角度计算。
 * 
 * <p>职责：
 * <ul>
 *   <li>拦截 World.getTimeOfDay() 方法，当使用自定义太阳时返回基于太阳角度的等效时间</li>
 *   <li>如果 TimelineWorldController 有活跃的 TimeSample 且使用自定义太阳模式，则计算等效时间</li>
 *   <li>否则使用原版逻辑</li>
 * </ul>
 * 
 * <p>优势：getTimeOfDay() 方法在所有 Minecraft 版本中都存在，比直接拦截天空角度方法更可靠。
 * 
 * @author ChronoBlocks Team
 * @version 1.1
 */
@Mixin(World.class)
public abstract class WorldTimeMixin {

    /**
     * 覆盖世界时间获取（参考 Flashback 的做法）
     * 
     * <p>通过拦截 getTimeOfDay() 来控制时间，这样所有依赖时间的地方
     * （包括天空角度计算）都会自动使用我们提供的时间值。
     * 
     * <p>策略：
     * <ul>
     *   <li>当使用自定义太阳模式时，根据太阳高度角计算等效的世界时间</li>
     *   <li>天空角度公式：angle = (timeOfDay + tickDelta) / 24000.0f</li>
     *   <li>反向计算：timeOfDay = angle * 24000.0f - tickDelta</li>
     *   <li>这样 getCelestialAngle() 等方法会自动使用我们返回的时间值</li>
     * </ul>
     * 
     * @param cir 回调返回值（世界时间，单位：tick）
     */
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void chronoblocks$onGetTimeOfDay(CallbackInfoReturnable<Long> cir) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (controller == null || !controller.isEnabled()) {
            return; // 未启用，使用原版逻辑
        }

        Optional<TimeSample> sampleOpt = controller.getCurrentSample();
        if (sampleOpt.isEmpty()) {
            return; // 没有采样，使用原版逻辑
        }

        TimeSample sample = sampleOpt.get();
        if (!sample.isUseCustomSun()) {
            return; // 不使用自定义太阳，使用原版逻辑
        }

        // ⭐ 根据自定义太阳高度角计算等效的世界时间
        // 
        // Minecraft 天空角度公式：skyAngle = (timeOfDay + tickDelta) / 24000.0f
        // 范围：0.0 (午夜) 到 1.0 (下一个午夜)
        // 
        // 我们要做的是：根据太阳高度角计算出等效的天空角度，然后反推出世界时间
        // 
        // 太阳高度角到天空角度的映射：
        // - 0° (地平线) → skyAngle ≈ 0.0 或 0.5 (日出/日落或午夜)
        // - 45° (半高) → skyAngle ≈ 0.375 或 0.625 (上午/下午)
        // - 90° (正头顶) → skyAngle = 0.5 (正午)
        //
        // 简化映射：将高度角映射到 0.25-0.75 范围（日出到日落）
        float sunElevation = sample.getSunElevation();
        float normalizedElevation = sunElevation / 90.0f; // 归一化到 0-1
        
        // 将高度角映射到天空角度（0.25 = 日出，0.5 = 正午，0.75 = 日落）
        float skyAngle = 0.25f + normalizedElevation * 0.5f;
        
        // 反推出世界时间：timeOfDay = skyAngle * 24000.0f - tickDelta
        // 注意：getTimeOfDay() 不带 tickDelta 参数，所以我们使用 0.0 作为插值
        // 实际的天空角度会在渲染时加上 tickDelta，所以这里我们返回基础时间即可
        long equivalentTime = (long)(skyAngle * 24000.0f);
        
        // 确保时间在有效范围内（0-24000）
        equivalentTime = Math.max(0L, Math.min(24000L, equivalentTime));
        
        cir.setReturnValue(equivalentTime);
    }
    
    /**
     * 备选方案：如果 getTimeOfDay() 也无法解析，可以尝试拦截天空角度计算方法
     * 
     * <p>⚠️ 注意：这个方法需要根据实际 Minecraft 版本找到正确的方法名。
     * 常见的方法名：
     * <ul>
     *   <li><code>getCelestialAngle(float tickDelta) → Float</code></li>
     *   <li><code>getSkyAngle(float tickDelta) → Float</code></li>
     * </ul>
     * 
     * <p>使用方式：取消注释下面的代码，并将方法名改为实际的方法名
     */
    /*
    @Inject(method = "getCelestialAngle", at = @At("HEAD"), cancellable = true)
    private void chronoblocks$onGetCelestialAngle(float tickDelta, CallbackInfoReturnable<Float> cir) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (controller == null || !controller.isEnabled()) {
            return;
        }

        Optional<TimeSample> sampleOpt = controller.getCurrentSample();
        if (sampleOpt.isEmpty()) {
            return;
        }

        TimeSample sample = sampleOpt.get();
        if (!sample.isUseCustomSun()) {
            return;
        }

        float sunElevation = sample.getSunElevation();
        float normalizedElevation = sunElevation / 90.0f;
        float skyAngle = 0.25f + normalizedElevation * 0.5f;
        cir.setReturnValue(skyAngle);
    }
    */
}
