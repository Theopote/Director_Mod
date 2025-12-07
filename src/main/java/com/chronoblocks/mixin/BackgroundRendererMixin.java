package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BackgroundRendererMixin v1.0
 * 
 * <p>拦截 BackgroundRenderer.getFogColor() 方法，应用季节系统的雾颜色偏移。
 * 
 * <p>这是更稳定的注入方式，直接修改 getFogColor() 的返回值，不依赖于 WorldRenderer.render() 的局部变量。
 * 
 * <p>设计：颜色 tint 是相对偏移值，使用公式：
 * <pre>
 * finalColor = originalColor * (1.0 + tint)
 * </pre>
 * 
 * <p>例如：tint = 0.1 表示增加 10%，tint = -0.1 表示减少 10%
 * 
 * <p>注意：天空颜色在 {@link ClientWorldMixin} 中处理
 * 
 * @author ChronoBlocks Team
 * @version 1.0 - 直接注入 BackgroundRenderer.getFogColor()，提高稳定性
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
        TimelineSeasonController controller = TimelineSeasonController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        
        SeasonSample sample = controller.getCurrentSample();
        Vector4f original = cir.getReturnValue();
        
        if (original == null) {
            return;
        }
        
        // tint 是相对偏移，比如 0.1 表示 +10%
        float r = clamp01(original.x * (1f + sample.fogTint.x));
        float g = clamp01(original.y * (1f + sample.fogTint.y));
        float b = clamp01(original.z * (1f + sample.fogTint.z));
        float a = original.w; // 保持 alpha 不变
        
        Vector4f modified = new Vector4f(r, g, b, a);
        cir.setReturnValue(modified);
    }
    
    /**
     * 将值限制在 [0, 1] 范围内
     */
    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}

