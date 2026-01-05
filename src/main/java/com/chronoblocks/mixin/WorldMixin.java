package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe;
import com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController;
import com.chronoblocks.ui.timeline.playback.weather.WeatherSample;
import com.chronoblocks.ui.timeline.playback.time.TimeSample;
import com.chronoblocks.ui.timeline.playback.time.TimelineWorldController;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WorldMixin v1.0
 * 
 * <p>覆盖 Minecraft World 类的天气相关方法，以支持 ChronoBlocks 天气轨道系统。
 * 
 * <p>功能：
 * <ul>
 *   <li>覆盖雨强度 (getRainGradient)</li>
 *   <li>覆盖雷暴强度 (getThunderGradient)</li>
 *   <li>覆盖是否下雨 (isRaining)</li>
 *   <li>覆盖是否打雷 (isThundering)</li>
 * </ul>
 * 
 * <p>注意：这些方法在 World 基类中定义，ClientWorld 继承自 World。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
@Mixin(World.class)
public abstract class WorldMixin {

    /**
     * Time Track：覆盖世界时间读取，避免服务端/原版每 tick 同步把时间写回导致“闪回当前世界时间”。
     *
     * <p>注意：我们尽量保留原始 day 计数，仅替换 time-of-day（0~23999）。
     */
    @Inject(method = "getTimeOfDay()J", at = @At("RETURN"), cancellable = true)
    private void overrideTimeOfDay(CallbackInfoReturnable<Long> cir) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        TimeSample sample = controller.current();
        if (sample == null) {
            return;
        }

        long base = cir.getReturnValue();
        long day = Math.floorDiv(base, 24000L);
        long tod = sample.getWorldTimeTicks() % 24000L;
        if (tod < 0) tod += 24000L;
        cir.setReturnValue(day * 24000L + tod);
    }

    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "getSkyAngle(F)F", at = @At("HEAD"), cancellable = true)
    private void overrideSkyAngle(float tickDelta, CallbackInfoReturnable<Float> cir) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        TimeSample sample = controller.current();
        if (sample == null) {
            return;
        }
        cir.setReturnValue(sample.getSkyAngle());
    }

    @Inject(method = "getSkyAngleRadians(F)F", at = @At("HEAD"), cancellable = true)
    private void overrideSkyAngleRadians(float tickDelta, CallbackInfoReturnable<Float> cir) {
        TimelineWorldController controller = TimelineWorldController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        TimeSample sample = controller.current();
        if (sample == null) {
            return;
        }
        cir.setReturnValue(sample.getSkyAngle() * (float) (Math.PI * 2.0));
    }

    /**
     * Weather：覆盖雨强度
     * 
     * ✅ 修复：当存在天气轨道时，完全禁止原版天气事件
     * - 如果轨道有降水，使用轨道的值
     * - 如果轨道没有降水，强制返回 0（禁止原版下雨）
     */
    @Inject(
        method = "getRainGradient(F)F",
        at = @At("RETURN"),
        cancellable = true
    )
    private void overrideRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        TimelineWeatherController controller = TimelineWeatherController.getInstance();
        if (!controller.hasWeatherTrack()) {
            return;
        }

        WeatherSample sample = controller.getCurrentSample();
        if (sample == null || !sample.enabled) {
            // ✅ 天气轨道启用但 sample 无效，禁止原版天气
            cir.setReturnValue(0.0f);
            return;
        }

        // ✅ 如果天气系统有降水，使用轨道的值；否则强制返回 0（禁止原版下雨）
        if (sample.rainStrength > 0.01f && sample.rainType != WeatherKeyframe.RainType.NONE) {
            cir.setReturnValue(sample.rainStrength);
        } else {
            // 轨道没有设置降水，完全禁止原版下雨
            cir.setReturnValue(0.0f);
        }
    }

    /**
     * Weather：覆盖雷暴强度
     * 
     * ✅ 修复：当存在天气轨道时，完全禁止原版天气事件
     * - 如果轨道有雷暴，使用轨道的值
     * - 如果轨道没有雷暴，强制返回 0（禁止原版雷暴）
     */
    @Inject(
        method = "getThunderGradient(F)F",
        at = @At("RETURN"),
        cancellable = true
    )
    private void overrideThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        TimelineWeatherController controller = TimelineWeatherController.getInstance();
        if (!controller.hasWeatherTrack()) {
            return;
        }

        WeatherSample sample = controller.getCurrentSample();
        if (sample == null || !sample.enabled) {
            // ✅ 天气轨道启用但 sample 无效，禁止原版天气
            cir.setReturnValue(0.0f);
            return;
        }

        // ✅ 如果天气系统有雷暴，使用轨道的值；否则强制返回 0（禁止原版雷暴）
        if (sample.thunderStrength > 0.01f) {
            cir.setReturnValue(sample.thunderStrength);
        } else {
            // 轨道没有设置雷暴，完全禁止原版雷暴
            cir.setReturnValue(0.0f);
        }
    }

    /**
     * Weather：覆盖是否下雨
     * 
     * ✅ 修复：当存在天气轨道时，完全禁止原版天气事件
     * - 如果轨道有降水，返回 true
     * - 如果轨道没有降水，强制返回 false（禁止原版下雨）
     */
    @Inject(
        method = "isRaining()Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private void overrideIsRaining(CallbackInfoReturnable<Boolean> cir) {
        TimelineWeatherController controller = TimelineWeatherController.getInstance();
        if (!controller.hasWeatherTrack()) {
            return;
        }

        WeatherSample sample = controller.getCurrentSample();
        if (sample == null || !sample.enabled) {
            // ✅ 天气轨道启用但 sample 无效，禁止原版天气
            cir.setReturnValue(false);
            return;
        }

        // ✅ 如果天气系统有降水，返回 true；否则强制返回 false（禁止原版下雨）
        if (sample.rainStrength > 0.01f && sample.rainType != WeatherKeyframe.RainType.NONE) {
            cir.setReturnValue(true);
        } else {
            // 轨道没有设置降水，完全禁止原版下雨
            cir.setReturnValue(false);
        }
    }

    /**
     * Weather：覆盖是否打雷
     * 
     * ✅ 修复：当存在天气轨道时，完全禁止原版天气事件
     * - 如果轨道有雷暴，返回 true
     * - 如果轨道没有雷暴，强制返回 false（禁止原版雷暴）
     */
    @Inject(
        method = "isThundering()Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private void overrideIsThundering(CallbackInfoReturnable<Boolean> cir) {
        TimelineWeatherController controller = TimelineWeatherController.getInstance();
        if (!controller.hasWeatherTrack()) {
            return;
        }

        WeatherSample sample = controller.getCurrentSample();
        if (sample == null || !sample.enabled) {
            // ✅ 天气轨道启用但 sample 无效，禁止原版天气
            cir.setReturnValue(false);
            return;
        }

        // ✅ 如果天气系统有雷暴，返回 true；否则强制返回 false（禁止原版雷暴）
        if (sample.thunderStrength > 0.01f) {
            cir.setReturnValue(true);
        } else {
            // 轨道没有设置雷暴，完全禁止原版雷暴
            cir.setReturnValue(false);
        }
    }
}

