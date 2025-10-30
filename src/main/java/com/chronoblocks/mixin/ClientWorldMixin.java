package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 🎬 ClientWorldMixin v2.3
 * -----------------------------------------------------
 * 在 ChronoBlocks 导演模式下实现选择性冻结：
 * - 暂停世界时间流逝 (tickTime)
 * - 停止随机方块显示效果 (doRandomBlockDisplayTicks)
 * - 实体冻结通过setFrozenTicks机制控制（不拦截tickEntities）
 * 
 * ✅ 保持玩家移动能力，只冻结非玩家实体
 * 
 * @author ChronoBlocks Team
 * @version 2.3
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    /**
     * 拦截世界时间更新（时间冻结）
     */
    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void onTickTime(CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.isChronoBlocksModeActive()) {
            ci.cancel();
        }
    }

    // 注意：不拦截tickEntities()，因为这会阻止玩家移动
    // 玩家移动通过实体冻结机制（setFrozenTicks）来控制

    /**
     * 拦截随机方块显示效果（方块粒子、显示效果）
     */
    @Inject(method = "doRandomBlockDisplayTicks", at = @At("HEAD"), cancellable = true)
    private void onDoRandomBlockDisplayTicks(int centerX, int centerY, int centerZ, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.isChronoBlocksModeActive()) {
            ci.cancel();
        }
    }

    // 注意：tickWeather和tickLighting方法在ClientWorld中不存在
    // 天气和光照处理在更高层级，通过tickTime和tickEntities已经可以拦截大部分功能
}
