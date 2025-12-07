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
 * BiomeColorsMixin v1.2
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
 * @version 1.2 - 修复颜色打包顺序错误，提取通用函数
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    
    /**
     * 拦截草地颜色获取
     * 
     * <p>方法签名参考（Minecraft 1.21.4 Yarn）：
     * <p>getGrassColor(BlockRenderView world, BlockPos pos)
     */
    @Inject(
        method = "getGrassColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void chronoblocks$modifyGrassColor(
            BlockRenderView world,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        SeasonSample sample = TimelineSeasonController.getInstance().getCurrentSample();
        int originalColor = cir.getReturnValue();
        int tintedColor = tintColor(originalColor, sample.grassTint);
        cir.setReturnValue(tintedColor);
    }
    
    /**
     * 拦截树叶颜色获取
     * 
     * <p>方法签名参考（Minecraft 1.21.4 Yarn）：
     * <p>getFoliageColor(BlockRenderView world, BlockPos pos)
     */
    @Inject(
        method = "getFoliageColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void chronoblocks$modifyFoliageColor(
            BlockRenderView world,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        SeasonSample sample = TimelineSeasonController.getInstance().getCurrentSample();
        int originalColor = cir.getReturnValue();
        int tintedColor = tintColor(originalColor, sample.foliageTint);
        cir.setReturnValue(tintedColor);
    }
    
    /**
     * 应用颜色 tint 到原始颜色（通用工具函数）
     * 
     * <p>颜色格式：ARGB (0xAARRGGBB)
     * <ul>
     *   <li>提取：A = (color >> 24) & 0xFF, R = (color >> 16) & 0xFF, G = (color >> 8) & 0xFF, B = color & 0xFF</li>
     *   <li>打包：ARGB = (A << 24) | (R << 16) | (G << 8) | B</li>
     * </ul>
     * 
     * @param originalColor 原始颜色（ARGB 格式）
     * @param tint 颜色偏移（Vector3f，x=R, y=G, z=B）
     * @return 应用 tint 后的颜色（ARGB 格式）
     */
    @Unique
    private static int tintColor(int originalColor, Vector3f tint) {
        // 提取 ARGB 分量
        int a = (originalColor >> 24) & 0xFF;
        int r = (originalColor >> 16) & 0xFF;
        int g = (originalColor >> 8) & 0xFF;
        int b = originalColor & 0xFF;
        
        // 转换为 [0, 1] 范围的浮点数
        float rF = r / 255f;
        float gF = g / 255f;
        float bF = b / 255f;
        
        // 应用 tint 偏移（tint 是相对偏移，例如 0.1 表示 +10%）
        rF = clamp01(rF * (1f + tint.x));
        gF = clamp01(gF * (1f + tint.y));
        bF = clamp01(bF * (1f + tint.z));
        
        // 转换回整数并打包为 ARGB 格式
        int newR = (int)(rF * 255);
        int newG = (int)(gF * 255);
        int newB = (int)(bF * 255);
        
        // ⚠️ 重要：打包顺序必须是 ARGB = (A << 24) | (R << 16) | (G << 8) | B
        return (a << 24) | (newR << 16) | (newG << 8) | newB;
    }
    
    /**
     * 将值限制在 [0, 1] 范围内
     */
    @Unique
    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}

