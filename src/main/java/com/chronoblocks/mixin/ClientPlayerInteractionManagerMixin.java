package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ClientPlayerInteractionManager的Mixin
 * 在ChronoBlocks模式下阻止方块交互
 * 
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    /**
     * 拦截方块右键交互
     * 在ChronoBlocks模式下阻止交互
     */
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(net.minecraft.client.network.ClientPlayerEntity player, 
                                Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        // ChronoBlocks 界面检查
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldDisableBlockInteraction()) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }
    
    /**
     * 拦截方块左键攻击
     * 在ChronoBlocks模式下阻止破坏
     */
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        // ChronoBlocks 界面检查
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldDisableBlockInteraction()) {
            cir.setReturnValue(false);
        }
    }
    
    /**
     * 拦截方块持续破坏
     * 在ChronoBlocks模式下阻止持续破坏
     */
    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        // ChronoBlocks 界面检查
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldDisableBlockInteraction()) {
            cir.setReturnValue(false);
        }
    }
}