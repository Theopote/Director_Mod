package com.chronoblocks.core.animation.effect;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Map;
import java.util.Random;

/**
 * 单个方块特效的接口。
 * 
 * <p>
 * 注意：render 在每一帧都会被调用，需要自行控制粒子数量/开销。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface IBlockEffect {

    /**
     * 渲染特效
     * 
     * @param world  客户端世界（用于 spawn 粒子 / 声音等）
     * @param pos    方块世界坐标
     * @param state  方块状态
     * @param t      特效归一化进度 [0,1]
     * @param inst   当前 EffectInstance 配置（含 startOffset/duration/params）
     * @param random 针对该方块的稳定随机源
     */
    void render(ClientWorld world,
            BlockPos pos,
            BlockState state,
            float t,
            EffectInstance inst,
            Random random);

    /**
     * 获取默认参数
     * 
     * <p>
     * 用于新建 EffectInstance 时填充初始值
     * 
     * @return 默认参数 Map
     */
    default Map<String, Object> getDefaultParams() {
        return Collections.emptyMap();
    }

    /**
     * 渲染该特效的参数编辑 UI
     * 
     * <p>
     * 如果特效支持 UI 渲染，实现此方法来自定义参数编辑界面。
     * 如果不支持（返回 false），UI 适配器将使用默认的参数编辑方式。
     * 
     * <p>
     * 注意：由于架构限制（core 包不应依赖 UI 包），此方法使用反射来访问 UI 类。
     * 如果 UI 类不可用，实现应返回 false。
     * 
     * @param params 参数配置 Map（可修改）
     * @return 是否修改了参数（用于标记是否需要保存）
     */
    default boolean renderUI(Map<String, Object> params) {
        // 默认实现：不渲染任何UI
        return false;
    }
}
