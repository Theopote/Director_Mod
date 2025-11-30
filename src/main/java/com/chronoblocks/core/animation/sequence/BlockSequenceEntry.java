package com.chronoblocks.core.animation.sequence;

import java.io.Serial;
import java.io.Serializable;
import net.minecraft.util.math.BlockPos;

/**
 * 输出给动画系统的方块序列条目。
 */
public final class BlockSequenceEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BlockPos pos;
    private final float normalizedTime;
    private final int groupId;
    private final float groupNormalizedTime;

    public BlockSequenceEntry(BlockPos pos, float normalizedTime, int groupId, float groupNormalizedTime) {
        this.pos = pos;
        this.normalizedTime = normalizedTime;
        this.groupId = groupId;
        this.groupNormalizedTime = groupNormalizedTime;
    }

    public BlockPos getPos() {
        return pos;
    }

    public float getNormalizedTime() {
        return normalizedTime;
    }

    public int getGroupId() {
        return groupId;
    }

    public float getGroupNormalizedTime() {
        return groupNormalizedTime;
    }
    
    /**
     * 生成基于方块位置的稳定随机数生成器
     * 
     * <p>用于动画效果中的随机微调，确保每个方块的效果稳定（相同位置总是产生相同的随机值）
     * 
     * @return 稳定随机数生成器
     */
    public java.util.Random getSeedRandom() {
        long seed = pos.asLong() * 4987142L + 5947611L;
        return new java.util.Random(seed);
    }
}

