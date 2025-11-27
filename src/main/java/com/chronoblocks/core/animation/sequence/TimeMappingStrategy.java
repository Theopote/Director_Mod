package com.chronoblocks.core.animation.sequence;

/**
 * 时间映射策略
 * 
 * <p>定义如何将排序得分（score）映射到具体的时间（startTime）。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum TimeMappingStrategy {
    
    /**
     * 线性映射：将归一化得分线性映射到 clip duration
     * blockStart = clipStart + delay + normalizedScore * (clipDuration - perBlockDuration)
     */
    NORMALIZED_LINEAR("线性映射", "将归一化得分线性映射到片段时长"),
    
    /**
     * 分组优先：先完成一组再开始下一组
     * 计算每组的 groupStart = clipStart + groupIndex * groupInterval
     * 组内用 normalizedScore 映射到 groupDuration
     */
    GROUP_FIRST("分组优先", "先完成一组再开始下一组，组内按得分映射"),
    
    /**
     * 批次映射：使用 batchSize 将排序结果分成若干批次
     * 每批同时执行，批与批之间有 interval
     */
    BATCH_MAPPING("批次映射", "按批次分组，每批同时执行，批间有间隔"),
    
    /**
     * 交错映射：支持 alternating（奇偶层方向相反）、ping-pong（反向交替）等
     */
    STAGGERED("交错映射", "支持交替、往复等交错执行模式");
    
    private final String displayName;
    private final String description;
    
    TimeMappingStrategy(String displayName, String description) {
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

