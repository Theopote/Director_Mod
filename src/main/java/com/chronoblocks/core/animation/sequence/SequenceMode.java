package com.chronoblocks.core.animation.sequence;

/**
 * 序列模式枚举
 * 
 * <p>定义不同的方块执行顺序模式。
 * 目前运行时统一使用 {@link com.chronoblocks.core.animation.sequence.BlockSequenceGenerator}
 * 与 {@link com.chronoblocks.core.animation.sequence.SequenceParameters} 生成序列。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum SequenceMode {
    
    /**
     * 基于排序算子：使用新的权重排序系统，支持参考系、主次排序、复合策略
     * 
     * <p>这是唯一的序列模式，通过组合不同的排序策略可以实现各种效果：
     * <ul>
     *   <li>顺序执行：主排序 NONE + 次排序 LINEAR</li>
     *   <li>随机执行：主排序 NONE + 次排序 RANDOM</li>
     *   <li>波浪式：主排序 LAYERED_Y + 次排序 RADIAL</li>
     *   <li>螺旋式：主排序 NONE + 次排序 SPIRAL</li>
     *   <li>并行执行：主排序 NONE + 次排序 NONE</li>
     * </ul>
     */
    EVALUATOR_BASED("基于排序算子", "使用新的权重排序系统，支持参考系、主次排序、复合策略");
    
    private final String displayName;
    private final String description;
    
    SequenceMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}

