package com.chronoblocks.core.animation.sequence;

/**
 * 序列模式枚举
 * 
 * <p>定义不同的方块执行顺序模式。
 * 每种模式对应一个 SequenceStrategy 实现。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum SequenceMode {
    
    /**
     * 顺序执行：按照列表顺序一个接一个执行
     */
    ONE_BY_ONE("顺序执行", "按照列表顺序一个接一个执行"),
    
    /**
     * 随机执行：随机打乱顺序执行
     */
    RANDOM("随机执行", "随机打乱顺序执行"),
    
    /**
     * 批量执行：每批N个方块同时执行
     */
    BATCH("批量执行", "每批N个方块同时执行"),
    
    /**
     * 波浪式执行：按行列或距离波浪式执行
     */
    WAVE("波浪式", "按行列或距离波浪式执行"),
    
    /**
     * 螺旋式执行：从中心向外螺旋式执行
     */
    SPIRAL("螺旋式", "从中心向外螺旋式执行"),
    
    /**
     * 并行执行：所有方块同时执行（用于瞬间出现等场景）
     */
    PARALLEL("并行执行", "所有方块同时执行");
    
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

