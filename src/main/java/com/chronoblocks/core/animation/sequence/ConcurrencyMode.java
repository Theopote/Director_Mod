package com.chronoblocks.core.animation.sequence;

/**
 * 并发模式
 * 
 * <p>控制方块动画的执行方式：
 * <ul>
 *   <li>ONE_BY_ONE：基于 score 串行触发（最常用）</li>
 *   <li>ALL_AT_ONCE：所有块同时触发（忽略 score）</li>
 *   <li>IN_BATCHES：按 batchSize 分组并发触发（每组同时，组间有间隔）</li>
 *   <li>PARALLEL_LAYERS：层并行，但层内串行（用于"逐层但层内分批"的场景）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum ConcurrencyMode {
    
    /**
     * 串行执行：基于 score 逐个触发
     */
    ONE_BY_ONE("串行执行", "基于排序得分逐个触发方块动画"),
    
    /**
     * 同时执行：所有方块同时触发（忽略排序）
     */
    ALL_AT_ONCE("同时执行", "所有方块同时触发动画，忽略排序得分"),
    
    /**
     * 批量执行：按批次并发触发
     */
    IN_BATCHES("批量执行", "按批次并发触发，每组同时执行，组间有间隔"),
    
    /**
     * 层并行：层与层之间并行，但层内串行
     */
    PARALLEL_LAYERS("层并行", "层与层之间并行执行，但层内按顺序执行");
    
    private final String displayName;
    private final String description;
    
    ConcurrencyMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}

