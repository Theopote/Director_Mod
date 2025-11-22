package com.chronoblocks.core.animation.sequence;

import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 序列策略接口
 * 
 * <p>定义方块动画的执行顺序逻辑。
 * 与 {@link com.chronoblocks.core.animation.BlockAnimationEngine} 配合使用：
 * <ul>
 *   <li>BlockAnimationEngine：计算方块出现时间（何时显示）</li>
 *   <li>SequenceStrategy：决定方块执行顺序（按什么顺序显示）</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>OneByOne：按顺序一个接一个显示</li>
 *   <li>Random：随机顺序显示</li>
 *   <li>Wave：波浪式显示（按行列或距离）</li>
 *   <li>Batch：批量显示（每批N个方块）</li>
 *   <li>Spiral：螺旋式显示（从中心向外）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface SequenceStrategy {
    
    /**
     * 根据序列规则返回处理方块的顺序列表
     * 
     * <p>此方法将无序的方块列表转换为有序列表，决定方块的执行顺序。
     * 
     * @param blocks 原始方块列表（无序，通常来自 Container.getBlocks()）
     * @param container 容器对象（用于获取边界框、中心点等信息）
     * @param params 序列参数（如批量大小、随机种子、波浪方向等）
     * @return 排序后的方块列表（有序）
     */
    List<BlockPos> order(List<BlockPos> blocks, Container container, SequenceParameters params);
    
    /**
     * 获取策略名称（用于调试和日志）
     * 
     * @return 策略名称，如 "OneByOne", "Random", "Wave" 等
     */
    String getStrategyName();
    
    /**
     * 获取策略的显示名称（用于UI显示）
     * 
     * @return 显示名称，如 "顺序执行", "随机执行", "波浪式" 等
     */
    String getDisplayName();
    
    /**
     * 获取策略的描述（用于UI提示）
     * 
     * @return 描述文本
     */
    String getDescription();
}

