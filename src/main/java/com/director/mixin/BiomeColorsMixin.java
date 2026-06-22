package com.director.mixin;

import com.director.timeline.playback.season.SeasonSample;
import com.director.timeline.playback.season.TimelineSeasonController;
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
 * @author Director Team
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
        TimelineSeasonController controller = TimelineSeasonController.getInstance();
        if (!controller.hasOverride()) {
            return; // 没有季节覆盖，直接返回原始颜色
        }
        
        SeasonSample s = controller.getCurrentSample();
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
        TimelineSeasonController controller = TimelineSeasonController.getInstance();
        if (!controller.hasOverride()) {
            return; // 没有季节覆盖，直接返回原始颜色
        }
        
        SeasonSample s = controller.getCurrentSample();
        if (s == null) {
            return;
        }
        cir.setReturnValue(applyTint(cir.getReturnValue(), s.foliageTint));
    }
    
    /**
     * 应用颜色 tint 到原始颜色（通用工具函数）
     * 
     * <p>颜色格式：Minecraft 生物群系颜色通常是 RGB (0xRRGGBB)，但可能被解释为 ARGB (0xAARRGGBB)。
     * 为了安全，我们确保 Alpha 通道总是 0xFF（完全不透明）。
     */
    @Unique
    private static int applyTint(int color, Vector3f tint) {
        // 提取 RGB 通道（Minecraft 颜色格式可能是 0xRRGGBB 或 0xFFRRGGBB）
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // 确保 Alpha 通道为 0xFF（完全不透明）
        // 如果原始颜色的 alpha 为 0，说明可能是 RGB 格式，需要设置 alpha = 0xFF
        int a = (color >> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF; // Minecraft 生物群系颜色应该是完全不透明的
        }
        
        // 应用 tint 偏移：finalColor = originalColor * (1.0 + tint)
        float rf = clamp01((r / 255f) * (1f + clampTint(tint.x)));
        float gf = clamp01((g / 255f) * (1f + clampTint(tint.y)));
        float bf = clamp01((b / 255f) * (1f + clampTint(tint.z)));
        
        // 重新打包为 ARGB 格式，确保 alpha = 0xFF
        return (0xFF << 24)
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

