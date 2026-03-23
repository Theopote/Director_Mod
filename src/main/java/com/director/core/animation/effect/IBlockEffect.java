package com.director.core.animation.effect;

import com.director.core.animation.BlockAnimationSnapshot;
import net.minecraft.client.world.ClientWorld;

import java.util.Collections;
import java.util.Map;
import java.util.Random;

/**
 * 单个方块特效的接口。
 * 
 * <p>
 * 注意：render 在每一帧都会被调用，需要自行控制粒子数量/开销。
 * 
 * @author Director Team
 * @version 1.0
 */
public interface IBlockEffect {

    /**
     * 执行特效渲染/粒子生成逻辑。
     * 
     * @param world    客户端世界
     * @param snapshot 当前方块的动画采样快照（包含位置、状态、变换矩阵等）
     * @param t        特效自身的内部进度 (0.0 -> 1.0)，由 startOffset 和 duration 映射得到
     * @param inst     当前特效实例的配置（参数、ID等）
     * @param random   每个方块、每个特效实例独立的随机源
     */
    void render(ClientWorld world,
            BlockAnimationSnapshot snapshot,
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
