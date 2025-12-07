package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.ui.timeline.playback.time.TimelineWorldController;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 🎬 ClientWorldMixin v2.5
 * -----------------------------------------------------
 * 在 ChronoBlocks 导演模式下实现选择性冻结：
 * - 暂停世界时间流逝 (tickTime)
 * - 停止随机方块显示效果 (doRandomBlockDisplayTicks)
 * - 实体冻结通过setFrozenTicks机制控制（不拦截tickEntities）
 * - ⭐ 支持时间轨道系统的时间冻结
 * <p>
 * ✅ 保持玩家移动能力，只冻结非玩家实体
 * 
 * @author ChronoBlocks Team
 * @version 2.5 - 集成时间轨道系统
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    /**
     * 拦截世界时间更新（时间冻结）
     * 
     * <p>冻结条件：
     * <ul>
     *   <li>ChronoBlocks 导演模式激活时</li>
     *   <li>时间轨道系统激活且有活跃时间轨道时</li>
     * </ul>
     */
    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void onTickTime(CallbackInfo ci) {
        // ⭐ DEBUG: 记录每次 tickTime 调用
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("ClientWorldMixin");
        if (logger.isTraceEnabled()) {
            logger.trace("[ClientWorldMixin] 🔔 tickTime() 被调用");
        }
        
        // 检查 ChronoBlocks 导演模式
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        boolean isDirectorMode = controller != null && controller.isChronoBlocksModeActive();
        
        // ⭐ v2.0：检查时间轨道系统是否需要冻结时间
        TimelineWorldController timeController = TimelineWorldController.getInstance();
        boolean shouldFreezeTime = timeController.isEnabled() && timeController.shouldFreezeTime();
        
        // ⭐ v3.4 关键修复：即使导演模式激活，时间轨道系统也需要维持冻结时间
        // 导演模式只是取消原版时间推进，但时间轨道系统需要每 tick 调用 maintainFrozenTime()
        // 来确保设置的时间不被重置（防止世界时间被其他代码修改）
        if (shouldFreezeTime) {
            if (isDirectorMode) {
                logger.info("[ClientWorldMixin] ❄️ tickTime() 被拦截 - ChronoBlocks 导演模式 + 时间轨道系统冻结 (enabled={}, shouldFreeze={})", 
                        timeController.isEnabled(), timeController.shouldFreezeTime());
            } else {
                logger.info("[ClientWorldMixin] ❄️ tickTime() 被拦截 - 时间轨道系统冻结 (enabled={}, shouldFreeze={})", 
                        timeController.isEnabled(), timeController.shouldFreezeTime());
            }
            
            ci.cancel();
            
            // ⭐ v2.0 核心改进：维持冻结时间
            // 在时间 tick 被取消后，立即维持冻结的时间值
            // 这确保即使有其他代码试图修改世界时间，也会立即被恢复
            timeController.maintainFrozenTime((ClientWorld) (Object) this);
        } else if (isDirectorMode) {
            // 只有导演模式，没有时间轨道系统冻结
            logger.info("[ClientWorldMixin] ❄️ tickTime() 被拦截 - ChronoBlocks 导演模式");
            ci.cancel();
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("[ClientWorldMixin] ✓ tickTime() 正常执行 - 不冻结 (导演模式={}, enabled={}, shouldFreeze={})",
                        false, timeController.isEnabled(), timeController.shouldFreezeTime());
            }
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
    
    // 注意：getBlockState 方法在 World 基类中，已移至 WorldMixin
    // 注意：tickWeather和tickLighting方法在ClientWorld中不存在
    // 天气和光照处理在更高层级，通过tickTime和tickEntities已经可以拦截大部分功能
}