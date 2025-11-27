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
}

