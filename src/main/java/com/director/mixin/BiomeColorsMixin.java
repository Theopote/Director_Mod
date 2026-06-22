package com.director.mixin;

import com.director.client.world.ClientWorldTintEffects;
import com.director.client.world.WorldRenderOverride;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BiomeColors mixin — 季节草地/树叶 tint 委托 {@link ClientWorldTintEffects}。
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {

    @Inject(
        method = "getGrassColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void modifyGrassColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasSeasonOverride() || state.seasonSample() == null) {
            return;
        }
        cir.setReturnValue(ClientWorldTintEffects.tintBiomeColor(
                cir.getReturnValue(), state.seasonSample().grassTint));
    }

    @Inject(
        method = "getFoliageColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void modifyFoliageColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasSeasonOverride() || state.seasonSample() == null) {
            return;
        }
        cir.setReturnValue(ClientWorldTintEffects.tintBiomeColor(
                cir.getReturnValue(), state.seasonSample().foliageTint));
    }
}
