package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController;
import net.minecraft.client.world.ClientWorld;
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

    /**
     * Season：天空 tint
     */
    @Inject(
        method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private void tintSky(Vec3d pos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        TimelineSeasonController controller = TimelineSeasonController.getInstance();
        if (!controller.hasOverride()) {
            return; // 没有季节覆盖，直接返回原始颜色
        }
        
        SeasonSample s = controller.getCurrentSample();
        if (s == null) {
            return;
        }
        
        int color = cir.getReturnValue();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // 确保 Alpha 通道为 0xFF（天空颜色应该完全不透明）
        int a = (color >> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF;
        }
        
        // 应用 tint 偏移：finalColor = originalColor * (1.0 + tint)
        float rf = clamp01((r / 255f) * (1f + clampTint(s.skyTint.x)));
        float gf = clamp01((g / 255f) * (1f + clampTint(s.skyTint.y)));
        float bf = clamp01((b / 255f) * (1f + clampTint(s.skyTint.z)));
        
        // 重新打包为 ARGB 格式，确保 alpha = 0xFF
        cir.setReturnValue((0xFF << 24)
                | ((int) (rf * 255) << 16)
                | ((int) (gf * 255) << 8)
                | (int) (bf * 255));
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