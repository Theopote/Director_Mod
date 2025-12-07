package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.world.BlockRenderView;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BiomeColorsMixin v3.5
 * 
 * <p>拦截生物群系颜色获取，应用季节系统的颜色偏移。
 * 
 * <p>应用季节颜色到：
 * <ul>
 *   <li>草地颜色（grass）</li>
 *   <li>树叶颜色（foliage）</li>
 * </ul>
 * 
 * <p>设计：颜色 tint 是相对偏移值，使用公式：
 * <pre>
 * finalColor = originalColor * (1.0 + tint)
 * </pre>
 * 
 * <p>例如：tint = 0.1 表示增加 10%，tint = -0.1 表示减少 10%
 * 
 * @author ChronoBlocks Team
 * @version 3.5 - 统一 tint 公式，添加 null check 和 tint 范围限制
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    
    /**
     * 拦截草地颜色获取
     */
    @Inject(
        method = "getGrassColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void modifyGrassColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        SeasonSample s = TimelineSeasonController.getInstance().getCurrentSample();
        if (s == null) {
            return;
        }
        cir.setReturnValue(applyTint(cir.getReturnValue(), s.grassTint));
    }
    
    /**
     * 拦截树叶颜色获取
     */
    @Inject(
        method = "getFoliageColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void modifyFoliageColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        SeasonSample s = TimelineSeasonController.getInstance().getCurrentSample();
        if (s == null) {
            return;
        }
        cir.setReturnValue(applyTint(cir.getReturnValue(), s.foliageTint));
    }
    
    /**
     * 应用颜色 tint 到原始颜色（通用工具函数）
     * 
     * <p>颜色格式：ARGB (0xAARRGGBB)
     */
    @Unique
    private static int applyTint(int argb, Vector3f tint) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        
        float rf = clamp01((r / 255f) * (1f + clampTint(tint.x)));
        float gf = clamp01((g / 255f) * (1f + clampTint(tint.y)));
        float bf = clamp01((b / 255f) * (1f + clampTint(tint.z)));
        
        return (a << 24)
                | ((int) (rf * 255) << 16)
                | ((int) (gf * 255) << 8)
                | (int) (bf * 255);
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

