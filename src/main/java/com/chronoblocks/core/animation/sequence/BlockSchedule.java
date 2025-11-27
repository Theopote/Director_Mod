package com.chronoblocks.core.animation.sequence;

import net.minecraft.util.math.BlockPos;

/**
 * 方块时间调度信息
 * 
 * <p>存储每个方块在动画序列中的时间信息，包括：
 * <ul>
 *   <li>方块位置</li>
 *   <li>开始时间（相对于片段开始）</li>
 *   <li>持续时间（单块动画长度）</li>
 *   <li>归一化得分（用于调试和预览）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class BlockSchedule {
    
    /** 方块位置 */
    private final BlockPos blockPos;
    
    /** 开始时间（秒，相对于片段开始） */
    private final float startTime;
    
    /** 持续时间（秒，单块动画长度） */
    private final float duration;
    
    /** 归一化得分（0-1，用于调试和预览） */
    private final float normalizedScore;
    
    /** 组索引（用于分组策略，-1 表示未分组） */
    private final int groupIndex;
    
    /**
     * 构造函数
     */
    public BlockSchedule(BlockPos blockPos, float startTime, float duration, float normalizedScore, int groupIndex) {
        this.blockPos = blockPos;
        this.startTime = startTime;
        this.duration = duration;
        this.normalizedScore = normalizedScore;
        this.groupIndex = groupIndex;
    }
    
    /**
     * 构造函数（简化版，不包含组索引）
     */
    public BlockSchedule(BlockPos blockPos, float startTime, float duration, float normalizedScore) {
        this(blockPos, startTime, duration, normalizedScore, -1);
    }
    
    /**
     * 获取结束时间
     */
    public float getEndTime() {
        return startTime + duration;
    }
    
    /**
     * 检查指定时间是否在此方块的时间范围内
     */
    public boolean isActiveAt(float time) {
        return time >= startTime && time < getEndTime();
    }
    
    // ========== Getters ==========
    
    public BlockPos getBlockPos() {
        return blockPos;
    }
    
    public float getStartTime() {
        return startTime;
    }
    
    public float getDuration() {
        return duration;
    }
    
    public float getNormalizedScore() {
        return normalizedScore;
    }
    
    public int getGroupIndex() {
        return groupIndex;
    }
    
    @Override
    public String toString() {
        return String.format("BlockSchedule{pos=%s, start=%.2fs, duration=%.2fs, score=%.3f, group=%d}",
            blockPos, startTime, duration, normalizedScore, groupIndex);
    }
}

