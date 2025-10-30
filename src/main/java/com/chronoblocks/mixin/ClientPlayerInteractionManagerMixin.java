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
 * 当ChronoBlocks界面打开时，完全阻止方块交互
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    /**
     * 拦截方块右键交互
     * 当ChronoBlocks界面打开时，完全阻止方块交互
     */
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(net.minecraft.client.network.ClientPlayerEntity player, 
                                Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller.shouldDisableBlockInteraction()) {
            System.out.println("[ClientPlayerInteractionManagerMixin] ✅ 方块右键交互被阻止");
            cir.setReturnValue(ActionResult.PASS);
        }
    }
    
    /**
     * 拦截方块左键攻击
     * 当ChronoBlocks界面打开时，完全阻止方块破坏
     */
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller.shouldDisableBlockInteraction()) {
            System.out.println("[ClientPlayerInteractionManagerMixin] ✅ 方块左键攻击被阻止");
            cir.setReturnValue(false);
        }
    }
}
