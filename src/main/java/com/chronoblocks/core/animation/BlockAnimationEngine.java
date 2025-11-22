package com.chronoblocks.core.animation;

import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

/**
 * 方块动画引擎接口
 * 
 * <p>定义容器中方块出现/消失的动画效果执行逻辑
 * 
 * <p>核心概念：
 * <ul>
 *   <li>片段的时长 = 方块从隐藏到完全出现的动画过程</li>
 *   <li>动画引擎根据片段的配置（AnimationMode、duration等）控制方块的出现时机</li>
 *   <li>每种动画模式有不同的算法来计算方块应该何时显示</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface BlockAnimationEngine {
    
    /**
     * 计算指定方块在片段中的出现时间
     * 
     * <p>根据动画模式、动画时长、方块位置等信息，计算每个方块应该在哪一时刻显示
     * 
     * @param container 容器对象
     * @param blockPos 方块位置
     * @param clipStartTime 片段开始时间（秒）
     * @param clipDuration 片段时长（秒）
     * @param animationDuration 动画时长（秒，在片段时长内）
     * @param params 额外的动画参数（如扩散方向、顺序等）
     * @return 方块应该显示的时间（相对于片段开始时间，单位：秒），如果返回 0 或负数表示立即显示，如果返回 clipDuration 或更大值表示不显示
     */
    float calculateBlockAppearTime(
        Container container,
        BlockPos blockPos,
        float clipStartTime,
        float clipDuration,
        float animationDuration,
        java.util.Map<String, Object> params
    );
    
    /**
     * 获取该动画引擎支持的动画模式
     * 
     * @return 支持的 AnimationMode 数组
     */
    com.chronoblocks.core.container.AnimationMode[] getSupportedModes();
    
    /**
     * 获取动画引擎的名称（用于调试和日志）
     * 
     * @return 引擎名称
     */
    String getEngineName();
}