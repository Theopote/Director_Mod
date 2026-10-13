package com.director.mixin;

import com.director.core.animation.WholeContainerRenderView;
import com.director.lighting.PointLightRuntime;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class PointLightWorldRendererMixin {
    @Inject(method="getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I", at=@At("RETURN"), cancellable=true)
    private static void director$pointLight(WorldRenderer.BrightnessGetter brightness, BlockRenderView view,
                                            BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        BlockPos sample=view instanceof WholeContainerRenderView container ? container.displayedPosition(pos) : pos;
        cir.setReturnValue(PointLightRuntime.sample(cir.getReturnValue(),sample.getX()+.5,sample.getY()+.5,sample.getZ()+.5));
    }
}
