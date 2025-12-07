package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.ui.timeline.playback.time.TimelineWorldController;
import com.chronoblocks.ui.timeline.playback.season.SeasonSample;
import com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
            // ⚠️ 只在首次进入冻结状态时打 info 日志，避免每 tick 刷屏
            if (logger.isTraceEnabled()) {
                if (isDirectorMode) {
                    logger.trace("[ClientWorldMixin] ❄️ tickTime() 被拦截 - ChronoBlocks 导演模式 + 时间轨道系统冻结");
                } else {
                    logger.trace("[ClientWorldMixin] ❄️ tickTime() 被拦截 - 时间轨道系统冻结");
                }
            }
            
            ci.cancel();
            
            // ⭐ v2.0 核心改进：维持冻结时间
            // 在时间 tick 被取消后，立即维持冻结的时间值
            // 这确保即使有其他代码试图修改世界时间，也会立即被恢复
            timeController.maintainFrozenTime((ClientWorld) (Object) this);
        } else if (isDirectorMode) {
            // 只有导演模式，没有时间轨道系统冻结
            if (logger.isTraceEnabled()) {
                logger.trace("[ClientWorldMixin] ❄️ tickTime() 被拦截 - ChronoBlocks 导演模式");
            }
            ci.cancel();
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("[ClientWorldMixin] ✓ tickTime() 正常执行 - 不冻结");
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
    
    /**
     * ⚠️ 备选方案：通过覆盖 getSkyAngle() 来影响太阳高度
     * 
     * 限制：这种方式只能控制太阳高度，无法控制方位角
     * 因为原版只使用 skyAngle 来计算太阳高度，不涉及方位角
     * 
     * 如果需要完整的太阳位置控制（高度+方位），需要在 WorldRenderer.renderSky() 中
     * 修改旋转矩阵（见 SkyRendererMixin）
     * 
     * 当前先通过这种方式实现基本的高度控制
     */
    // TODO: 如果能够找到正确的方法签名，取消下面的注释
    // @ModifyReturnValue(
    //     method = "getSkyAngle", // 需要确认方法签名，可能是 getSkyAngle(F)F 或其他
    //     at = @At("RETURN")
    // )
    // private float chronoblocks$modifySkyAngle(float original, float tickDelta) {
    //     TimelineWorldController controller = TimelineWorldController.getInstance();
    //     if (controller == null || !controller.hasOverride()) {
    //         return original;
    //     }
    // 
    //     TimeSample sample = controller.current();
    //     if (sample == null || sample.isUseWorldTime()) {
    //         return original;
    //     }
    // 
    //     // ⚠️ 限制：只能返回自定义的天空角度，无法控制方位角
    //     // 完整的太阳位置控制需要在 WorldRenderer 中修改旋转矩阵
    //     return sample.getSkyAngle();
    // }
    
    /**
     * ⭐ v2.6：拦截天空颜色获取，应用季节系统的天空颜色偏移
     * 
     * <p>方法签名（Minecraft 1.21.4）：
     * <p>getSkyColor(Vec3d pos, float tickDelta) -> int
     * 
     * <p>返回 ARGB 格式的颜色值
     */
    @Inject(
        method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)I",
        at = @At("RETURN"),
        cancellable = true
    )
    private void chronoblocks$modifySkyColor(Vec3d pos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        TimelineSeasonController controller = TimelineSeasonController.getInstance();
        if (!controller.hasOverride()) {
            return;
        }
        
        SeasonSample sample = controller.getCurrentSample();
        int originalColor = cir.getReturnValue();
        
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
        rF = Math.max(0f, Math.min(1f, rF * (1f + sample.skyTint.x)));
        gF = Math.max(0f, Math.min(1f, gF * (1f + sample.skyTint.y)));
        bF = Math.max(0f, Math.min(1f, bF * (1f + sample.skyTint.z)));
        
        // 转换回整数并打包为 ARGB 格式
        int newR = (int)(rF * 255);
        int newG = (int)(gF * 255);
        int newB = (int)(bF * 255);
        
        // ⚠️ 重要：打包顺序必须是 ARGB = (A << 24) | (R << 16) | (G << 8) | B
        int newColor = (a << 24) | (newR << 16) | (newG << 8) | newB;
        cir.setReturnValue(newColor);
    }
}