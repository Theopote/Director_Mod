package com.director.mixin;

import com.director.core.container.visibility.ClientSectionInvalidation;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe actual chunk writes, including network updates that bypass ClientWorld#setBlockState. */
@Mixin(WorldChunk.class)
public abstract class ClientChunkReadinessMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Lnet/minecraft/block/BlockState;",
            at = @At("RETURN"))
    private void director$invalidateReadiness(BlockPos pos, BlockState state, int flags,
            CallbackInfoReturnable<BlockState> cir) {
        var world = ((WorldChunk) (Object) this).getWorld();
        if (world instanceof ClientWorld && cir.getReturnValue() != null)
            ClientSectionInvalidation.getInstance().invalidateBlock(world, pos);
    }
}
